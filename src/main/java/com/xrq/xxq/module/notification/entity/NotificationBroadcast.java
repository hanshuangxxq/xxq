package com.xrq.xxq.module.notification.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 广播通知：每个全局通知仅 1 行，按 target_type 命中用户群体。
 * <p>
 * 未读 = 可见广播数 − notification_read 中已读数，避免为每个接收者落库一条记录。
 */
@Data
@TableName("notification_broadcast")
public class NotificationBroadcast {

    @TableId(type = IdType.AUTO)
    private Long id;

    private NotificationTypeEnum type;

    private String title;

    private String content;

    private NotificationTargetEnum targetType;

    private Long senderId;

    private LocalDateTime createTime;
}
