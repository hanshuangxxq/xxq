package com.xrq.xxq.module.coursework.assignment.grading;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

import com.xrq.xxq.module.coursework.common.MultiScoreRuleEnum;
import com.xrq.xxq.module.coursework.common.QuestionTypeEnum;

import tools.jackson.databind.JsonNode;

/**
 * 客观题判分纯函数（不进 Spring、不碰 IO，单测零依赖）。
 * <p>
 * 计分规则：
 * <ul>
 *   <li>单选/判断：命中满分否则 0（选项 key 大小写不敏感）；</li>
 *   <li>多选：ALL_OR_NOTHING 全对才得分；HALF_ON_PARTIAL（默认）少选
 *       （所选 ⊆ 正确答案且非空）得半分、含错选 0 分；</li>
 *   <li>填空：逐空给分「题分 × 命中空数 / 总空数」（先乘后除，两位小数 HALF_UP，
 *       避免逐空四舍五入后求和超出题分）；ordered=true 逐空对位，
 *       false 集合匹配（每组可接受答案最多被命中一次）；默认忽略大小写与首尾空白，
 *       caseSensitive=true 时仅去首尾空白、大小写敏感；</li>
 *   <li>ESSAY 返回 null（教师批改）。</li>
 * </ul>
 * 标准答案缺失/损坏按 0 分返回 —— 判分发生在学生提交路径上，教师录题错误不应阻断学生。
 */
public final class ObjectiveGrader {

    private static final BigDecimal ZERO = new BigDecimal("0.00");
    private static final int SCALE = 2;

    private ObjectiveGrader() {
    }

    /**
     * @param standard      标准答案 JSON（结构按题型；null/损坏按 0 分）
     * @param student       学生答案 JSON（null 视为未答 → 0 分）
     * @param scoreRule     多选计分规则（null → HALF_ON_PARTIAL；仅多选生效）
     * @param caseSensitive 填空大小写敏感（仅填空生效）
     * @param questionScore 本题配分
     * @return 客观题得分（2 位小数）；ESSAY 返回 null
     */
    public static BigDecimal grade(QuestionTypeEnum type, JsonNode standard, JsonNode student,
                                   MultiScoreRuleEnum scoreRule, boolean caseSensitive,
                                   BigDecimal questionScore) {
        if (type == QuestionTypeEnum.ESSAY) {
            return null;
        }
        if (questionScore == null || questionScore.signum() <= 0) {
            return ZERO;
        }
        try {
            return switch (type) {
                case SINGLE_CHOICE -> gradeSingle(standard, student, questionScore);
                case MULTI_CHOICE -> gradeMulti(standard, student,
                        MultiScoreRuleEnum.orDefault(scoreRule), questionScore);
                case JUDGE -> gradeJudge(standard, student, questionScore);
                case FILL_BLANK -> gradeFillBlank(standard, student, caseSensitive, questionScore);
                case ESSAY -> null; // switch 完备性；入口已拦
            };
        } catch (RuntimeException e) {
            return ZERO;
        }
    }

    private static BigDecimal gradeSingle(JsonNode standard, JsonNode student, BigDecimal score) {
        if (standard == null || !standard.isString() || student == null || !student.isString()) {
            return ZERO;
        }
        return keyOf(standard.asString()).equals(keyOf(student.asString())) ? full(score) : ZERO;
    }

    private static BigDecimal gradeJudge(JsonNode standard, JsonNode student, BigDecimal score) {
        if (standard == null || !standard.isBoolean() || student == null || !student.isBoolean()) {
            return ZERO;
        }
        return standard.asBoolean() == student.asBoolean() ? full(score) : ZERO;
    }

