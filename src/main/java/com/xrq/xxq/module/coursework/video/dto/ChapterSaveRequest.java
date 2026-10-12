package com.xrq.xxq.module.coursework.video.dto;

import lombok.Data;
import org.jspecify.annotations.NonNull;

/** 建章请求。 */
@Data
public class ChapterSaveRequest {

    @NonNull
    private Long teachInfoId;

    @NonNull
    private String title;
}
