package com.xrq.xxq.module.practice.graduation.dto;

import java.time.LocalDateTime;

import org.jspecify.annotations.NonNull;

import com.xrq.xxq.module.practice.graduation.entity.DuplicateResultEnum;

import lombok.Data;

/**
 * 教务登记查重结果（R-8.5，第三方平台检测后）。
 */
@Data
public class DuplicateCheckRegisterRequest {

    /** 论文ID（仅限待查重/查重不通过的最新版） */
    @NonNull
    private Long thesisId;

    /** 重复率（百分比 0-100） */
    @NonNull
    private Integer duplicateRate;

    /** 检测平台名称 */
    private String platform;

    /** 检测时间 */
    @NonNull
    private LocalDateTime checkTime;

    @NonNull
    private DuplicateResultEnum result;

    /** 备注 */
    private String comment;
}
