package com.xrq.xxq.module.notification.service;

import com.baomidou.mybatisplus.spring.service.IService;
import com.xrq.xxq.common.PageQuery;
import com.xrq.xxq.common.PageResult;
import com.xrq.xxq.module.notification.dto.NotificationResponse;
import com.xrq.xxq.module.notification.dto.SendNotificationRequest;
import com.xrq.xxq.module.notification.entity.Notification;
import com.xrq.xxq.module.notification.entity.NotificationTargetEnum;
import com.xrq.xxq.module.notification.entity.NotificationTypeEnum;

import java.util.List;

/**
 * 站内消息提醒服务：消息存取 + 实时推送编排。
 * <p>
 * 支持两类消息：
 * <ul>
 *   <li>单点消息（{@link Notification}）：按 user_id 落库，点对点。</li>
 *   <li>广播消息（{@link com.xrq.xxq.module.notification.entity.NotificationBroadcast}）：
 *       全局通知仅 1 行，按 target_type 命中用户群体；已读记录单独存储，
 *       未读 = 可见广播数 − 已读数，避免为每个接收者落库而压垮数据库。</li>
 * </ul>
 */
public interface NotificationService extends IService<Notification> {

    /**
     * 当前用户未读消息数（单点未读 + 广播未读）。
     *
     * @param userType 当前用户类型，用于判定可见广播；null 时只统计单点未读。
     */
    int unreadCount(Long userId, String userType);

    /**
     * 当前用户消息列表（单点 + 广播合并），status 可选 read/unread，不传则全部。
     */
    PageResult<NotificationResponse> listByUser(Long userId, String userType, String status, PageQuery pageQuery);

    /** 标记单条已读（校验归属）。 */
    void markRead(Long userId, String userType, Long id);

    /** 全部已读（单点置已读 + 广播批量记已读）。 */
    void markAllRead(Long userId, String userType);

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
    void pushUnreadCount(Long userId, String userType);

    /** 标记一条广播通知为已读（幂等）。 */
    void markBroadcastRead(Long userId, String userType, Long broadcastId);

    /**
     * 全局广播通知：写 1 条广播记录 + 给目标群体在线连接实时推送消息体。
     * <p>
     * 不为每个接收者落库，避免大量已读/未读记录压垮数据库。
     * 离线用户上线时由 {@link #pushUnreadCount} / {@link #listByUser} 补推，不丢消息。
     *
     * @param target   目标群体（STUDENT/ALL）
     * @param senderId 发送者 user.id，用于审计
     */
    NotificationResponse broadcast(NotificationTypeEnum type, NotificationTargetEnum target,
                                   String title, String content, Long senderId);
}
