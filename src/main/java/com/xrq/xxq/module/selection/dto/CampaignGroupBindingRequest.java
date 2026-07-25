package com.xrq.xxq.module.selection.dto;

import org.jspecify.annotations.NonNull;

import lombok.Data;

/**
 * 选课活动绑定选课组请求。
 * <p>
 * 将一个已存在的选课组绑定到指定选课活动。
 */
@Data
public class CampaignGroupBindingRequest {
    @NonNull
    private Long groupId;
    private Integer sortOrder;
}
