package com.xrq.xxq.module.practice.graduation.service;

import java.util.List;

import com.xrq.xxq.common.PageQuery;
import com.xrq.xxq.common.PageResult;
import com.xrq.xxq.module.practice.graduation.dto.DashboardRow;

/**
 * 教务/院系看板与数据导出（R-5.8~R-5.11）。
 */
public interface GraduationDashboardService {

    /** 看板分页查询（R-5.8/R-5.9：按院系/状态筛选 + 学号/姓名搜索） */
    PageResult<DashboardRow> listDashboard(Long campaignId, String status, String keyword,
                                           Long collegeId, String userType, Long operatorUserId,
                                           PageQuery pageQuery);

    /** 导出文件（R-5.10：xlsx 为主，csv 为辅；导出动作记录日志） */
    ExportFile exportDashboard(Long campaignId, String format, String status, String keyword,
                               Long collegeId, String userType, Long operatorUserId,
                               Long operatorId, String operatorType);

    /**
     * 导出结果：字节 + 文件名。
     */
    record ExportFile(byte[] data, String fileName) {
    }
}
