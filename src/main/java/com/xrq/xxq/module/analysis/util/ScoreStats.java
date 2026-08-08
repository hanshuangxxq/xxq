package com.xrq.xxq.module.analysis.util;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Collection;

import com.xrq.xxq.util.ParamValidator;

/**
 * 成绩统计数学工具：平均分、及格率、标准差、挂科判定、等级映射、成绩区间校验。
 * <p>
 * 替代散落在 ScoreAnalysis/ClassAnalysis/StudentProfile/TeachingEvaluation/Score 等
 * 服务中重复的 {@code avgOf}/{@code passRateOf}/{@code isFail}/{@code levelOf}/{@code validateScore}
 * 私有方法。统一及格线 {@link #PASS_SCORE} 与百分比基数 {@link #HUNDRED}，消除散落的 60/100 魔法数。
 * <p>
 * 纯逻辑工具，接收 {@link BigDecimal} 总评分集合，与
 * {@link com.xrq.xxq.module.score.entity.Score} 实体解耦（对齐 {@link GpaCalculator} 风格）。
 */
public final class ScoreStats {

    public static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    /** 及格线。 */
    public static final double PASS_SCORE = 60.0;

    private ScoreStats() {
    }

    /** 是否挂科：总评为 null 视为未挂（不计），&lt; 60 为挂科。 */
    public static boolean isFail(BigDecimal total) {
        return total != null && total.doubleValue() < PASS_SCORE;
    }

    /** 平均分：null/空集合返回 null；否则 sum/count 保留 2 位 HALF_UP。 */
    public static BigDecimal avg(Collection<BigDecimal> vals) {
        if (vals == null || vals.isEmpty()) {
            return null;
        }
        BigDecimal sum = BigDecimal.ZERO;
        int count = 0;
        for (BigDecimal v : vals) {
            if (v == null) {
                continue;
            }
            sum = sum.add(v);
            count++;
        }
        if (count == 0) {
            return null;
        }
        return sum.divide(BigDecimal.valueOf(count), 2, RoundingMode.HALF_UP);
    }

    /** 及格率：null/空集合返回 null；否则 pass*100/total 保留 2 位 HALF_UP。 */
    public static BigDecimal passRate(Collection<BigDecimal> vals) {
        if (vals == null || vals.isEmpty()) {
            return null;
        }
        int total = 0;
        int pass = 0;
        for (BigDecimal v : vals) {
            if (v == null) {
                continue;
            }
            total++;
            if (v.doubleValue() >= PASS_SCORE) {
                pass++;
            }
        }
        if (total == 0) {
            return null;
        }
        return BigDecimal.valueOf(pass).multiply(HUNDRED)
                .divide(BigDecimal.valueOf(total), 2, RoundingMode.HALF_UP);
    }

    /** 总体标准差：样本数 &lt; 2 或 avg 为 null 返回 null；否则 sqrt(Σ(v-avg)²/n) 保留 2 位。 */
    public static BigDecimal stddev(Collection<BigDecimal> vals, BigDecimal avg) {
        if (vals == null || vals.size() < 2 || avg == null) {
            return null;
        }
        BigDecimal sqSum = BigDecimal.ZERO;
        int count = 0;
        for (BigDecimal v : vals) {
            if (v == null) {
                continue;
            }
            BigDecimal diff = v.subtract(avg);
            sqSum = sqSum.add(diff.multiply(diff));
            count++;
        }
        if (count < 2) {
            return null;
        }
        double variance = sqSum.divide(BigDecimal.valueOf(count), 6, RoundingMode.HALF_UP).doubleValue();
        return BigDecimal.valueOf(Math.sqrt(variance)).setScale(2, RoundingMode.HALF_UP);
    }

    /** 等级映射：&gt;=90 优 / &gt;=80 良 / &gt;=70 中 / &gt;=60 及格 / 否则 不及格；null 返回 null。 */
    public static String levelOf(BigDecimal total) {
        if (total == null) {
            return null;
        }
        double t = total.doubleValue();
        if (t >= 90) {
            return "优";
        }
        if (t >= 80) {
            return "良";
        }
        if (t >= 70) {
            return "中";
        }
        if (t >= 60) {
            return "及格";
        }
        return "不及格";
    }

    /** 成绩合法性校验：非空 + 0-100 区间，委托 {@link ParamValidator}。 */
    public static void validateScore(BigDecimal score) {
        ParamValidator.requireNonNull(score, "成绩");
        ParamValidator.requireInRange(score, 0, 100, "成绩");
    }
}
