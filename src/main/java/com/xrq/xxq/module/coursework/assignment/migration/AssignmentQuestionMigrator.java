package com.xrq.xxq.module.coursework.assignment.migration;

import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 存量「文本+附件」作业 → 题目模型的一次性迁移：
 * 每份无快照题的作业生成 1 道 ESSAY 快照（配分=作业满分，题干为固定迁移文案），
 * 每条提交生成 1 条 answer（content→text，file_name→files[0]），final_score 沿用旧 score。
 *
 * <h3>运行方式</h3>
 * <pre>
 * coursework.migration.enabled=true   # 开关，默认不存在=整个类不装配
 * coursework.migration.apply=false    # 默认 dry-run：只打日志不动库
 * coursework.migration.apply=true     # 真正执行
 * </pre>
 *
 * 可重入：已迁移的作业（存在快照题）/已迁移的提交（存在 answer）自动跳过。
 * <p><b>本类是临时脚本，迁移完成并观察一个窗口后请连同配置一起删除。</b>
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "coursework.migration.enabled", havingValue = "true")
@RequiredArgsConstructor
public class AssignmentQuestionMigrator implements ApplicationRunner {

    private static final String MIGRATED_STEM = "（迁移自原作业）详见作业要求内容";

    @Value("${coursework.migration.apply:false}")
    private boolean apply;

    private final JdbcTemplate jdbcTemplate;

    @Override
    public void run(ApplicationArguments args) {
        log.warn("存量作业题目迁移开始 | 模式={}", apply ? "APPLY" : "DRY-RUN");

        List<Map<String, Object>> assignments = jdbcTemplate.queryForList(
                "SELECT a.id, a.total_score FROM course_assignment a WHERE a.deleted = 0"
                        + " AND NOT EXISTS (SELECT 1 FROM course_assignment_question q"
                        + " WHERE q.assignment_id = a.id AND q.deleted = 0)");
        int questionCount = 0;
        int answerCount = 0;
        for (Map<String, Object> row : assignments) {
            Long assignmentId = ((Number) row.get("id")).longValue();
            Object totalScore = row.get("total_score");
            if (apply) {
                jdbcTemplate.update(
                        "INSERT INTO course_assignment_question"
                                + " (assignment_id, source_question_id, type, stem, options_json,"
                                + " answer_json, analysis, score, sort_order, score_rule,"
                                + " case_sensitive, require_file, create_time, update_time, deleted)"
                                + " VALUES (?, NULL, 'ESSAY', ?, NULL, NULL, NULL, ?, 1, NULL, 0, 0,"
                                + " NOW(), NOW(), 0)",
                        assignmentId, MIGRATED_STEM, totalScore);
            }
            questionCount++;

            List<Map<String, Object>> submissions = jdbcTemplate.queryForList(
                    "SELECT s.id, s.content, s.file_name, s.file_original, s.score"
                            + " FROM course_assignment_submission s WHERE s.assignment_id = ?"
                            + " AND s.deleted = 0 AND NOT EXISTS (SELECT 1 FROM course_assignment_answer ans"
                            + " WHERE ans.submission_id = s.id AND ans.deleted = 0)",
                    assignmentId);
            for (Map<String, Object> sub : submissions) {
                if (apply) {
                    // 题目快照 id：刚插入的迁移题（每作业仅一道，取最小 id 兜底重跑场景）
                    Long questionId = jdbcTemplate.queryForObject(
                            "SELECT MIN(id) FROM course_assignment_question"
                                    + " WHERE assignment_id = ? AND deleted = 0",
                            Long.class, assignmentId);
                    String answerJson = buildAnswerJson(
                            (String) sub.get("content"),
                            (String) sub.get("file_name"),
                            (String) sub.get("file_original"));
                    jdbcTemplate.update(
                            "INSERT INTO course_assignment_answer"
                                    + " (submission_id, question_id, answer_json, auto_score, final_score,"
                                    + " comment, create_time, update_time, deleted)"
                                    + " VALUES (?, ?, ?, NULL, ?, NULL, NOW(), NOW(), 0)",
                            sub.get("id"), questionId, answerJson, sub.get("score"));
                }
                answerCount++;
            }
        }
        log.warn("存量作业题目迁移结束 | {} | 作业 {} 份 | 提交 {} 条",
                apply ? "已执行" : "仅演练（未改动任何数据）", questionCount, answerCount);
        if (!apply && questionCount > 0) {
            log.warn("以上为演练结果。确认无误后置 coursework.migration.apply=true 重跑以真正执行。");
        }
    }

    /** 旧提交 → 大题答案 JSON：{"text":..., "files":[{"path","original"}]}，手拼避免依赖 Jackson。 */
    private static String buildAnswerJson(String content, String fileName, String fileOriginal) {
        StringBuilder sb = new StringBuilder("{");
        boolean hasText = content != null && !content.isBlank();
        if (hasText) {
            sb.append("\"text\":").append(quote(content));
        }
        if (fileName != null && !fileName.isBlank()) {
            if (hasText) {
                sb.append(',');
            }
            sb.append("\"files\":[{\"path\":").append(quote(fileName))
                    .append(",\"original\":").append(quote(fileOriginal != null ? fileOriginal : fileName))
                    .append("}]");
        }
        return sb.append('}').toString();
    }

    /** 最小 JSON 字符串转义（引号/反斜杠/控制字符）。 */
    private static String quote(String s) {
        StringBuilder out = new StringBuilder("\"");
        for (char c : s.toCharArray()) {
            switch (c) {
                case '"' -> out.append("\\\"");
                case '\\' -> out.append("\\\\");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                case '\t' -> out.append("\\t");
                default -> {
                    if (c < 0x20) {
                        out.append(String.format("\\u%04x", (int) c));
                    } else {
                        out.append(c);
                    }
                }
            }
        }
        return out.append('"').toString();
    }
}
