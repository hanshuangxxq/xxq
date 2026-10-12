package com.xrq.xxq.module.coursework.video.dto;

import lombok.Data;
import org.jspecify.annotations.NonNull;

/** 建节请求（挂在某章下）。 */
@Data
public class SectionSaveRequest {

    @NonNull
    private Long chapterId;

    @NonNull
    private String title;
}
