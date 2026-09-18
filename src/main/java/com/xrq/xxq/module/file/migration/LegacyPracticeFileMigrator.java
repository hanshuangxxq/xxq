package com.xrq.xxq.module.file.migration;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import com.xrq.xxq.module.file.entity.FileBizEnum;
import com.xrq.xxq.module.file.service.FileStorageService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import tools.jackson.databind.ObjectMapper;

/**
 * 存量 practice 文件一次性迁移：把 {@code uploads/practice/} 下扁平的 UUID 文件搬进内容寻址的
 * {@code objects/{biz}/{sha256}{ext}}，并把 5 张业务表的 {@code file_name} 改指新路径。
 *
 * <h3>运行方式</h3>
 * <pre>
 * file.migration.enabled=true   # 开关，默认不存在=整个类不装配
 * file.migration.apply=false    # 默认 dry-run：只写日志，不动磁盘也不动数据库
 * file.migration.apply=true     # 真正执行
 * file.migration.rollback=true  # 按日志反向还原 file_name（不搬回文件，源文件从未删除）
 * </pre>
 *
 * <h3>安全设计</h3>
 * <ul>
 *   <li><b>可重入</b>：只处理 {@code file_name NOT LIKE 'objects/%'}，第二次运行天然为空；
 *       中断重跑时已搬过的行走 {@link LegacyMigrationPlanner.Decision#DEDUP} 分支。</li>
 *   <li><b>乐观更新</b>：{@code UPDATE ... WHERE id = ? AND file_name = 旧值}，
 *       迁移期间被并发改过的行不会被覆盖。</li>
 *   <li><b>绝不删除源文件</b>：{@code uploads/practice/} 原样保留，回滚窗口完整；
 *       源文件清理是迁移收敛后独立的手工步骤。</li>
 *   <li><b>不执行任何 DELETE</b>：本类只搬文件 + 改 {@code file_name} 列。
 *       「上传后清库」是各业务自己的逻辑删除路径，两者零交集。</li>
 * </ul>
 *
 * <p><b>本类是临时脚本，迁移完成并观察一个窗口后请连同配置一起删除。</b>
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "file.migration.enabled", havingValue = "true")
@RequiredArgsConstructor
public class LegacyPracticeFileMigrator implements ApplicationRunner {

    /** 表 → 业务目录的固定映射（不靠文件名/时间推断归属，避免误判）。 */
    private static final Map<String, FileBizEnum> TABLE_BIZ = new LinkedHashMap<>();

    static {
        TABLE_BIZ.put("graduation_thesis", FileBizEnum.GRADUATION_THESIS);
        TABLE_BIZ.put("graduation_opening_report", FileBizEnum.GRADUATION_OPENING);
        TABLE_BIZ.put("graduation_midterm", FileBizEnum.GRADUATION_MIDTERM);
        TABLE_BIZ.put("internship_report", FileBizEnum.INTERNSHIP_REPORT);
        TABLE_BIZ.put("social_practice_report", FileBizEnum.SOCIAL_PRACTICE_REPORT);
    }

    private static final String NEW_PREFIX = FileStorageService.OBJECTS_PREFIX;
    private static final int BUFFER_SIZE = 64 * 1024;

    @Value("${file.migration.apply:false}")
    private boolean apply;

    @Value("${file.migration.rollback:false}")
    private boolean rollback;

    @Value("${practice.storage-path:uploads/practice}")
    private String legacyDir;

    @Value("${file.storage-path:uploads/files}")
    private String storagePath;

    @Value("${file.migration.log:logs/file-migration.jsonl}")
    private String logPath;

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    /** 一行迁移记录（JSONL 一行一条，old/new 双向可回滚）。 */
    private record LogEntry(String ts, String table, Long id, String oldPath, String newPath,
                            long size, String sha256, String status) {
    }

    @Override
    public void run(ApplicationArguments args) {
        if (rollback) {
            rollbackFromLog();
            return;
        }
        migrate();
    }

    private void migrate() {
        Path legacyRoot = Path.of(legacyDir).normalize();
        if (!Files.isDirectory(legacyRoot)) {
            log.info("迁移跳过：legacy 目录不存在 {}", legacyRoot.toAbsolutePath());
            return;
        }
        log.warn("存量文件迁移开始 | 模式={} | legacy={} | 目标={}",
                apply ? "APPLY" : "DRY-RUN", legacyRoot.toAbsolutePath(), storagePath);

        Map<String, Integer> counters = new LinkedHashMap<>();
        int total = 0;
        for (Map.Entry<String, FileBizEnum> e : TABLE_BIZ.entrySet()) {
            total += migrateTable(e.getKey(), e.getValue(), legacyRoot, counters);
        }
        counters.put("scanned", total);
        log.warn("存量文件迁移结束 | {} | 明细={}", apply ? "已执行" : "仅演练（未改动任何数据）", counters);
        if (!apply && total > 0) {
            log.warn("以上为演练结果。确认无误后置 file.migration.apply=true 重跑以真正执行。");
        }
    }

    private int migrateTable(String table, FileBizEnum biz, Path legacyRoot,
                             Map<String, Integer> counters) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT id, file_name FROM " + table
                        + " WHERE file_name IS NOT NULL AND file_name <> ''"
                        + " AND file_name NOT LIKE ?", NEW_PREFIX + "%");

        for (Map<String, Object> row : rows) {
            Long id = ((Number) row.get("id")).longValue();
            String oldPath = String.valueOf(row.get("file_name"));
            migrateRow(table, biz, legacyRoot, id, oldPath, counters);
        }
        return rows.size();
    }

    private void migrateRow(String table, FileBizEnum biz, Path legacyRoot, Long id, String oldPath,
                            Map<String, Integer> counters) {
        try {
            Path legacyFile = resolveLegacy(legacyRoot, oldPath);
            boolean legacyExists = legacyFile != null && Files.isRegularFile(legacyFile);
            long legacySize = legacyExists ? Files.size(legacyFile) : -1L;

            // 快路径：旧文件不在时无需读摘要即可判定
            if (!legacyExists) {
                record(counters, LegacyMigrationPlanner.Decision.MISSING, table, id, oldPath, null, -1L, null);
                return;
            }

            String sha256 = sha256Of(legacyFile);
            String ext = extensionOf(oldPath);
            String newPath = NEW_PREFIX + biz.getCode() + "/" + sha256 + ext;
            Path target = Path.of(storagePath).normalize().resolve(newPath).normalize();

            boolean targetExists = Files.isRegularFile(target);
            long targetSize = targetExists ? Files.size(target) : -1L;

            LegacyMigrationPlanner.Decision decision =
                    LegacyMigrationPlanner.decide(true, legacySize, targetExists, targetSize);

            switch (decision) {
                case MIGRATE -> {
                    if (apply) {
                        copyAtomically(legacyFile, target);
                        updateRow(table, id, oldPath, newPath);
                    }
                }
                case DEDUP -> {
                    if (apply) {
                        updateRow(table, id, oldPath, newPath);
                    }
                }
                case CONFLICT -> log.error("目标产物大小不符，跳过（需人工核对）| {} id={} target={} 目标{}B vs 源{}B",
                        table, id, target, targetSize, legacySize);
                default -> {
                    // MISSING 已在上面处理
                }
            }
            record(counters, decision, table, id, oldPath, newPath, legacySize, sha256);
        } catch (IOException e) {
            counters.merge("failed", 1, Integer::sum);
            log.error("迁移失败 {} id={} file={}: {}", table, id, oldPath, e.toString());
            writeLog(new LogEntry(Instant.now().toString(), table, id, oldPath, null, -1L, null,
                    "FAILED: " + e.getMessage()));
        }
    }

    /**
     * 复制到同目录临时名后原子 rename —— 与存储层一致的提交语义，
     * 中断只会留下可清扫的 .tmp，不会产生半个成品。
     */
    private void copyAtomically(Path source, Path target) throws IOException {
        Files.createDirectories(target.getParent());
        Path tmp = target.resolveSibling(target.getFileName() + "." + UUID.randomUUID() + ".tmp");
        try (InputStream in = Files.newInputStream(source);
             OutputStream out = Files.newOutputStream(tmp, StandardOpenOption.CREATE,
                     StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE)) {
            in.transferTo(out);
        }
        try {
            Files.move(tmp, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            Files.deleteIfExists(tmp);
            throw e;
        }
    }

    /** 乐观更新：值没变才改，避免覆盖迁移期间被并发修改的行。 */
    private void updateRow(String table, Long id, String oldPath, String newPath) {
        int updated = jdbcTemplate.update(
                "UPDATE " + table + " SET file_name = ? WHERE id = ? AND file_name = ?",
                newPath, id, oldPath);
        if (updated == 0) {
            log.warn("乐观更新未命中（行已被并发修改），跳过 {} id={}", table, id);
        }
    }

    private void record(Map<String, Integer> counters, LegacyMigrationPlanner.Decision decision,
                        String table, Long id, String oldPath, String newPath, long size, String sha256) {
        String status = apply ? decision.name() : "DRY-" + decision.name();
        counters.merge(status, 1, Integer::sum);
        writeLog(new LogEntry(Instant.now().toString(), table, id, oldPath, newPath, size, sha256, status));
        if (decision == LegacyMigrationPlanner.Decision.MISSING) {
            log.warn("旧文件缺失，记录保持不动 | {} id={} file={}", table, id, oldPath);
        }
    }

    /** 按日志反向还原 file_name。不搬回文件 —— 源文件从未被删除，仍在 legacy 目录里。 */
    private void rollbackFromLog() {
        Path logFile = Path.of(logPath);
        if (!Files.isRegularFile(logFile)) {
            log.error("回滚失败：迁移日志不存在 {}", logFile.toAbsolutePath());
            return;
        }
        int restored = 0;
        int skipped = 0;
        List<String> lines;
        try {
            lines = Files.readAllLines(logFile, StandardCharsets.UTF_8);
        } catch (IOException e) {
            log.error("回滚失败：日志读取异常 {}", e.toString());
            return;
        }
        // 倒序回滚：同一行被迁移过多次时，最后一条才反映当前状态
        for (int i = lines.size() - 1; i >= 0; i--) {
            String line = lines.get(i);
            if (line.isBlank()) {
                continue;
            }
            LogEntry entry;
            try {
                entry = objectMapper.readValue(line, LogEntry.class);
            } catch (RuntimeException e) {
                continue;
            }
            if (entry.newPath() == null || "MISSING".equals(entry.status())
                    || entry.status().startsWith("FAILED")) {
                continue;
            }
            if (!TABLE_BIZ.containsKey(entry.table())) {
                continue;
            }
            int updated = jdbcTemplate.update(
                    "UPDATE " + entry.table() + " SET file_name = ? WHERE id = ? AND file_name = ?",
                    entry.oldPath(), entry.id(), entry.newPath());
            if (updated > 0) {
                restored++;
            } else {
                skipped++;
            }
        }
        log.warn("存量文件迁移回滚完成 | 还原 {} 行 | 未命中 {} 行（行已被改动或已删除）| "
                + "文件未搬回，legacy 目录始终保持原样", restored, skipped);
    }

    private Path resolveLegacy(Path legacyRoot, String storedName) {
        Path resolved = legacyRoot.resolve(storedName).normalize();
        return resolved.startsWith(legacyRoot) ? resolved : null;
    }

    private static String extensionOf(String name) {
        int i = name.lastIndexOf('.');
        if (i < 0 || i == name.length() - 1) {
            return "";
        }
        String ext = name.substring(i).toLowerCase(Locale.ROOT);
        return ext.chars().allMatch(c -> c == '.' || Character.isLetterOrDigit(c)) ? ext : "";
    }

    private static String sha256Of(Path file) throws IOException {
        try (InputStream in = Files.newInputStream(file)) {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] buffer = new byte[BUFFER_SIZE];
            int n;
            while ((n = in.read(buffer)) != -1) {
                digest.update(buffer, 0, n);
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException e) {
            throw new IOException("JVM 不支持 SHA-256", e);
        }
    }

    private void writeLog(LogEntry entry) {
        try {
            Path logFile = Path.of(logPath);
            Files.createDirectories(logFile.getParent() == null ? Path.of(".") : logFile.getParent());
            Files.writeString(logFile, objectMapper.writeValueAsString(entry) + System.lineSeparator(),
                    StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException | RuntimeException e) {
            // 日志写失败不该中断迁移本身，但必须让人看见
            log.error("迁移日志写入失败: {}", e.toString());
        }
    }
}
