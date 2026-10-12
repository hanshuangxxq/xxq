package com.xrq.xxq.module.coursework.video.dto;

import lombok.Data;

/** 视频元数据修改（JSON；文件本体不可换，换文件 = 删除重传）。nodeId 非空即改挂到目标节（限同一授课组）。 */
@Data
public class VideoUpdateRequest {

    private String title;
    private String description;
    private Long nodeId;
    private Integer durationSec;
}
