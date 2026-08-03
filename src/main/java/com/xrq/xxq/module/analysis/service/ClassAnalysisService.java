package com.xrq.xxq.module.analysis.service;

import java.util.List;

import com.xrq.xxq.module.analysis.dto.ClassAnalysisDto;
import com.xrq.xxq.module.analysis.dto.ClassTrendDto;

/**
 * 班级/专业成绩分析服务：分组聚合与跨学期趋势对比。
 */
public interface ClassAnalysisService {

    /**
     * 分组聚合：按班级或专业统计均分/GPA/及格率/挂科/等级分布。
     *
     * @param groupBy class 或 major
     */
    List<ClassAnalysisDto> aggregate(String groupBy, Long semesterId, Long callerUserId, String callerUserType);

    /**
     * 单组跨学期趋势。
     *
     * @param groupBy   class 或 major
     * @param groupKey  班级名 或 专业名
     */
    ClassTrendDto trend(String groupBy, String groupKey, Long callerUserId, String callerUserType);
}
