package com.xrq.xxq.module.notification.dto;

import com.xrq.xxq.module.notification.entity.NotificationTypeEnum;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 返回前端的消息视图。
 */
@Data
public class NotificationResponse {

    private Long id;
    private Long userId;
    private NotificationTypeEnum type;
    private String title;
    private String content;
    private Integer isRead;
    private Boolean broadcast; // true:广播消息；null/false:单点消息
    private LocalDateTime createTime;
}
