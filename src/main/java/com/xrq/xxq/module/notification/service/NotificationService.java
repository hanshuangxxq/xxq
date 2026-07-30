package com.xrq.xxq.module.notification.service;

import com.baomidou.mybatisplus.spring.service.IService;
import com.xrq.xxq.module.notification.dto.NotificationResponse;
import com.xrq.xxq.module.notification.dto.SendNotificationRequest;
import com.xrq.xxq.module.notification.entity.Notification;
import com.xrq.xxq.module.notification.entity.NotificationTypeEnum;

import java.util.List;

/**
 * 站内消息提醒服务：消息存取 + 实时推送编排。
 */
public interface NotificationService extends IService<Notification> {

    /** 当前用户未读消息数。 */
    int unreadCount(Long userId);

    /** 当前用户消息列表，status 可选 read/unread，不传则全部。 */
    List<NotificationResponse> listByUser(Long userId, String status);

    /** 标记单条已读（校验归属）。 */
    void markRead(Long userId, Long id);

    /** 全部已读。 */
    void markAllRead(Long userId);

    /** 删除单条（校验归属）。 */
    void removeOwned(Long userId, Long id);

    /**
     * 发送消息给指定用户（写库 + 在线实时推送）。
     * <p>
     * 后续接入消息来源时，各业务模块统一调用本方法。
     */
    NotificationResponse sendToUser(Long userId, NotificationTypeEnum type, String title, String content);

    /** 管理员发送入口。 */
    NotificationResponse send(SendNotificationRequest request);

    /** 向指定用户在线连接推送最新未读数。 */
    void pushUnreadCount(Long userId);
}
