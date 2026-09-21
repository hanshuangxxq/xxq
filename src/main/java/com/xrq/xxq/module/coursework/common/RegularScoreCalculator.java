package com.xrq.xxq.module.coursework.common;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.xrq.xxq.module.coursework.assignment.entity.CourseAssignment;
import com.xrq.xxq.module.coursework.assignment.entity.CourseAssignmentSubmission;

/**
 * 平时分合成计算器（纯函数）：每生已批改作业按各自满分归一化后算术平均，百分制 2 位小数。
 * 分母只计已批改份数——未提交/未批改的作业既不拉低也不抬高。
 */
public final class RegularScoreCalculator {

    private static final BigDecimal HUNDRED = new BigDecimal("100");

    private RegularScoreCalculator() {
    }

    /** @return 学生 user.id → 平时分（0-100）；无任何有效已批改成绩的学生不出现在结果中。 */
    public static Map<Long, BigDecimal> normalize(List<CourseAssignment> assignments,
                                                  List<CourseAssignmentSubmission> gradedSubs) {
        Map<Long, BigDecimal> totalByAssignment = assignments.stream().collect(Collectors.toMap(
                CourseAssignment::getId, CourseAssignment::getTotalScore, (a, b) -> a));
        Map<Long, List<BigDecimal>> ratiosByStudent = new HashMap<>();
        for (CourseAssignmentSubmission s : gradedSubs) {
            BigDecimal total = totalByAssignment.get(s.getAssignmentId());
            if (total == null || total.signum() <= 0 || s.getScore() == null) {
                continue;
            }
            ratiosByStudent.computeIfAbsent(s.getStudentId(), k -> new ArrayList<>())
                    .add(s.getScore().divide(total, 6, RoundingMode.HALF_UP));
        }
        Map<Long, BigDecimal> result = new HashMap<>();
        ratiosByStudent.forEach((sid, ratios) -> {
            BigDecimal sum = ratios.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal avg = sum.divide(BigDecimal.valueOf(ratios.size()), 6, RoundingMode.HALF_UP);
            result.put(sid, avg.multiply(HUNDRED).setScale(2, RoundingMode.HALF_UP));
        });
        return result;
    }
}
