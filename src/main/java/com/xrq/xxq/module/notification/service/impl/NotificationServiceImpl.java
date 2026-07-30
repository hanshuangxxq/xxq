package com.xrq.xxq.module.notification.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.xrq.xxq.common.BusinessException;
import com.xrq.xxq.module.notification.dto.NotificationResponse;
import com.xrq.xxq.module.notification.dto.SendNotificationRequest;
import com.xrq.xxq.module.notification.entity.Notification;
import com.xrq.xxq.module.notification.entity.NotificationTypeEnum;
import com.xrq.xxq.module.notification.mapper.NotificationMapper;
import com.xrq.xxq.module.notification.service.NotificationService;
import com.xrq.xxq.module.notification.ws.NotificationSessionManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationServiceImpl extends ServiceImpl<NotificationMapper, Notification> implements NotificationService {

    private final NotificationSessionManager sessionManager;
    private final ObjectMapper objectMapper;

    @Override
    public int unreadCount(Long userId) {
        Long count = baseMapper.selectCount(new LambdaQueryWrapper<Notification>()
                .eq(Notification::getUserId, userId)
                .eq(Notification::getIsRead, 0));
        return count == null ? 0 : count.intValue();
    }

    @Override
    public List<NotificationResponse> listByUser(Long userId, String status) {
        LambdaQueryWrapper<Notification> wrapper = new LambdaQueryWrapper<Notification>()
                .eq(Notification::getUserId, userId)
                .orderByDesc(Notification::getCreateTime);
        if ("unread".equalsIgnoreCase(status)) {
            wrapper.eq(Notification::getIsRead, 0);
        } else if ("read".equalsIgnoreCase(status)) {
            wrapper.eq(Notification::getIsRead, 1);
        }
        return baseMapper.selectList(wrapper).stream().map(this::toResponse).toList();
    }

    @Override
    public void markRead(Long userId, Long id) {
        Notification n = getOwned(userId, id);
        if (n.getIsRead() != null && n.getIsRead() == 1) {
            return;
        }
        baseMapper.update(null, new LambdaUpdateWrapper<Notification>()
                .eq(Notification::getId, id)
                .eq(Notification::getUserId, userId)
                .set(Notification::getIsRead, 1));
        pushUnreadCount(userId);
    }

    @Override
    public void markAllRead(Long userId) {
        baseMapper.update(null, new LambdaUpdateWrapper<Notification>()
                .eq(Notification::getUserId, userId)
                .eq(Notification::getIsRead, 0)
                .set(Notification::getIsRead, 1));
        pushUnreadCount(userId);
    }

    @Override
    public void removeOwned(Long userId, Long id) {
        getOwned(userId, id);
        baseMapper.deleteById(id);
    }

    @Override
    public NotificationResponse sendToUser(Long userId, NotificationTypeEnum type, String title, String content) {
        Notification n = new Notification();
        n.setUserId(userId);
        n.setType(type != null ? type : NotificationTypeEnum.SYSTEM);
        n.setTitle(title);
        n.setContent(content);
        n.setIsRead(0);
        n.setCreateTime(LocalDateTime.now());
        baseMapper.insert(n);

        NotificationResponse resp = toResponse(n);
        // 在线实时推送消息体；离线则靠下次连接拉取未读数
        sendPayload(userId, Map.of("type", "notification", "data", resp));
        pushUnreadCount(userId);
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
    public void pushUnreadCount(Long userId) {
        int count = unreadCount(userId);
        sendPayload(userId, Map.of("type", "unread_count", "count", count));
    }

    private void sendPayload(Long userId, Object payload) {
        try {
            sessionManager.send(userId, objectMapper.writeValueAsString(payload));
        } catch (Exception e) {
            log.warn("推送消息失败: userId={}", userId, e);
        }
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
}