    private static BigDecimal gradeMulti(JsonNode standard, JsonNode student,
                                         MultiScoreRuleEnum rule, BigDecimal score) {
        if (standard == null || !standard.isArray() || standard.isEmpty()) {
            return ZERO;
        }
        Set<String> correct = keySet(standard);
        Set<String> chosen = keySet(student);
        if (chosen.isEmpty()) {
            return ZERO;
        }
        if (chosen.equals(correct)) {
            return full(score);
        }
        if (rule == MultiScoreRuleEnum.HALF_ON_PARTIAL && correct.containsAll(chosen)) {
            return score.multiply(new BigDecimal("0.5")).setScale(SCALE, RoundingMode.HALF_UP);
        }
        return ZERO;
    }

    private static BigDecimal gradeFillBlank(JsonNode standard, JsonNode student,
                                             boolean caseSensitive, BigDecimal score) {
        if (standard == null || !standard.isObject()) {
            return ZERO;
        }
        JsonNode blanks = standard.path("blanks");
        if (!blanks.isArray() || blanks.isEmpty()) {
            return ZERO;
        }
        boolean ordered = !standard.has("ordered") || standard.path("ordered").asBoolean(true);
        int blankCount = blanks.size();

        int hits;
        if (ordered) {
            hits = 0;
            for (int i = 0; i < blankCount; i++) {
                String answer = studentTextAt(student, i);
                if (answer != null && groupContains(blanks.get(i), answer, caseSensitive)) {
                    hits++;
                }
            }
        } else {
            // 集合匹配：每组可接受答案最多被命中一次（学生重复填同一答案不重复得分）
            boolean[] used = new boolean[blankCount];
            hits = 0;
            int studentCount = student != null && student.isArray() ? student.size() : 0;
            for (int i = 0; i < studentCount; i++) {
                String answer = studentTextAt(student, i);
                if (answer == null) {
                    continue;
                }
                for (int g = 0; g < blankCount; g++) {
                    if (!used[g] && groupContains(blanks.get(g), answer, caseSensitive)) {
                        used[g] = true;
                        hits++;
                        break;
                    }
                }
            }
        }
        if (hits == 0) {
            return ZERO;
        }
        return score.multiply(BigDecimal.valueOf(hits))
                .divide(BigDecimal.valueOf(blankCount), SCALE, RoundingMode.HALF_UP);
    }

    // ---- 内部 ----

    private static BigDecimal full(BigDecimal score) {
        return score.setScale(SCALE, RoundingMode.HALF_UP);
    }

    /** 选项 key 归一：去首尾空白 + 大写。 */
    private static String keyOf(String key) {
        return key == null ? "" : key.trim().toUpperCase(Locale.ROOT);
    }

    private static Set<String> keySet(JsonNode array) {
        Set<String> result = new HashSet<>();
        if (array != null && array.isArray()) {
            for (JsonNode node : array) {
                if (node.isString()) {
                    result.add(keyOf(node.asString()));
                }
            }
        }
        return result;
    }

    /** 学生填空第 i 空文本（null/非数组/越界/非文本 → null）。 */
    private static String studentTextAt(JsonNode student, int i) {
        if (student == null || !student.isArray() || i >= student.size()) {
            return null;
        }
        JsonNode node = student.get(i);
        return node != null && node.isString() ? node.asString() : null;
    }

    /** 某空的可接受答案组是否命中学生答案。 */
    private static boolean groupContains(JsonNode group, String answer, boolean caseSensitive) {
        if (group == null || !group.isArray()) {
            return false;
        }
        String normalized = normalize(answer, caseSensitive);
        for (JsonNode acceptable : group) {
            if (acceptable.isString() && normalize(acceptable.asString(), caseSensitive).equals(normalized)) {
                return true;
            }
        }
        return false;
    }

    /** 填空文本归一：始终去首尾空白；默认大小写不敏感。 */
    private static String normalize(String s, boolean caseSensitive) {
        String trimmed = s == null ? "" : s.trim();
        return caseSensitive ? trimmed : trimmed.toLowerCase(Locale.ROOT);
    }
}
