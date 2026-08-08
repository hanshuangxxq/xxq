package com.xrq.xxq.module.notification.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.xrq.xxq.common.BusinessException;
import com.xrq.xxq.common.PageQuery;
import com.xrq.xxq.common.PageResult;
import com.xrq.xxq.module.notification.dto.NotificationResponse;
import com.xrq.xxq.module.notification.dto.SendNotificationRequest;
import com.xrq.xxq.module.notification.entity.Notification;
import com.xrq.xxq.module.notification.entity.NotificationBroadcast;
import com.xrq.xxq.module.notification.entity.NotificationRead;
import com.xrq.xxq.module.notification.entity.NotificationTargetEnum;
import com.xrq.xxq.module.notification.entity.NotificationTypeEnum;
import com.xrq.xxq.module.notification.mapper.NotificationBroadcastMapper;
import com.xrq.xxq.module.notification.mapper.NotificationMapper;
import com.xrq.xxq.module.notification.mapper.NotificationReadMapper;
import com.xrq.xxq.module.notification.service.NotificationService;
import com.xrq.xxq.module.notification.ws.NotificationSessionManager;
import com.xrq.xxq.module.user.entity.User;
import com.xrq.xxq.module.user.mapper.UserMapper;
import com.xrq.xxq.util.ReferenceValidator;
import com.xrq.xxq.util.auth.AuthFacade;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationServiceImpl extends ServiceImpl<NotificationMapper, Notification> implements NotificationService {

    private final NotificationSessionManager sessionManager;
    private final ObjectMapper objectMapper;
    private final NotificationBroadcastMapper notificationBroadcastMapper;
    private final NotificationReadMapper notificationReadMapper;
    private final UserMapper userMapper;
    private final ReferenceValidator referenceValidator;

    // ==================== 查询 ====================

    @Override
    public int unreadCount(Long userId, String userType) {
        Long direct = baseMapper.selectCount(new LambdaQueryWrapper<Notification>()
                .eq(Notification::getUserId, userId)
                .eq(Notification::getIsRead, 0));
        int directUnread = direct == null ? 0 : direct.intValue();
        return directUnread + broadcastUnreadCount(userId, userType);
    }

    @Override
    public PageResult<NotificationResponse> listByUser(Long userId, String userType, String status, PageQuery pageQuery) {
        // 单点通知
        LambdaQueryWrapper<Notification> wrapper = new LambdaQueryWrapper<Notification>()
                .eq(Notification::getUserId, userId)
                .orderByDesc(Notification::getCreateTime);
        List<NotificationResponse> all = new ArrayList<>(baseMapper.selectList(wrapper).stream()
                .map(n -> {
                    NotificationResponse r = toResponse(n);
                    r.setBroadcast(false);
                    return r;
                })
                .toList());
        // 广播通知
        all.addAll(listBroadcastsForUser(userId, userType));
        all.sort(Comparator.comparing(NotificationResponse::getCreateTime,
                Comparator.nullsLast(Comparator.reverseOrder())));

        if ("unread".equalsIgnoreCase(status)) {
            all = new ArrayList<>(all.stream().filter(r -> r.getIsRead() == null || r.getIsRead() == 0).toList());
        } else if ("read".equalsIgnoreCase(status)) {
            all = new ArrayList<>(all.stream().filter(r -> r.getIsRead() != null && r.getIsRead() == 1).toList());
        }
        // 单点+广播合并视图无法将分页下推到 SQL，全局排序后内存切片
        return PageResult.slice(all, pageQuery);
    }

    // ==================== 单点消息 ====================

    @Override
    public void markRead(Long userId, String userType, Long id) {
        Notification n = getOwned(userId, id);
        if (n.getIsRead() != null && n.getIsRead() == 1) {
            return;
        }
        baseMapper.update(null, new LambdaUpdateWrapper<Notification>()
                .eq(Notification::getId, id)
                .eq(Notification::getUserId, userId)
                .set(Notification::getIsRead, 1));
        pushUnreadCount(userId, userType);
    }

    @Override
    public void markAllRead(Long userId, String userType) {
        // 单点全部已读
        baseMapper.update(null, new LambdaUpdateWrapper<Notification>()
                .eq(Notification::getUserId, userId)
                .eq(Notification::getIsRead, 0)
                .set(Notification::getIsRead, 1));
        // 广播全部已读：未读广播批量记已读
        markBroadcastsRead(userId, visibleBroadcastIds(userType));
        pushUnreadCount(userId, userType);
    }

    @Override
    public void removeOwned(Long userId, Long id) {
        getOwned(userId, id);
        baseMapper.deleteById(id);
    }

    @Override
    public NotificationResponse sendToUser(Long userId, NotificationTypeEnum type, String title, String content) {
        referenceValidator.requireExists(userMapper, userId, "用户");
        Notification n = new Notification();
        n.setUserId(userId);
        n.setType(type != null ? type : NotificationTypeEnum.SYSTEM);
        n.setTitle(title);
        n.setContent(content);
        n.setIsRead(0);
        n.setCreateTime(LocalDateTime.now());
        baseMapper.insert(n);

        NotificationResponse resp = toResponse(n);
        resp.setBroadcast(false);
        // 在线实时推送消息体；离线则靠下次连接拉取未读数
        sendPayload(userId, Map.of("type", "notification", "data", resp));
        pushUnreadCount(userId, userTypeOf(userId));
        return resp;
    }

    @Override
    public NotificationResponse send(SendNotificationRequest request) {
        if (request.getUserId() == null) {
            throw new BusinessException(400, "接收用户ID不能为空");
        }
        if (request.getType() == null) {
            throw new BusinessException(400, "消息类型不能为空");
        }
        if (request.getTitle() == null || request.getTitle().isBlank()) {
            throw new BusinessException(400, "标题不能为空");
        }
        return sendToUser(request.getUserId(), request.getType(), request.getTitle(), request.getContent());
    }

    @Override
    public void pushUnreadCount(Long userId, String userType) {
        int count = unreadCount(userId, userType);
        sendPayload(userId, Map.of("type", "unread_count", "count", count));
    }

    // ==================== 广播消息 ====================

    @Override
    public void markBroadcastRead(Long userId, String userType, Long broadcastId) {
        referenceValidator.requireExists(notificationBroadcastMapper, broadcastId, "广播消息");
        referenceValidator.requireExists(userMapper, userId, "用户");
        markBroadcastsRead(userId, List.of(broadcastId));
        pushUnreadCount(userId, userType);
    }

    @Override
    public NotificationResponse broadcast(NotificationTypeEnum type, NotificationTargetEnum target,
                                          String title, String content, Long senderId) {
        referenceValidator.requireExists(userMapper, senderId, "用户");
        NotificationTargetEnum resolvedTarget = target != null ? target : NotificationTargetEnum.ALL;
        NotificationBroadcast b = new NotificationBroadcast();
        b.setType(type != null ? type : NotificationTypeEnum.SYSTEM);
        b.setTitle(title);
        b.setContent(content);
        b.setTargetType(resolvedTarget);
        b.setSenderId(senderId);
        b.setCreateTime(LocalDateTime.now());
        notificationBroadcastMapper.insert(b);

        NotificationResponse resp = toBroadcastResponse(b, false);
        // 仅给目标群体的在线连接推送消息体；不逐个重算未读数，避免 N 次 count 查询。
        // 离线用户上线时由 pushUnreadCount/listByUser 补推，不丢消息。
        sendBroadcastPayload(resolvedTarget, Map.of("type", "notification", "data", resp));
        return resp;
    }

    // ==================== 广播内部逻辑 ====================

    /**
     * 该用户可见广播 id 列表：target_type=ALL 命中所有用户；target_type=STUDENT 仅学生可见。
     */
    private List<Long> visibleBroadcastIds(String userType) {
        if (userType == null) {
            return List.of();
        }
        LambdaQueryWrapper<NotificationBroadcast> w;
        if (AuthFacade.USER_TYPE_STUDENT.equals(userType)) {
            w = new LambdaQueryWrapper<NotificationBroadcast>()
                    .in(NotificationBroadcast::getTargetType,
                            NotificationTargetEnum.ALL, NotificationTargetEnum.STUDENT);
        } else {
            w = new LambdaQueryWrapper<NotificationBroadcast>()
                    .eq(NotificationBroadcast::getTargetType, NotificationTargetEnum.ALL);
        }
        return notificationBroadcastMapper.selectList(w).stream()
                .map(NotificationBroadcast::getId)
                .toList();
    }

    private int broadcastUnreadCount(Long userId, String userType) {
        List<Long> visibleIds = visibleBroadcastIds(userType);
        if (visibleIds.isEmpty()) {
            return 0;
        }
        Long readCount = notificationReadMapper.selectCount(new LambdaQueryWrapper<NotificationRead>()
                .eq(NotificationRead::getUserId, userId)
                .in(NotificationRead::getBroadcastId, visibleIds));
        int read = readCount == null ? 0 : readCount.intValue();
        return visibleIds.size() - read;
    }

    private List<NotificationResponse> listBroadcastsForUser(Long userId, String userType) {
        List<Long> visibleIds = visibleBroadcastIds(userType);
        if (visibleIds.isEmpty()) {
            return List.of();
        }
        List<NotificationBroadcast> broadcasts = notificationBroadcastMapper.selectList(
                new LambdaQueryWrapper<NotificationBroadcast>()
                        .in(NotificationBroadcast::getId, visibleIds)
                        .orderByDesc(NotificationBroadcast::getCreateTime));
        if (broadcasts.isEmpty()) {
            return List.of();
        }
        Set<Long> readIds = readBroadcastIds(userId, visibleIds);
        return broadcasts.stream()
                .map(b -> toBroadcastResponse(b, readIds.contains(b.getId())))
                .toList();
    }

    private Set<Long> readBroadcastIds(Long userId, List<Long> broadcastIds) {
        if (broadcastIds.isEmpty()) {
            return Set.of();
        }
        return notificationReadMapper.selectList(new LambdaQueryWrapper<NotificationRead>()
                .eq(NotificationRead::getUserId, userId)
                .in(NotificationRead::getBroadcastId, broadcastIds))
                .stream()
                .map(NotificationRead::getBroadcastId)
                .collect(Collectors.toSet());
    }

    /**
     * 批量记录广播已读（幂等，先查已读集合再插入，唯一索引兜底并发）。
     */
    private void markBroadcastsRead(Long userId, List<Long> broadcastIds) {
        if (broadcastIds == null || broadcastIds.isEmpty()) {
            return;
        }
        Set<Long> already = readBroadcastIds(userId, broadcastIds);
        LocalDateTime now = LocalDateTime.now();
        for (Long bid : broadcastIds) {
            if (already.contains(bid)) {
                continue;
            }
            NotificationRead rec = new NotificationRead();
            rec.setUserId(userId);
            rec.setBroadcastId(bid);
            rec.setReadTime(now);
            try {
                notificationReadMapper.insert(rec);
            } catch (DuplicateKeyException e) {
                // 并发下唯一索引兜底，幂等忽略
            }
        }
    }

    // ==================== 推送 ====================

    private void sendPayload(Long userId, Object payload) {
        try {
            sessionManager.send(userId, objectMapper.writeValueAsString(payload));
        } catch (Exception e) {
            log.warn("推送消息失败: userId={}", userId, e);
        }
    }

    private void sendBroadcastPayload(NotificationTargetEnum target, Object payload) {
        try {
            String json = objectMapper.writeValueAsString(payload);
            if (target == NotificationTargetEnum.ALL) {
                for (Long uid : sessionManager.getOnlineUserIds()) {
                    sessionManager.send(uid, json);
                }
            } else {
                // STUDENT 等：按 userType 推送（target code 小写后匹配 user.user_type）
                sessionManager.broadcastToUserType(target.getCode().toLowerCase(), json);
            }
        } catch (Exception e) {
            log.warn("广播推送失败", e);
        }
    }

    // ==================== 辅助 ====================

    private String userTypeOf(Long userId) {
        User u = userMapper.selectById(userId);
        return u != null ? u.getUserType() : null;
    }

    private Notification getOwned(Long userId, Long id) {
        Notification n = baseMapper.selectById(id);
        if (n == null) {
            throw new BusinessException(404, "消息不存在");
        }
        if (!n.getUserId().equals(userId)) {
            throw new BusinessException(403, "权限不足");
        }
        return n;
    }

    private NotificationResponse toResponse(Notification n) {
        NotificationResponse resp = new NotificationResponse();
        resp.setId(n.getId());
        resp.setUserId(n.getUserId());
        resp.setType(n.getType());
        resp.setTitle(n.getTitle());
        resp.setContent(n.getContent());
        resp.setIsRead(n.getIsRead());
        resp.setCreateTime(n.getCreateTime());
        return resp;
    }

    private NotificationResponse toBroadcastResponse(NotificationBroadcast b, boolean read) {
        NotificationResponse resp = new NotificationResponse();
        resp.setId(b.getId());
        resp.setUserId(null); // 广播无单一接收者
        resp.setType(b.getType());
        resp.setTitle(b.getTitle());
        resp.setContent(b.getContent());
        resp.setIsRead(read ? 1 : 0);
        resp.setBroadcast(true);
        resp.setCreateTime(b.getCreateTime());
        return resp;
    }
}
