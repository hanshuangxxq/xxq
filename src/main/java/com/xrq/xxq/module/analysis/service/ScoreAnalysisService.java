package com.xrq.xxq.module.analysis.service;

import com.xrq.xxq.module.analysis.dto.ScoreComparisonDto;
import com.xrq.xxq.module.analysis.dto.ScoreDistributionDto;
import com.xrq.xxq.module.analysis.dto.ScoreTrendDto;

/**
 * 成绩分析服务：分数段分布、跨学期趋势、班级横向对比。
 */
public interface ScoreAnalysisService {

    /** 分数段分布：[0-59][60-69][70-79][80-89][90-100] + 均值/及格率/标准差。 */
    ScoreDistributionDto distribution(Long courseId, String className, Long semesterId,
                                      Long callerUserId, String callerUserType);

    /** 课程成绩跨学期趋势。 */
    ScoreTrendDto trend(Long courseId, String className, Long callerUserId, String callerUserType);

    /** 同课程各班级成绩横向对比（按学期）。 */
    ScoreComparisonDto comparison(Long courseId, Long semesterId,
                                  Long callerUserId, String callerUserType);
}
