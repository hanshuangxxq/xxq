package com.xrq.xxq.util.file;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.xrq.xxq.common.BusinessException;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import tools.jackson.databind.ObjectMapper;

/**
 * 分片上传存储组件（断点续传）：纯工具服务，供各业务模块在自身接口层鉴权后编程调用，
 * 不提供 REST 端点、不落数据库 —— 「磁盘即状态」，服务重启零丢失。
 * <p>
 * 存储布局（{file.storage-path} 下）：
 * <pre>
 * chunks/{biz}/{fileMd5}/meta.json   会话不可变参数（init 写一次）
 * chunks/{biz}/{fileMd5}/part-N      已提交分片（先写 part-N.tmp 再原子 rename，rename 即提交点）
 * objects/{biz}/{fileMd5}{ext}       合并成品，内容寻址（天然秒传 + 完整性自证）
 * </pre>
 * 断点恢复原理：会话 ID 即文件 MD5，已收分片清单 = 扫描 part-N 文件；半成品只会是 .tmp，
 * 恢复时自然忽略并由客户端重传覆盖。
 * <p>
 * 典型用法（调用方）：上传前 {@link #init}（幂等，返回已收分片清单，前端跳过已传片）→
 * 逐片 {@link #saveChunk} → 齐后 {@link #complete}，把返回的 storedPath/originalName
 * 写入业务表；下载时业务表取 storedPath → {@link #resolve} →
 * {@link ResumableFileResponse#buildDownload} 直接返回（支持 Range 续下）。
 * 注意：合并校验失败时若分片未带 MD5 则无法定位坏片，只能 abort 后全量重传 —— 建议始终传分片 MD5。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ChunkedUploadStore {

    private static final Pattern BIZ_PATTERN = Pattern.compile("[A-Za-z0-9][A-Za-z0-9/_-]*");
    private static final Pattern MD5_PATTERN = Pattern.compile("[a-fA-F0-9]{32}");
    private static final Pattern PART_PATTERN = Pattern.compile("part-(\\d+)");
    private static final Pattern EXT_PATTERN = Pattern.compile("\\.[a-z0-9]{1,9}");
    private static final int BUFFER_SIZE = 64 * 1024;
    private static final String META_FILE = "meta.json";
    private static final String MERGING_SUFFIX = ".merging";
    /** 合并半成品的精确形状：{md5}{ext}.{UUID}.merging —— 清扫过滤锚定 UUID 段，避免误删扩展名恰为 .merging 的成品。 */
    private static final Pattern MERGING_LEFTOVER_PATTERN = Pattern.compile(
            ".+\\.[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}\\.merging");

    @Value("${file.storage-path:uploads/files}")
    private String storagePath;

    @Value("${file.max-file-size:2147483648}")
    private long maxFileSize;

    @Value("${file.chunk-size:5242880}")
    private long referenceChunkSize;

    @Value("${file.chunk-expire-hours:168}")
    private long chunkExpireHours;

    private final ObjectMapper objectMapper;

    /** 上传会话视图。completedFile 非空表示文件已就绪（秒传命中），无需再传分片。 */
    public record UploadSession(String md5, String originalName, long totalSize, long chunkSize,
                                int totalChunks, List<Integer> receivedChunks, StoredFileInfo completedFile) {
    }

    /** 合并成品信息：storedPath 相对存储根目录（调用方写入业务表），originalName 为展示名。 */
    public record StoredFileInfo(String storedPath, String originalName, long size, String md5) {
    }

    /** meta.json 内部结构（会话不可变参数）。 */
    private record SessionMeta(String originalName, long totalSize, long chunkSize, int totalChunks,
                               String md5, long createTime) {
    }

    /** 参考分片大小（字节），供调用方向前端建议分片粒度。 */
    public long referenceChunkSize() {
        return referenceChunkSize;
    }

    /**
     * 初始化/恢复上传会话（幂等）。成品已存在 → 秒传（completedFile 非空）；
     * 同 biz+md5 已有会话 → 以磁盘 meta 为准返回会话与已收分片清单；否则创建会话目录与 meta.json。
     */
    public UploadSession init(String biz, String originalName, long totalSize, int totalChunks, String md5) {
        validateBiz(biz);
        String md5Lower = normalizeMd5(md5);
        if (originalName == null || originalName.isBlank()) {
            throw new BusinessException(400, "文件名不能为空");
        }
        if (totalSize <= 0) {
            throw new BusinessException(400, "文件大小必须大于 0");
        }
        if (totalSize > maxFileSize) {
            throw new BusinessException(400, "文件过大，最大允许 " + maxFileSize / 1024 / 1024 + "MB");
        }
        if (totalChunks < 1) {
            throw new BusinessException(400, "分片数量必须大于 0");
        }
        if (totalChunks > totalSize) {
            throw new BusinessException(400, "分片数量不能超过文件字节数");
        }
        if (totalSize - derivedChunkSize(totalSize, totalChunks) * (totalChunks - 1L) < 1) {
            throw new BusinessException(400, "分片数量不合理：末片不足 1 字节");
        }

        String ext = extensionOf(originalName);
        Path objectFile = objectFile(biz, md5Lower, ext);
        if (Files.isRegularFile(objectFile)) {
            // 秒传/完成竞态：顺手清理可能残留的分片目录
            deleteRecursivelyQuietly(chunkDir(biz, md5Lower));
            return new UploadSession(md5Lower, originalName, totalSize,
                    derivedChunkSize(totalSize, totalChunks), totalChunks, List.of(),
                    new StoredFileInfo(storedPathOf(biz, md5Lower, ext),
                            originalName, sizeOfQuietly(objectFile), md5Lower));
        }

        Path dir = chunkDir(biz, md5Lower);
        Path metaFile = dir.resolve(META_FILE);
        SessionMeta meta;
        if (Files.isRegularFile(metaFile)) {
            meta = readMeta(metaFile);
        } else {
            meta = new SessionMeta(originalName, totalSize, derivedChunkSize(totalSize, totalChunks),
                    totalChunks, md5Lower, System.currentTimeMillis());
            createDirectories(dir);
            writeMetaAtomic(metaFile, meta);
        }
        return new UploadSession(meta.md5(), meta.originalName(), meta.totalSize(), meta.chunkSize(),
                meta.totalChunks(), receivedChunks(dir, meta), null);
    }

    /** 只读查询会话进度（不创建）；会话不存在或已过期 → 404。 */
    public UploadSession getSession(String biz, String md5) {
        validateBiz(biz);
        String md5Lower = normalizeMd5(md5);
        Path dir = chunkDir(biz, md5Lower);
        SessionMeta meta = readMeta(dir.resolve(META_FILE));
        return new UploadSession(meta.md5(), meta.originalName(), meta.totalSize(), meta.chunkSize(),
                meta.totalChunks(), receivedChunks(dir, meta), null);
    }

    /**
     * 保存一个分片：写 part-N.tmp → 校验大小 → 可选分片 MD5 验损 → 原子 rename 为 part-N（提交点）。
     * 校验失败删 tmp 不留痕；同 index 重传幂等覆盖。返回当前已收分片数。
     */
    public int saveChunk(String biz, String md5, int index, InputStream data, String chunkMd5) {
        validateBiz(biz);
        String md5Lower = normalizeMd5(md5);
        if (data == null) {
            throw new BusinessException(400, "分片数据为空");
        }
        Path dir = chunkDir(biz, md5Lower);
        SessionMeta meta = readMeta(dir.resolve(META_FILE));
        if (index < 0 || index >= meta.totalChunks()) {
            throw new BusinessException(400, "分片序号超出范围: " + index);
        }
        long expected = expectedChunkSize(meta, index);
        Path tmp = dir.resolve("part-" + index + ".tmp");
        Path part = dir.resolve("part-" + index);
        long written;
        try {
            written = Files.copy(data, tmp, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            deleteQuietly(tmp);
            throw new BusinessException(500, "文件保存失败");
        }
        if (written != expected) {
            deleteQuietly(tmp);
            throw new BusinessException(400, "分片大小不符：期望 " + expected + " 字节，实际 " + written + " 字节");
        }
        if (chunkMd5 != null && !chunkMd5.isBlank()) {
            if (!MD5_PATTERN.matcher(chunkMd5.trim()).matches()) {
                deleteQuietly(tmp);
                throw new BusinessException(400, "分片 MD5 格式不正确");
            }
            String actual = md5OfFile(tmp);
            if (!actual.equalsIgnoreCase(chunkMd5.trim())) {
                deleteQuietly(tmp);
                throw new BusinessException(400, "分片校验失败，请重传该分片");
            }
        }
        moveAtomically(tmp, part);
        return receivedChunks(dir, meta).size();
    }

    /**
     * 合并分片为成品：分片齐全校验 → 流式合并（全程不载内存）→ 总大小与整文件 MD5 校验 →
     * 原子 rename 至 objects/ → 删除分片目录。合并前成品已存在（并发/秒传竞态）则幂等返回。
     * 校验失败删半成品、保留分片，可重传坏片后重试；已成功合并的会话再次 complete 按 404 处理。
     */
    public StoredFileInfo complete(String biz, String md5) {
        validateBiz(biz);
        String md5Lower = normalizeMd5(md5);
        Path dir = chunkDir(biz, md5Lower);
        SessionMeta meta = readMeta(dir.resolve(META_FILE));

        String ext = extensionOf(meta.originalName());
        Path objectFile = objectFile(biz, md5Lower, ext);
        String storedPath = storedPathOf(biz, md5Lower, ext);
        if (Files.isRegularFile(objectFile)) {
            deleteRecursivelyQuietly(dir);
            return new StoredFileInfo(storedPath, meta.originalName(), sizeOfQuietly(objectFile), md5Lower);
        }

        List<Integer> received = receivedChunks(dir, meta);
        if (received.size() != meta.totalChunks()) {
            throw new BusinessException(400, "分片未传齐：" + received.size() + "/" + meta.totalChunks());
        }

        createDirectories(objectsDir(biz));
        // 每次调用唯一名：并发 complete 各自合出完整正确的半成品，
        // rename（REPLACE_EXISTING、同内容）退化为无害的 last-writer-wins
        Path merging = objectsDir(biz).resolve(md5Lower + ext + "." + UUID.randomUUID() + MERGING_SUFFIX);
        long actualSize;
        String actualMd5;
        try {
            MessageDigest digest = MessageDigest.getInstance("MD5");
            byte[] buffer = new byte[BUFFER_SIZE];
            long total = 0L;
            try (OutputStream out = Files.newOutputStream(merging,
                    StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE)) {
                for (int i = 0; i < meta.totalChunks(); i++) {
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
            actualMd5 = HexFormat.of().formatHex(digest.digest());
        } catch (IOException | NoSuchAlgorithmException e) {
            deleteQuietly(merging);
            throw new BusinessException(500, "文件合并失败");
        }
        if (actualSize != meta.totalSize() || !actualMd5.equalsIgnoreCase(meta.md5())) {
            deleteQuietly(merging);
            throw new BusinessException(400, "文件校验失败，请重传损坏分片后重试（或取消后重新上传）");
        }
        moveAtomically(merging, objectFile);
        deleteRecursivelyQuietly(dir);
        return new StoredFileInfo(storedPath, meta.originalName(), actualSize, md5Lower);
    }

    /** 取消上传：尽力删除分片目录（不存在的会话视为已成功）。 */
    public void abort(String biz, String md5) {
        validateBiz(biz);
        String md5Lower = normalizeMd5(md5);
        deleteRecursivelyQuietly(chunkDir(biz, md5Lower));
    }

    /**
     * 定时清理：删除超过 {@code file.chunk-expire-hours} 的未完成分片会话目录，
     * 以及 objects/ 下崩溃遗留的合并半成品（UUID 唯一名，重试不复用，不扫会永久泄漏磁盘）。
     * 会话目录判据 meta.createTime（meta 损坏回退目录修改时间），半成品按文件修改时间；
     * 两者均与 JVM 同机文件系统时间同源可比。单目录失败仅 warn 不阻断，全部尽力而为。
     */
    @Scheduled(initialDelayString = "${file.cleanup-interval-ms:3600000}",
            fixedDelayString = "${file.cleanup-interval-ms:3600000}")
    public void cleanupExpired() {
        long cutoff = System.currentTimeMillis() - chunkExpireHours * 3600_000L;
        int removed = cleanupChunkSessions(cutoff) + cleanupMergingLeftovers(cutoff);
        if (removed > 0) {
            log.info("分片清理完成，删除过期上传会话/合并半成品 {} 个", removed);
        }
    }

    /**
     * 按存储相对路径（{@code objects/...}）解析磁盘文件，含路径穿越防护（下载场景用）。
     */
    public Path resolve(String storedPath) {
        if (storedPath == null || storedPath.isBlank()) {
            throw new BusinessException(400, "文件路径为空");
        }
        Path base = Path.of(storagePath).normalize();
        Path filePath = base.resolve(storedPath).normalize();
        if (!filePath.startsWith(base)) {
            throw new BusinessException(400, "非法的文件路径");
        }
        if (!Files.exists(filePath) || !Files.isRegularFile(filePath)) {
            throw new BusinessException(404, "文件不存在");
        }
        return filePath;
    }

    // ---- 内部实现 ----

    /** 扫描目录内合规 part-N（存在且大小符合约定）作为已收分片清单；.tmp 与坏尺寸自然排除。 */
    private List<Integer> receivedChunks(Path dir, SessionMeta meta) {
        if (!Files.isDirectory(dir)) {
            return List.of();
        }
        try (Stream<Path> stream = Files.list(dir)) {
            return stream.map(p -> PART_PATTERN.matcher(p.getFileName().toString()))
                    .filter(Matcher::matches)
                    .map(m -> Integer.parseInt(m.group(1)))
                    .filter(i -> i >= 0 && i < meta.totalChunks())
                    .filter(i -> sizeOfQuietly(dir.resolve("part-" + i)) == expectedChunkSize(meta, i))
                    .sorted()
                    .toList();
        } catch (IOException e) {
            return List.of();
        }
    }

    /** 约定「等长分片 + 末片余数」：前 N-1 片 = chunkSize，末片 = 余数。 */
    private long expectedChunkSize(SessionMeta meta, int index) {
        return index < meta.totalChunks() - 1
                ? meta.chunkSize()
                : meta.totalSize() - meta.chunkSize() * (meta.totalChunks() - 1L);
    }

    private static long derivedChunkSize(long totalSize, int totalChunks) {
        return (totalSize + totalChunks - 1) / totalChunks;
    }

    private SessionMeta readMeta(Path metaFile) {
        if (!Files.isRegularFile(metaFile)) {
            throw new BusinessException(404, "上传会话不存在或已过期");
        }
        try {
            return objectMapper.readValue(Files.readString(metaFile), SessionMeta.class);
        } catch (IOException | RuntimeException e) {
            throw new BusinessException(500, "上传会话信息损坏");
        }
    }

    private String md5OfFile(Path file) {
        try (InputStream in = Files.newInputStream(file)) {
            MessageDigest digest = MessageDigest.getInstance("MD5");
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

    private void writeMetaAtomic(Path metaFile, SessionMeta meta) {
        Path tmp = metaFile.resolveSibling(META_FILE + ".tmp");
        try {
            Files.writeString(tmp, objectMapper.writeValueAsString(meta),
                    StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);
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

    private Path chunkDir(String biz, String md5Lower) {
        return Path.of(storagePath, "chunks").resolve(biz).resolve(md5Lower);
    }

    private Path objectsDir(String biz) {
        return Path.of(storagePath, "objects").resolve(biz);
    }

    private Path objectFile(String biz, String md5Lower, String ext) {
        return objectsDir(biz).resolve(md5Lower + ext);
    }

    private String storedPathOf(String biz, String md5Lower, String ext) {
        return "objects/" + biz + "/" + md5Lower + ext;
    }

    /** 提取小写扩展名（含点）；无扩展名、含路径分隔符等非法字符或超长（>10 字符）按无扩展名处理。 */
    private static String extensionOf(String originalName) {
        int i = originalName.lastIndexOf('.');
        if (i < 0) {
            return "";
        }
        String ext = originalName.substring(i).toLowerCase();
        return EXT_PATTERN.matcher(ext).matches() ? ext : "";
    }

    private void validateBiz(String biz) {
        if (biz == null || !BIZ_PATTERN.matcher(biz).matches()) {
            throw new BusinessException(400, "非法的业务目录: " + biz);
        }
    }

    /** 32 位十六进制校验（天然杜绝路径穿越），返回小写形式。 */
    private String normalizeMd5(String md5) {
        if (md5 == null || !MD5_PATTERN.matcher(md5).matches()) {
            throw new BusinessException(400, "MD5 格式不正确");
        }
        return md5.toLowerCase();
    }

    private long sizeOfQuietly(Path file) {
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

    /** 清理 chunks/ 下过期会话目录，返回删除数。 */
    private int cleanupChunkSessions(long cutoff) {
        Path chunksRoot = Path.of(storagePath, "chunks");
        if (!Files.isDirectory(chunksRoot)) {
            return 0;
        }
        List<Path> metaFiles;
        try (Stream<Path> stream = Files.walk(chunksRoot)) {
            metaFiles = stream.filter(p -> META_FILE.equals(p.getFileName().toString())).toList();
        } catch (IOException e) {
            log.warn("分片清理扫描失败: {}", e.getMessage());
            return 0;
        }
        int removed = 0;
        for (Path metaFile : metaFiles) {
            Path dir = metaFile.getParent();
            try {
                long timestamp;
                try {
                    timestamp = readMeta(metaFile).createTime();
                } catch (BusinessException e) {
                    // meta 损坏（含目录已被并发删除）：回退目录修改时间
                    timestamp = Files.getLastModifiedTime(dir).toMillis();
                }
                if (timestamp < cutoff) {
                    deleteRecursivelyQuietly(dir);
                    removed++;
                }
            } catch (IOException e) {
                log.warn("分片清理失败，目录 {}: {}", dir, e.getMessage());
            }
        }
        return removed;
    }

    /** 清理 objects/ 下崩溃遗留的合并半成品（按修改时间过期删除），返回删除数。 */
    private int cleanupMergingLeftovers(long cutoff) {
        Path objectsRoot = Path.of(storagePath, "objects");
        if (!Files.isDirectory(objectsRoot)) {
            return 0;
        }
        List<Path> mergingFiles;
        try (Stream<Path> stream = Files.walk(objectsRoot)) {
            mergingFiles = stream.filter(p -> MERGING_LEFTOVER_PATTERN.matcher(p.getFileName().toString()).matches()).toList();
        } catch (IOException e) {
            log.warn("合并半成品清理扫描失败: {}", e.getMessage());
            return 0;
        }
        int removed = 0;
        for (Path merging : mergingFiles) {
            try {
                if (Files.getLastModifiedTime(merging).toMillis() < cutoff) {
                    deleteQuietly(merging);
                    removed++;
                }
            } catch (IOException e) {
                log.warn("合并半成品清理失败 {}: {}", merging, e.getMessage());
            }
        }
        return removed;
    }

    private void deleteRecursivelyQuietly(Path dir) {
        if (!Files.exists(dir)) {
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
