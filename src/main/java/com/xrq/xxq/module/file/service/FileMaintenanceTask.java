package com.xrq.xxq.module.file.service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.xrq.xxq.module.file.cache.UploadProgressIndex;
import com.xrq.xxq.module.file.entity.FileBizEnum;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 文件存储维护：过期分片会话、崩溃遗留半成品、无引用成品的回收。
 * <p>
 * 三类清扫互相独立，单条失败仅 warn 不阻断其他项（沿用项目「尽力而为」的清理纪律）。
 * <p>
 * <b>安全取向：宁可漏删，不可误删。</b> 未在 {@link FileBizEnum} 登记的业务目录一律跳过并告警 ——
 * 这是「贡献者漏登记」时的最后一道防线，也是产物目录与分片目录分离布局的价值所在。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class FileMaintenanceTask {

    /**
     * 合并半成品的精确形状：{@code {sha256}{ext}.{UUID}.merging}。
     * <p>清扫必须锚定 UUID 段再过滤，否则会把「扩展名恰好是 .merging」的成品一起删掉。
     */
    private static final Pattern MERGING_LEFTOVER_PATTERN = Pattern.compile(
            ".+\\.[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}"
                    + Pattern.quote(FileStorageService.MERGING_SUFFIX));

    private static final String CHUNKS_DIR = "chunks";

    @Value("${file.storage-path:uploads/files}")
    private String storagePath;

    @Value("${file.chunk-expire-hours:168}")
    private long chunkExpireHours;

    @Value("${file.orphan-grace-hours:48}")
    private long orphanGraceHours;

    private final UploadProgressIndex progressIndex;
    private final List<FileUsageContributor> contributors;

    @Scheduled(initialDelayString = "${file.cleanup-interval-ms:3600000}",
            fixedDelayString = "${file.cleanup-interval-ms:3600000}")
    public void cleanup() {
        int removed = sweepChunkSessions() + sweepMergingLeftovers() + sweepOrphanObjects();
        if (removed > 0) {
            log.info("文件清理完成，删除过期分片会话/半成品/无引用成品共 {} 项", removed);
        }
    }

    /**
     * 过期分片会话目录：删除 {@code chunks/{biz}/{ownerId}/{uploadId}/}。
     * <p><b>判据必须是两个条件同时成立</b>：① Redis 已无该会话；② 目录活性超过
     * {@code file.chunk-expire-hours}。只看 ① 会误删「TTL 刚过期但用户马上就回来续传」的会话
     * （init 会用同一确定性 uploadId 重建会话）；只看 ② 则会让刚被 abort 的目录多留一周。
     * <p>活性直接用<b>目录修改时间</b>：分片写入/重命名都会更新它，等价于「最后一次活动时间」，
     * 因此跨天续传不会被误删，也无需解析 meta.json。
     */
    private int sweepChunkSessions() {
        Path chunksRoot = Path.of(storagePath, CHUNKS_DIR);
        if (!Files.isDirectory(chunksRoot)) {
            return 0;
        }
        long cutoff = System.currentTimeMillis() - chunkExpireHours * 3600_000L;
        Set<String> knownBizDirs = new HashSet<>();
        int removed = 0;
        for (FileBizEnum biz : FileBizEnum.values()) {
            knownBizDirs.add(biz.getCode());
            Path bizDir = chunksRoot.resolve(biz.getCode());
            for (Path ownerDir : listDirs(bizDir)) {
                for (Path sessionDir : listDirs(ownerDir)) {
                    String uploadId = sessionDir.getFileName().toString();
                    if (progressIndex.load(uploadId) != null) {
                        continue;   // 会话仍活着，无论目录多旧都不动
                    }
                    try {
                        if (Files.getLastModifiedTime(sessionDir).toMillis() < cutoff) {
                            deleteRecursivelyQuietly(sessionDir);
                            removed++;
                        }
                    } catch (IOException e) {
                        log.warn("分片会话清理失败 {}: {}", sessionDir, e.getMessage());
                    }
                }
            }
        }
        warnUnknownDirs(chunksRoot, knownBizDirs, "chunks");
        return removed;
    }

    /** 崩溃遗留的半成品：{@code *.merging} 与整传中转目录里过期的 {@code *.tmp}。 */
    private int sweepMergingLeftovers() {
        Path objectsRoot = Path.of(storagePath, FileStorageService.OBJECTS_DIR);
        if (!Files.isDirectory(objectsRoot)) {
            return 0;
        }
        long cutoff = System.currentTimeMillis() - chunkExpireHours * 3600_000L;
        int removed = 0;
        try (Stream<Path> stream = Files.walk(objectsRoot)) {
            for (Path p : stream.filter(Files::isRegularFile).toList()) {
                String name = p.getFileName().toString();
                boolean leftover = MERGING_LEFTOVER_PATTERN.matcher(name).matches()
                        || (name.endsWith(FileStorageService.TMP_SUFFIX)
                            && p.getParent() != null
                            && FileStorageService.STAGING_DIR.equals(p.getParent().getFileName().toString()));
                if (!leftover) {
                    continue;
                }
                try {
                    if (Files.getLastModifiedTime(p).toMillis() < cutoff) {
                        Files.deleteIfExists(p);
                        removed++;
                    }
                } catch (IOException e) {
                    log.warn("半成品清理失败 {}: {}", p, e.getMessage());
                }
            }
        } catch (IOException e) {
            log.warn("半成品扫描失败: {}", e.getMessage());
        }
        return removed;
    }

    /**
     * 无引用成品回收：遍历 {@code objects/{biz}/}，删除「没有任何业务行引用 且 已过宽限期」的文件。
     * <p><b>宽限期</b>（{@code file.orphan-grace-hours}，默认 48h）覆盖的是「complete 成功后、
     * 业务表单还没提交」的窗口 —— 分片产物在业务事务之前就已落盘，若立刻回收会把用户刚要提交的
     * 文件删掉。小时级窗口相对分钟级的表单填写时间有两个数量级余量。
     * <p><b>缺贡献者的 biz 整体跳过</b>：宁可漏删不可误删。
     */
    private int sweepOrphanObjects() {
        Path objectsRoot = Path.of(storagePath, FileStorageService.OBJECTS_DIR);
        if (!Files.isDirectory(objectsRoot)) {
            return 0;
        }
        long graceCutoff = System.currentTimeMillis() - orphanGraceHours * 3600_000L;
        Set<String> knownBizDirs = new HashSet<>();
        int removed = 0;
        for (FileBizEnum biz : FileBizEnum.values()) {
            knownBizDirs.add(biz.getCode());
            Path bizDir = objectsRoot.resolve(biz.getCode());
            if (!Files.isDirectory(bizDir)) {
                continue;
            }
            FileUsageContributor contributor = contributorOf(biz);
            if (contributor == null) {
                log.warn("业务目录 {} 没有登记 FileUsageContributor，跳过成品回收", biz.getCode());
                continue;
            }
            Set<String> referenced = contributor.referencedPaths(biz);
            try (Stream<Path> stream = Files.list(bizDir)) {
                for (Path p : stream.filter(Files::isRegularFile).toList()) {
                    String rel = FileStorageService.OBJECTS_PREFIX + biz.getCode() + "/"
                            + p.getFileName();
                    if (referenced.contains(rel)) {
                        continue;
                    }
                    if (Files.getLastModifiedTime(p).toMillis() > graceCutoff) {
                        continue;   // 宽限期内：可能正等着业务提交
                    }
                    Files.deleteIfExists(p);
                    removed++;
                    log.info("回收无引用成品 {}（size={}B）", rel, sizeQuietly(p));
                }
            } catch (IOException e) {
                log.warn("成品回收失败 {}: {}", bizDir, e.getMessage());
            }
        }
        warnUnknownDirs(objectsRoot, knownBizDirs, "objects");
        return removed;
    }

    private FileUsageContributor contributorOf(FileBizEnum biz) {
        for (FileUsageContributor c : contributors) {
            if (c.biz().contains(biz)) {
                return c;
            }
        }
        return null;
    }

    /** 未登记的业务目录一律不删，只告警 —— 漏登记时宁可泄漏磁盘也不能误删数据。 */
    private void warnUnknownDirs(Path root, Set<String> known, String label) {
        for (Path dir : listDirs(root)) {
            String name = dir.getFileName().toString();
            if (!known.contains(name)) {
                log.warn("{}/ 下存在未登记的业务目录 {}，跳过清理（检查 FileBizEnum 是否需要新增）",
                        label, name);
            }
        }
    }

    private static List<Path> listDirs(Path parent) {
        if (parent == null || !Files.isDirectory(parent)) {
            return List.of();
        }
        try (Stream<Path> stream = Files.list(parent)) {
            return stream.filter(Files::isDirectory).toList();
        } catch (IOException e) {
            return List.of();
        }
    }

    private static long sizeQuietly(Path file) {
        try {
            return Files.size(file);
        } catch (IOException e) {
            return -1L;
        }
    }

    private void deleteRecursivelyQuietly(Path dir) {
        if (!Files.exists(dir)) {
            return;
        }
        try (Stream<Path> stream = Files.walk(dir)) {
            for (Path p : stream.sorted(java.util.Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(p);
            }
        } catch (IOException e) {
            log.warn("目录删除失败 {}: {}", dir, e.getMessage());
        }
    }
}
