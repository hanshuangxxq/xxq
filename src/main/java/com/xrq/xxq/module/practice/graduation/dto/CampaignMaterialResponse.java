package com.xrq.xxq.module.practice.graduation.dto;

import java.time.LocalDateTime;

import lombok.Data;

/**
 * 毕设活动资料响应。
 */
@Data
public class CampaignMaterialResponse {

    private Long id;

    private Long campaignId;

    /** 展示文件名 */
    private String fileOriginal;

    /** 上传人 user.id */
    private Long uploaderId;

    private String uploaderName;

    private LocalDateTime createTime;
}
