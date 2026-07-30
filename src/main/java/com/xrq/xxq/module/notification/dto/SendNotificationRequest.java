package com.xrq.xxq.module.notification.dto;

import com.xrq.xxq.module.notification.entity.NotificationTypeEnum;
import lombok.Data;
import org.jspecify.annotations.NonNull;

/**
 * 管理员/系统发送消息请求。
 */
@Data
public class SendNotificationRequest {

    @NonNull
    private Long userId;

    @NonNull
    private NotificationTypeEnum type;

    @NonNull
    private String title;

    private String content;
}
