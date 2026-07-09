package com.xrq.xxq.module.selection.dto;

import org.jspecify.annotations.NonNull;

import lombok.Data;

@Data
public class SelectionRecordRequest {
    @NonNull
    private Long campaignId;
    @NonNull
    private Long courseId;
}
