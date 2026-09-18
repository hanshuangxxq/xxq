package com.xrq.xxq.module.file.service.impl;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

import com.xrq.xxq.common.BusinessException;
import com.xrq.xxq.module.file.cache.UploadProgressIndex;
import com.xrq.xxq.module.file.cache.UploadProgressIndex.SessionState;
import com.xrq.xxq.module.file.cache.UploadProgressIndex.Status;
import com.xrq.xxq.module.file.dto.StoredFileRef;
import com.xrq.xxq.module.file.dto.UploadSessionView;
import com.xrq.xxq.module.file.dto.VerifyResult;
import com.xrq.xxq.module.file.entity.FileBizEnum;
import com.xrq.xxq.module.file.service.FileStorageService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import tools.jackson.databind.ObjectMapper;

/**
 * {@link FileStorageService} 实现。
 * <p>
 * 存储布局见接口 javadoc。几处<b>反直觉</b>但刻意的设计，改动前务必先读懂：
 * <ol>
 *   <li><b>{@code objects/{biz}/{sha256}{ext}} 的路径由内容决定 ⇒ 同一路径的字节永不改变。</b>
 *       这是「业务删记录不删文件、由对账回收兜底」与「ETag 无需服务端求值 If-Range」两条设计的
 *       共同依据。不要为了「省磁盘」在业务删除时直接删产物 —— 同一 sha256 可能已被多行业务引用。</li>
 *   <li><b>{@code uploadId} 是确定性派生的</b>（{@code sha256(biz:ownerId:fileSha256)}），不是随机 UUID。
 *       因此「刷新页面后同参数重新 init」必然算出同一个 id，无需任何索引 key；且磁盘分片目录
 *       同样由 {@code (biz, ownerId, sha256)} 决定，Redis 全丢也能重新定位。
 *       id 可推导不构成越权：每次操作都比对 {@link SessionState#ownerId()}。</li>
 *   <li><b>分片目录带 ownerId、产物目录不带。</b>产物按内容去重（多业务共享一份物理文件）；
 *       分片目录按「内容 + 上传者」隔离，避免两个学生同时传同一份模板时互相踩 {@code part-N}，
 *       也避免任一人 abort 掉他人的进度。</li>
 *   <li><b>{@code complete} 不加分布式锁，靠构造性幂等。</b>双重 complete 在客户端超时重试下很常见，
 *       返回 409 不如幂等友好；且不引入 Redis 依赖才能让本层做零依赖单测。
 *       竞态由三处兜底：合并前检查产物是否已存在、分片校验失败时复查产物、读片 IOException 时复查产物。</li>
 * </ol>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FileStorageServiceImpl implements FileStorageService {

    private static final Pattern SHA256_PATTERN = Pattern.compile("[a-fA-F0-9]{64}");
    private static final int BUFFER_SIZE = 64 * 1024;
    private static final String ALGORITHM = "SHA-256";

    private static final String META_FILE = "meta.json";
    private static final String CHUNKS_DIR = "chunks";

    // 布局常量统一取自接口，避免清扫任务与存储层两处字面量各自漂移
    private static final String TMP_SUFFIX = FileStorageService.TMP_SUFFIX;
    private static final String MERGING_SUFFIX = FileStorageService.MERGING_SUFFIX;
    private static final String STAGING_DIR = FileStorageService.STAGING_DIR;
    private static final String OBJECTS_DIR = FileStorageService.OBJECTS_DIR;
    private static final String OBJECTS_PREFIX = FileStorageService.OBJECTS_PREFIX;

    /** 分片数上限：直接封顶 Redis 分片集合的规模（2GB/5MB = 410 片，留足余量）。 */
    private static final int MAX_CHUNKS = 10_000;

    /** 报错信息里最多列出多少个坏片序号。 */
    private static final int MAX_LISTED_PARTS = 20;

    @Value("${file.storage-path:uploads/files}")
    private String storagePath;

    @Value("${file.max-file-size:2147483648}")
    private long maxFileSize;

    @Value("${file.chunk-size:5242880}")
    private long referenceChunkSize;

    private final UploadProgressIndex progressIndex;
    private final ObjectMapper objectMapper;

    /** meta.json 内部结构（会话不可变参数的磁盘持久镜像）。 */
    private record SessionMeta(String originalName, long totalSize, long chunkSize, int totalChunks,
                               String sha256, Long ownerId, String ownerType, long createTime) {
    }

    /** 有界流式写入结果。{@code overflow} 为真时 {@code bytes}/{@code sha256} 无意义。 */
    private record CopyResult(long bytes, String sha256, boolean overflow) {
    }

    @Override
    public long referenceChunkSize() {
        return referenceChunkSize;
    }

    // ---- 会话 ----

    @Override
    public UploadSessionView init(Long ownerId, String ownerType, FileBizEnum biz, String originalName,
                                  long totalSize, int totalChunks, String sha256) {
        if (biz == null) {
            throw new BusinessException(400, "业务目录不能为空");
        }
        String sha = normalizeFileSha256(sha256);
        String name = sanitizeName(originalName);
        if (name.isEmpty()) {
            throw new BusinessException(400, "文件名不能为空");
        }
        String ext = requireAllowedExtension(biz, name);
        validateSizeParams(totalSize, totalChunks);

        // ① 秒传：产物已存在，零传输
        Path objectFile = objectFile(biz, sha, ext);
        if (Files.isRegularFile(objectFile)) {
            // 顺手清掉可能残留的分片目录（上次 complete 后被中断的会话）
            deleteRecursivelyQuietly(chunkDir(biz, ownerId, sha));
            progressIndex.remove(deriveUploadId(biz, ownerId, sha));
            return instantView(biz, name, sha, objectFile);
        }

        String uploadId = deriveUploadId(biz, ownerId, sha);

        // ② Redis 命中：进行中则续传，已合并则幂等返回产物
        SessionState existing = progressIndex.load(uploadId);
        if (existing != null) {
            if (existing.status() == Status.MERGED) {
                return mergedView(existing);
            }
            progressIndex.touch(uploadId);
            return sessionView(existing, sortedList(progressIndex.partIndexes(uploadId)),
                    progressIndex.partCount(uploadId));
        }

        // ③ Redis 丢失 → 由磁盘 meta.json 重建；磁盘也没有 → 新建
        Path dir = chunkDir(biz, ownerId, sha);
        Path metaFile = dir.resolve(META_FILE);
        SessionMeta meta;
        Map<Integer, String> parts;
        if (Files.isRegularFile(metaFile)) {
            // 磁盘 meta 权威：忽略本次入参的分片粒度（客户端必须沿用服务端返回的 chunkSize）
            meta = readMeta(metaFile);
            parts = scanParts(dir, meta);
        } else {
            meta = new SessionMeta(name, totalSize, derivedChunkSize(totalSize, totalChunks),
                    totalChunks, sha, ownerId, ownerType, System.currentTimeMillis());
            createDirectories(dir);
            writeMetaAtomic(metaFile, meta);
            parts = Map.of();
        }

        SessionState state = new SessionState(uploadId, biz, meta.originalName(), meta.totalSize(),
                meta.chunkSize(), meta.totalChunks(), meta.sha256(), ownerId, ownerType,
                meta.createTime(), Status.UPLOADING, null);
        progressIndex.save(state, parts);
        return sessionView(state, List.copyOf(parts.keySet()), parts.size());
    }

    @Override
    public UploadSessionView progress(Long ownerId, String uploadId) {
        SessionState state = requireSession(ownerId, uploadId);
        if (state.status() == Status.MERGED) {
            return mergedView(state);
        }
        progressIndex.touch(uploadId);
        return sessionView(state, sortedList(progressIndex.partIndexes(uploadId)),
                progressIndex.partCount(uploadId));
    }

    @Override
    public void abort(Long ownerId, String uploadId) {
        SessionState state = requireSession(ownerId, uploadId);
        progressIndex.remove(uploadId);
        deleteRecursivelyQuietly(chunkDir(state));
    }

    // ---- 分片 ----

    @Override
    public int saveChunk(Long ownerId, String uploadId, int index, InputStream data, String chunkSha256) {
        SessionState state = requireSession(ownerId, uploadId);
        if (state.status() == Status.MERGED) {
            throw new BusinessException(409, "上传已合并，无需再传分片");
        }
        if (data == null) {
            throw new BusinessException(400, "分片数据为空");
        }
        if (index < 0 || index >= state.totalChunks()) {
            throw new BusinessException(400, "分片序号超出范围: " + index);
        }
        String expectedSha = normalizeChunkSha256(chunkSha256);

        long expected = expectedChunkSize(state, index);
        Path dir = chunkDir(state);
        createDirectories(dir);
        // 唯一临时名：并发/重传同一分片时各写各的，避免共用 part-N.tmp 互相踩
        Path tmp = dir.resolve("part-" + index + "." + UUID.randomUUID() + TMP_SUFFIX);
        Path part = dir.resolve("part-" + index);

        CopyResult result;
        try {
            result = copyBounded(data, tmp, expected, ALGORITHM);
        } catch (IOException e) {
            deleteQuietly(tmp);
            throw new BusinessException(500, "文件保存失败");
        }
        if (result.overflow()) {
            deleteQuietly(tmp);
            throw new BusinessException(400, "分片大小不符：期望 " + expected + " 字节，实际超过上限");
        }
        if (result.bytes() != expected) {
            deleteQuietly(tmp);
            throw new BusinessException(400,
                    "分片大小不符：期望 " + expected + " 字节，实际 " + result.bytes() + " 字节");
        }
        if (!result.sha256().equalsIgnoreCase(expectedSha)) {
            deleteQuietly(tmp);
            throw new BusinessException(400, "分片校验失败，请重传该分片");
        }
        // rename 即提交点：此前所有失败路径都不留痕
        moveAtomically(tmp, part);
        progressIndex.addPart(uploadId, index, expectedSha.toLowerCase(Locale.ROOT));
        return progressIndex.partCount(uploadId);
    }

    // ---- 合并 ----

    @Override
    public StoredFileRef complete(Long ownerId, String uploadId) {
        SessionState state = requireSession(ownerId, uploadId);
        if (state.status() == Status.MERGED && state.storedPath() != null) {
            return refOf(state, state.storedPath());
        }

        String ext = extensionOf(state.originalName());
        Path objectFile = objectFile(state.biz(), state.sha256(), ext);
        String storedPath = storedPathOf(state.biz(), state.sha256(), ext);

        // 秒传命中，或并发 complete 已抢先完成：幂等返回
        if (Files.isRegularFile(objectFile)) {
            return finishMerged(state, uploadId, objectFile, storedPath);
        }

        Path dir = chunkDir(state);
        // 以磁盘为准校验分片齐全：Redis 只是快速索引，可能与磁盘不一致（如被外部清理）
        List<Integer> bad = findBadParts(dir, state);
        if (!bad.isEmpty()) {
            if (Files.isRegularFile(objectFile)) {
                // 竞态窗口：另一个 complete 刚完成并释放了分片目录
                return finishMerged(state, uploadId, objectFile, storedPath);
            }
            throw new BusinessException(400, "分片缺失或损坏（序号：" + summarize(bad) + "），请重传后重试");
        }

        createDirectories(objectsDir(state.biz()));
        Path merging = objectsDir(state.biz())
                .resolve(state.sha256() + ext + "." + UUID.randomUUID() + MERGING_SUFFIX);
        long actualSize;
        String actualSha;
        try {
            MessageDigest digest = MessageDigest.getInstance(ALGORITHM);
            byte[] buffer = new byte[BUFFER_SIZE];
            long total = 0L;
            try (OutputStream out = Files.newOutputStream(merging, StandardOpenOption.CREATE,
                    StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE)) {
                for (int i = 0; i < state.totalChunks(); i++) {
                    try (InputStream in = Files.newInputStream(dir.resolve("part-" + i))) {
                        int n;
                        while ((n = in.read(buffer)) != -1) {
                            digest.update(buffer, 0, n);
                            out.write(buffer, 0, n);
                            total += n;
                        }
                    }
                }
            }
            actualSize = total;
            actualSha = HexFormat.of().formatHex(digest.digest());
        } catch (IOException | NoSuchAlgorithmException e) {
            deleteQuietly(merging);
            if (Files.isRegularFile(objectFile)) {
                // 另一路 complete 在合并途中释放了分片目录导致读片失败：其结果同样有效
                return finishMerged(state, uploadId, objectFile, storedPath);
            }
            log.warn("文件合并失败 uploadId={}: {}", uploadId, e.toString());
            throw new BusinessException(500, "文件合并失败");
        }

        if (actualSize != state.totalSize() || !actualSha.equalsIgnoreCase(state.sha256())) {
            // 保留分片供重传，只删半成品
            deleteQuietly(merging);
            throw new BusinessException(400, "文件校验失败，请重传损坏分片后重试（或取消后重新上传）");
        }
        // 同内容并发合并时 last-writer-wins，字节一致故无害
        moveAtomically(merging, objectFile);
        return finishMerged(state, uploadId, objectFile, storedPath);
    }

    @Override
    public VerifyResult verify(Long ownerId, String uploadId) {
        SessionState state = requireSession(ownerId, uploadId);
        Path dir = chunkDir(state);
        Map<Integer, String> knownHashes = progressIndex.partHashes(uploadId);
        boolean hashesKnown = !knownHashes.isEmpty() && knownHashes.values().stream().allMatch(Objects::nonNull);

        List<Integer> received = new ArrayList<>();
        List<Integer> missing = new ArrayList<>();
        List<Integer> badDigest = new ArrayList<>();
        for (int i = 0; i < state.totalChunks(); i++) {
            Path part = dir.resolve("part-" + i);
            long actual = sizeOfQuietly(part);
            if (actual < 0) {
                missing.add(i);
                continue;
            }
            if (actual != expectedChunkSize(state, i)) {
                missing.add(i);
                continue;
            }
            received.add(i);
            String baseline = knownHashes.get(i);
            if (hashesKnown && baseline != null && !baseline.equalsIgnoreCase(sha256OfFile(part))) {
                badDigest.add(i);
            }
        }
        return new VerifyResult(received, missing, badDigest, hashesKnown);
    }

    // ---- 整传 / 绑定 / 解析 ----

    @Override
    public StoredFileRef storeWhole(FileBizEnum biz, MultipartFile file) {
        if (biz == null) {
            throw new BusinessException(400, "业务目录不能为空");
        }
        if (file == null || file.isEmpty()) {
            throw new BusinessException(400, "文件为空");
        }
        String name = sanitizeName(file.getOriginalFilename());
        if (name.isEmpty()) {
            throw new BusinessException(400, "文件名不能为空");
        }
        String ext = requireAllowedExtension(biz, name);
        long limit = Math.min(biz.getMaxWholeSize(), maxFileSize);
        if (file.getSize() > limit) {
            throw new BusinessException(400, "文件过大，最大允许 " + limit / 1024 / 1024 + "MB");
        }

        Path staging = objectsDir(biz).resolve(STAGING_DIR)
                .resolve(UUID.randomUUID().toString().replace("-", "") + ext);
        CopyResult result;
        try {
            createDirectories(staging.getParent());
            result = copyBounded(file.getInputStream(), staging, limit, ALGORITHM);
        } catch (IOException e) {
            deleteQuietly(staging);
            throw new BusinessException(500, "文件保存失败");
        }
        if (result.overflow() || result.bytes() <= 0) {
            deleteQuietly(staging);
            throw new BusinessException(400, "文件过大，最大允许 " + limit / 1024 / 1024 + "MB");
        }

        String sha = result.sha256();
        Path target = objectFile(biz, sha, ext);
        String storedPath = storedPathOf(biz, sha, ext);
        if (Files.isRegularFile(target)) {
            // 秒传：同内容已在库，丢弃中转文件
            deleteQuietly(staging);
            return new StoredFileRef(storedPath, name, sizeOfQuietly(target), sha, biz);
        }
        publishAfterCommit(staging, target);
        return new StoredFileRef(storedPath, name, result.bytes(), sha, biz);
    }

    @Override
    public StoredFileRef bind(FileBizEnum biz, String storedPath) {
        if (biz == null) {
            throw new BusinessException(400, "业务目录不能为空");
        }
        String path = normalizeStoredPath(storedPath);
        // 穿越防护 + 存在性（复用 resolve 的校验）
        Path file = resolve(path);

        String[] segments = path.split("/");
        // 形状固定为 objects/{biz}/{sha256}{ext}
        if (segments.length != 3 || !segments[1].equals(biz.getCode())) {
            throw new BusinessException(403, "文件不属于该业务目录: " + path);
        }
        String fileName = segments[2];
        int dot = fileName.indexOf('.');
        String sha = dot < 0 ? fileName : fileName.substring(0, dot);
        if (!SHA256_PATTERN.matcher(sha).matches()) {
            throw new BusinessException(400, "非法的文件路径: " + path);
        }
        return new StoredFileRef(path, fileName, sizeOfQuietly(file), sha.toLowerCase(Locale.ROOT), biz);
    }

    @Override
    public Path resolve(String storedPath) {
        String path = normalizeStoredPath(storedPath);
        Path base = Path.of(storagePath).normalize();
        Path filePath = base.resolve(path).normalize();
        if (!filePath.startsWith(base)) {
            throw new BusinessException(400, "非法的文件路径");
        }
        if (!Files.isRegularFile(filePath)) {
            throw new BusinessException(404, "文件不存在");
        }
        return filePath;
    }

    // ---- 内部：会话与产物 ----

    /** 合并完成后统一收尾：标记会话、释放分片目录、返回引用。 */
    private StoredFileRef finishMerged(SessionState state, String uploadId, Path objectFile,
                                       String storedPath) {
        progressIndex.markMerged(uploadId, storedPath);
        deleteRecursivelyQuietly(chunkDir(state));
        return new StoredFileRef(storedPath, state.originalName(), sizeOfQuietly(objectFile),
                state.sha256(), state.biz());
    }

    private SessionState requireSession(Long ownerId, String uploadId) {
        if (uploadId == null || uploadId.isBlank()) {
            throw new BusinessException(400, "上传会话标识不能为空");
        }
        SessionState state = progressIndex.load(uploadId);
        if (state == null) {
            throw new BusinessException(404, "上传会话不存在或已过期");
        }
        if (!Objects.equals(state.ownerId(), ownerId)) {
            throw new BusinessException(403, "权限不足");
        }
        return state;
    }

    /** {@link UploadProgressIndex#partIndexes} 返回升序 Set，转 List 保持有序（供前端按序跳过）。 */
    private static List<Integer> sortedList(Set<Integer> indexes) {
        return List.copyOf(indexes);
    }

    private UploadSessionView sessionView(SessionState state, List<Integer> received, int receivedCount) {
        return new UploadSessionView(state.uploadId(), state.biz().getCode(), state.originalName(),
                state.totalSize(), state.chunkSize(), state.totalChunks(), state.sha256(),
                received, receivedCount, null);
    }

    private UploadSessionView mergedView(SessionState state) {
        if (state.storedPath() == null) {
            // MERGED 但无路径：不该出现，按会话失效处理让客户端重新 init
            throw new BusinessException(404, "上传会话不存在或已过期");
        }
        return new UploadSessionView(state.uploadId(), state.biz().getCode(), state.originalName(),
                state.totalSize(), state.chunkSize(), state.totalChunks(), state.sha256(),
                List.of(), 0, refOf(state, state.storedPath()));
    }

    private UploadSessionView instantView(FileBizEnum biz, String name, String sha, Path objectFile) {
        long size = sizeOfQuietly(objectFile);
        StoredFileRef ref = new StoredFileRef(storedPathOf(biz, sha, extensionOf(name)), name,
                size, sha, biz);
        return new UploadSessionView(null, biz.getCode(), name, size, 0, 0, sha, List.of(), 0, ref);
    }

    /**
     * 由会话状态构造产物引用。路径由服务端生成，此处只做尺寸读取；产物万一缺失也不抛错
     * （幂等返回不应因磁盘异常变成 500），交由真正下载时的 {@link #resolve} 给出 404。
     */
    private StoredFileRef refOf(SessionState state, String storedPath) {
        long size = -1L;
        try {
            size = Files.size(Path.of(storagePath).normalize().resolve(storedPath).normalize());
        } catch (IOException | InvalidPathException ignored) {
            // 产物缺失或路径异常：返回 -1，下载阶段会明确报 404
        }
        return new StoredFileRef(storedPath, state.originalName(), size, state.sha256(), state.biz());
    }

    // ---- 内部：校验 ----

    private void validateSizeParams(long totalSize, int totalChunks) {
        if (totalSize <= 0) {
            throw new BusinessException(400, "文件大小必须大于 0");
        }
        if (totalSize > maxFileSize) {
            throw new BusinessException(400, "文件过大，最大允许 " + maxFileSize / 1024 / 1024 + "MB");
        }
        if (totalChunks < 1) {
            throw new BusinessException(400, "分片数量必须大于 0");
        }
        if (totalChunks > MAX_CHUNKS) {
            throw new BusinessException(400, "分片数量过多，上限 " + MAX_CHUNKS);
        }
        if (totalChunks > totalSize) {
            throw new BusinessException(400, "分片数量不能超过文件字节数");
        }
    }

    private static String requireAllowedExtension(FileBizEnum biz, String name) {
        String ext = extensionOf(name);
        if (!biz.allowedExtensions().contains(ext)) {
            throw new BusinessException(400,
                    "不支持的文件格式: " + (ext.isEmpty() ? "（无扩展名）" : ext));
        }
        return ext;
    }

    private String normalizeFileSha256(String sha256) {
        if (sha256 == null || !SHA256_PATTERN.matcher(sha256.trim()).matches()) {
            throw new BusinessException(400, "SHA-256 格式不正确（需 64 位十六进制）");
        }
        return sha256.trim().toLowerCase(Locale.ROOT);
    }

    private String normalizeChunkSha256(String chunkSha256) {
        if (chunkSha256 == null || !SHA256_PATTERN.matcher(chunkSha256.trim()).matches()) {
            throw new BusinessException(400, "分片 SHA-256 缺失或格式不正确（需 64 位十六进制）");
        }
        return chunkSha256.trim().toLowerCase(Locale.ROOT);
    }

    private static String normalizeStoredPath(String storedPath) {
        if (storedPath == null || storedPath.isBlank()) {
            throw new BusinessException(400, "文件路径为空");
        }
        return storedPath.trim();
    }

    /** 去掉客户端可能带上的目录前缀，只保留文件名（部分浏览器/客户端会传全路径）。 */
    private static String sanitizeName(String originalName) {
        if (originalName == null) {
            return "";
        }
        String name = originalName.trim();
        int slash = Math.max(name.lastIndexOf('/'), name.lastIndexOf('\\'));
        if (slash >= 0) {
            name = name.substring(slash + 1);
        }
        return name.trim();
    }

    /** 提取小写扩展名（含点）；无扩展名或含非法字符按无扩展名处理。 */
    private static String extensionOf(String name) {
        int i = name.lastIndexOf('.');
        if (i < 0 || i == name.length() - 1) {
            return "";
        }
        String ext = name.substring(i).toLowerCase(Locale.ROOT);
        return ext.chars().allMatch(c -> c == '.' || Character.isLetterOrDigit(c)) ? ext : "";
    }

    // ---- 内部：路径 ----

    private Path chunkDir(SessionState state) {
        return chunkDir(state.biz(), state.ownerId(), state.sha256());
    }

    private Path chunkDir(FileBizEnum biz, Long ownerId, String sha) {
        return Path.of(storagePath, CHUNKS_DIR).resolve(biz.getCode())
                .resolve(String.valueOf(ownerId)).resolve(sha);
    }

    private Path objectsDir(FileBizEnum biz) {
        return Path.of(storagePath, OBJECTS_DIR).resolve(biz.getCode());
    }

    private Path objectFile(FileBizEnum biz, String sha, String ext) {
        return objectsDir(biz).resolve(sha + ext);
    }

    private static String storedPathOf(FileBizEnum biz, String sha, String ext) {
        return OBJECTS_PREFIX + biz.getCode() + "/" + sha + ext;
    }

    /**
     * 确定性会话标识：同 (业务目录, 上传者, 文件内容) 必然得到同一 id。
     * 因此「刷新页面后重新 init」无需任何索引即能找回会话；磁盘分片目录也用同一组输入定位。
     * 每次操作都比对 ownerId，故可推导不构成越权。
     */
    private static String deriveUploadId(FileBizEnum biz, Long ownerId, String sha) {
        try {
            MessageDigest digest = MessageDigest.getInstance(ALGORITHM);
            byte[] raw = digest.digest((biz.getCode() + ":" + ownerId + ":" + sha)
                    .getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(raw);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("JVM 不支持 " + ALGORITHM, e);
        }
    }

    // ---- 内部：分片尺寸 ----

    private static long derivedChunkSize(long totalSize, int totalChunks) {
        return (totalSize + totalChunks - 1) / totalChunks;
    }

    /** 约定「等长分片 + 末片余数」：前 N-1 片 = chunkSize，末片 = 余数。 */
    private static long expectedChunkSize(SessionState state, int index) {
        return index < state.totalChunks() - 1
                ? state.chunkSize()
                : state.totalSize() - state.chunkSize() * (state.totalChunks() - 1L);
    }

    /** 磁盘扫描重建进度：只认尺寸合规的 part-N，摘要未知（返回 null 值）。 */
    private Map<Integer, String> scanParts(Path dir, SessionMeta meta) {
        if (!Files.isDirectory(dir)) {
            return Map.of();
        }
        Map<Integer, String> parts = new LinkedHashMap<>();
        for (int i = 0; i < meta.totalChunks(); i++) {
            long expected = i < meta.totalChunks() - 1
                    ? meta.chunkSize()
                    : meta.totalSize() - meta.chunkSize() * (meta.totalChunks() - 1L);
            if (sizeOfQuietly(dir.resolve("part-" + i)) == expected) {
                parts.put(i, null);
            }
        }
        return parts;
    }

    private List<Integer> findBadParts(Path dir, SessionState state) {
        List<Integer> bad = new ArrayList<>();
        for (int i = 0; i < state.totalChunks(); i++) {
            if (sizeOfQuietly(dir.resolve("part-" + i)) != expectedChunkSize(state, i)) {
                bad.add(i);
            }
        }
        return bad;
    }

    private static String summarize(List<Integer> indexes) {
        if (indexes.size() <= MAX_LISTED_PARTS) {
            return indexes.toString();
        }
        return indexes.subList(0, MAX_LISTED_PARTS) + " 等 " + indexes.size() + " 片";
    }

    // ---- 内部：IO ----

    /**
     * 有界流式写入：边写边算摘要；超过 {@code limit} <b>立即中止</b>，绝不先把磁盘写满再校验
     * （旧实现先 {@code Files.copy} 整个流再比大小，恶意客户端可据此打满磁盘）。
     */
    private static CopyResult copyBounded(InputStream in, Path target, long limit, String algorithm)
            throws IOException {
        MessageDigest digest;
        try {
            digest = MessageDigest.getInstance(algorithm);
        } catch (NoSuchAlgorithmException e) {
            throw new IOException("JVM 不支持 " + algorithm, e);
        }
        byte[] buffer = new byte[BUFFER_SIZE];
        long total = 0L;
        try (OutputStream out = Files.newOutputStream(target, StandardOpenOption.CREATE,
                StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE)) {
            int n;
            while ((n = in.read(buffer)) != -1) {
                total += n;
                if (total > limit) {
                    return new CopyResult(-1L, null, true);
                }
                digest.update(buffer, 0, n);
                out.write(buffer, 0, n);
            }
        }
        return new CopyResult(total, HexFormat.of().formatHex(digest.digest()), false);
    }

    /**
     * 整传产物的事务感知发布：处于活动事务时 {@code afterCommit} 才 rename（回滚则删中转文件），
     * 消灭「先落盘、DB 回滚留孤儿」的缺陷；无事务时立即 rename。
     * <p>同盘 rename 是原子的，故客户端在响应返回时（afterCommit 已同步执行完）必能看到产物。
     */
    private void publishAfterCommit(Path staging, Path target) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            moveAtomically(staging, target);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                try {
                    moveAtomically(staging, target);
                } catch (BusinessException e) {
                    // 事务已提交，无法回滚；此处只能告警，业务行会指向缺失文件
                    log.error("整传产物发布失败 staging={} target={}", staging, target, e);
                }
            }

            @Override
            public void afterCompletion(int status) {
                if (status != TransactionSynchronization.STATUS_COMMITTED) {
                    deleteQuietly(staging);
                }
            }
        });
    }

    private SessionMeta readMeta(Path metaFile) {
        try {
            return objectMapper.readValue(Files.readString(metaFile), SessionMeta.class);
        } catch (IOException | RuntimeException e) {
            throw new BusinessException(500, "上传会话信息损坏");
        }
    }

    private void writeMetaAtomic(Path metaFile, SessionMeta meta) {
        Path tmp = metaFile.resolveSibling(META_FILE + TMP_SUFFIX);
        try {
            Files.writeString(tmp, objectMapper.writeValueAsString(meta), StandardOpenOption.CREATE,
                    StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);
        } catch (IOException e) {
            throw new BusinessException(500, "文件保存失败");
        }
        moveAtomically(tmp, metaFile);
    }

    private void moveAtomically(Path from, Path to) {
        try {
            Files.move(from, to, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException e) {
            try {
                Files.move(from, to, StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException ex) {
                throw new BusinessException(500, "文件保存失败");
            }
        } catch (IOException e) {
            throw new BusinessException(500, "文件保存失败");
        }
    }

    private void createDirectories(Path dir) {
        try {
            Files.createDirectories(dir);
        } catch (IOException e) {
            throw new BusinessException(500, "文件保存失败");
        }
    }

    private String sha256OfFile(Path file) {
        try (InputStream in = Files.newInputStream(file)) {
            MessageDigest digest = MessageDigest.getInstance(ALGORITHM);
            byte[] buffer = new byte[BUFFER_SIZE];
            int n;
            while ((n = in.read(buffer)) != -1) {
                digest.update(buffer, 0, n);
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (IOException | NoSuchAlgorithmException e) {
            throw new BusinessException(500, "文件校验计算失败");
        }
    }

    private static long sizeOfQuietly(Path file) {
        try {
            return Files.size(file);
        } catch (IOException e) {
            return -1L;
        }
    }

    private void deleteQuietly(Path file) {
        try {
            Files.deleteIfExists(file);
        } catch (IOException ignored) {
            // 尽力删除
        }
    }

    private void deleteRecursivelyQuietly(Path dir) {
        if (dir == null || !Files.exists(dir)) {
            return;
        }
        try (Stream<Path> stream = Files.walk(dir)) {
            for (Path p : stream.sorted(Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(p);
            }
        } catch (IOException e) {
            log.warn("目录删除失败 {}: {}", dir, e.getMessage());
        }
    }
}
