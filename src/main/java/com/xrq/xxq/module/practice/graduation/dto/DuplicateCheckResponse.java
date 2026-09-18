package com.xrq.xxq.module.practice.graduation.dto;

import java.time.LocalDateTime;

import com.xrq.xxq.module.practice.graduation.entity.DuplicateResultEnum;

import lombok.Data;

/**
 * 查重记录响应。
 */
@Data
public class DuplicateCheckResponse {

    private Long id;

    private Long thesisId;

    private Integer duplicateRate;

    private String platform;

    private LocalDateTime checkTime;

    private DuplicateResultEnum result;

    private String comment;

    /** 查重报告存储路径（有值即表示可下载） */
    private String fileName;

    /** 查重报告展示文件名 */
    private String fileOriginal;

    private Long operatorId;

    private String operatorName;

    private LocalDateTime createTime;
}
