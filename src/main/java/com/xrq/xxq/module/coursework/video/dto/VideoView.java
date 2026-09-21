package com.xrq.xxq.module.coursework.video.dto;

import java.time.LocalDateTime;

import lombok.Data;

/** 视频视图。 */
@Data
public class VideoView {

    private Long id;
    private Long teachInfoId;
    private String title;
    private String description;
    private String fileName;
    private String fileOriginal;
    private String sha256;
    private Long sizeBytes;
    private Integer durationSec;
    private Integer sortNo;
    private LocalDateTime createTime;
}
