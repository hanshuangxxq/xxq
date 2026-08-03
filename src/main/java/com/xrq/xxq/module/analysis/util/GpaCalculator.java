package com.xrq.xxq.module.analysis.util;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Map;

import com.xrq.xxq.module.score.entity.Score;
import com.xrq.xxq.module.score.entity.ScoreTypeEnum;

/**
 * 绩点计算工具（5 分制线性）。
 * <p>
 * 绩点 = 成绩 &lt; 60 ? 0 : (成绩 - 50) / 10，即 60-&gt;1.0、90-&gt;4.0、100-&gt;5.0。
 * 学分绩点（加权 GPA）= Σ(单科绩点 × 学分) / Σ学分，仅统计 REGULAR 正常成绩，
 * 跳过无总评或无学分的记录；挂科课程绩点记 0 但学分仍计入分母（拉低 GPA）。
 * <p>
 * 被 学生画像 / 学业预警 / 班级专业分析 复用。
 */
public final class GpaCalculator {

    private GpaCalculator() {
    }

    /** 单科绩点：成绩为空或 &lt;60 记 0，否则 (score-50)/10，保留 2 位。 */
    public static BigDecimal gradePoint(BigDecimal totalScore) {
        if (totalScore == null) {
            return BigDecimal.ZERO;
        }
        double s = totalScore.doubleValue();
        if (s < 60) {
            return BigDecimal.ZERO;
        }
        return BigDecimal.valueOf(s - 50).divide(BigDecimal.TEN, 2, RoundingMode.HALF_UP);
    }

    /**
     * 按学分加权计算 GPA。仅取 REGULAR 成绩且 totalScore 非空、学分有效者。
     *
     * @param scores         成绩列表
     * @param creditByCourseId 课程ID -&gt; 学分 映射
     * @return 加权 GPA（2 位小数）；无有效成绩返回 null
     */
    public static BigDecimal weightedGpa(List<Score> scores, Map<Long, Integer> creditByCourseId) {
        if (scores == null || scores.isEmpty()) {
            return null;
        }
        BigDecimal gpSum = BigDecimal.ZERO;     // Σ(绩点×学分)
        BigDecimal creditSum = BigDecimal.ZERO; // Σ学分
        for (Score s : scores) {
            if (s.getScoreType() != ScoreTypeEnum.REGULAR) {
                continue;
            }
            if (s.getTotalScore() == null) {
                continue;
            }
            Integer credit = creditByCourseId == null ? null : creditByCourseId.get(s.getCourseId());
            if (credit == null || credit <= 0) {
                continue;
            }
            BigDecimal gp = gradePoint(s.getTotalScore());
            BigDecimal creditBd = BigDecimal.valueOf(credit);
            gpSum = gpSum.add(gp.multiply(creditBd));
            creditSum = creditSum.add(creditBd);
        }
        if (creditSum.signum() == 0) {
            return null;
        }
        return gpSum.divide(creditSum, 2, RoundingMode.HALF_UP);
    }
}
