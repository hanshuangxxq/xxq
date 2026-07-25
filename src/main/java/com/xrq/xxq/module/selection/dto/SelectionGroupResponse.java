package com.xrq.xxq.module.selection.dto;

import java.time.LocalDateTime;

import lombok.Data;

/**
 * 选课组响应。
 * <p>
 * 字段填充语义随上下文：
 * <ul>
 *   <li>listAll / getDetail：courseCount 为跨所有活动合计；boundCampaignCount 为绑定活动数；sortOrderInCampaign 为 null。</li>
 *   <li>listByCampaign：courseCount 为该活动内计数；sortOrderInCampaign 为组在该活动中的排序；boundCampaignCount 为 null。</li>
 * </ul>
 */
@Data
public class SelectionGroupResponse {
    private Long id;
    private String name;
    private Integer maxCourses;
    private Integer courseCount;
    private Integer boundCampaignCount;
    private Integer sortOrderInCampaign;
    private LocalDateTime createTime;
}
