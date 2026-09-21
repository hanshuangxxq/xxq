package com.xrq.xxq.module.coursework.announcement.dto;

import java.time.LocalDateTime;

import lombok.Data;

/** 公告视图。 */
@Data
public class AnnouncementView {

    private Long id;
    private Long teachInfoId;
    private String title;
    private String content;
    private Long teacherId;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
