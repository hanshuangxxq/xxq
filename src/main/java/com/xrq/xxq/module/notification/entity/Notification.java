package com.xrq.xxq.module.notification.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * @类名 Notification
 * @Date 2026/7/30
 * 站内消息提醒，按用户维度存储，支持实时推送与未读统计
 */
@Data
@TableName("notification")
public class Notification {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId; // 接收者 user.id

    private NotificationTypeEnum type; // 消息类型

    private String title; // 标题

    private String content; // 内容

    private Integer isRead; // 0:未读 1:已读

    private LocalDateTime createTime; // 创建时间

    @TableLogic
    private Integer deleted; // 逻辑删除 0:未删除 1:已删除
}
