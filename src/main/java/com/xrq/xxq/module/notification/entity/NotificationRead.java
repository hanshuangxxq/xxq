package com.xrq.xxq.module.notification.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 广播已读记录：仅用户主动标记已读时写入，UNIQUE(user_id, broadcast_id) 保证幂等。
 */
@Data
@TableName("notification_read")
public class NotificationRead {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    private Long broadcastId;

    private LocalDateTime readTime;
}
