package com.xrq.xxq.module.coursework.announcement.dto;

import lombok.Data;
import org.jspecify.annotations.NonNull;

/** 公告发布/修改请求（JSON）。 */
@Data
public class AnnouncementSaveRequest {

    /** 授课安排 id；创建必填，修改忽略。 */
    private Long teachInfoId;

    @NonNull
    private String title;

    @NonNull
    private String content;
}
