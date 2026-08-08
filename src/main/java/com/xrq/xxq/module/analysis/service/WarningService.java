package com.xrq.xxq.module.analysis.service;

import java.util.List;

import com.xrq.xxq.common.PageQuery;
import com.xrq.xxq.common.PageResult;
import com.xrq.xxq.module.analysis.dto.WarningConfigDto;
import com.xrq.xxq.module.analysis.dto.WarningItemDto;
import com.xrq.xxq.module.analysis.dto.WarningScanResultDto;
import com.xrq.xxq.module.analysis.entity.WarningLevelEnum;

/**
 * 学业预警服务：阈值配置、扫描（持久化记录+通知）、看板与自查。
 */
public interface WarningService {

    /** 查询全部阈值配置。 */
    List<WarningConfigDto> listConfig();

    /** 批量更新阈值配置（按级别 upsert 已有行）。 */
    void updateConfig(List<WarningConfigDto> configs);

    /**
     * 扫描全体学生：按阈值匹配最高级别，upsert 预警记录，
     * 新激活者推送站内通知，已好转者标记解除。返回摘要。
     */
    WarningScanResultDto scan(Long callerUserId);

    /** 预警看板：教务全校、院系本院；按学期/级别过滤。 */
    PageResult<WarningItemDto> list(Long semesterId, WarningLevelEnum level, Long callerUserId, String callerUserType, PageQuery pageQuery);

    /** 学生查询本人生效中的预警。 */
    List<WarningItemDto> myWarnings(Long studentUserId);
}
