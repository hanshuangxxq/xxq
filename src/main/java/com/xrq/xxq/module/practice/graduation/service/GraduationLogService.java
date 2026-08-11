package com.xrq.xxq.module.practice.graduation.service;

import com.xrq.xxq.common.PageQuery;
import com.xrq.xxq.common.PageResult;
import com.xrq.xxq.module.practice.graduation.dto.OperationLogResponse;

/**
 * 毕业设计操作日志（R-10.4 关键动作留痕）。
 */
public interface GraduationLogService {

    /**
     * 记录一条操作日志（各业务 Service 在关键动作后调用）。
     */
    void record(Long campaignId, Long operatorId, String operatorType,
                String action, String targetType, Long targetId, String detail);

    /**
     * 分页查询活动下的操作日志（教务全量，院系按操作人可见性由调用方过滤）。
     */
    PageResult<OperationLogResponse> listLogs(Long campaignId, PageQuery pageQuery);
}
