package com.xrq.xxq.module.analysis.dto;

import java.util.List;

import org.jspecify.annotations.NonNull;

import lombok.Data;

/**
 * 预警阈值配置批量更新请求。
 */
@Data
public class WarningConfigRequest {

    @NonNull
    private List<WarningConfigDto> configs;
}
