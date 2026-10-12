package com.xrq.xxq.module.coursework.video.dto;

import java.time.LocalDateTime;

import com.xrq.xxq.module.coursework.video.entity.CourseVideo;
import lombok.Data;

/** 视频视图。 */
@Data
public class VideoView {

    private Long id;
    private Long teachInfoId;
    private Long nodeId;             // 挂载的节节点 id（null=公共区）
    private String title;
    private String description;
    private String fileName;
    private String fileOriginal;
    private String sha256;
    private Long sizeBytes;
    private Integer durationSec;
    private LocalDateTime createTime;

    /** 实体 -> 视图（视频服务与章节树组装共用，保持单一口径）。 */
    public static VideoView of(CourseVideo v) {
        VideoView view = new VideoView();
        view.setId(v.getId());
        view.setTeachInfoId(v.getTeachInfoId());
        view.setNodeId(v.getNodeId());
        view.setTitle(v.getTitle());
        view.setDescription(v.getDescription());
        view.setFileName(v.getFileName());
        view.setFileOriginal(v.getFileOriginal());
        view.setSha256(v.getSha256());
        view.setSizeBytes(v.getSizeBytes());
        view.setDurationSec(v.getDurationSec());
        view.setCreateTime(v.getCreateTime());
        return view;
    }
}
