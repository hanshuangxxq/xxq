package com.xrq.xxq.module.notification.ws;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;

/**
 * 通知在线会话管理：维护 userId -> 在线 WebSocket 会话集合，并记录在线用户类型。
 * <p>
 * 支持同一用户多端在线，所有连接都会收到推送。推送文本到单个 session 时对该 session 加锁，
 * 避免 {@code sendMessage} 并发触发 IllegalStateException。
 */
@Slf4j
@Component
public class NotificationSessionManager {

    private final ConcurrentHashMap<Long, Set<WebSocketSession>> userSessions = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Long, String> userTypes = new ConcurrentHashMap<>();

    public void register(Long userId, String userType, WebSocketSession session) {
        userSessions.computeIfAbsent(userId, k -> new CopyOnWriteArraySet<>()).add(session);
        if (userType != null) {
            userTypes.put(userId, userType);
        }
        log.debug("通知连接注册: userId={}, sessionId={}", userId, session.getId());
    }

    public void unregister(Long userId, WebSocketSession session) {
        Set<WebSocketSession> sessions = userSessions.get(userId);
        if (sessions != null) {
            sessions.remove(session);
            if (sessions.isEmpty()) {
                userSessions.remove(userId, sessions);
                userTypes.remove(userId);
            }
        }
        log.debug("通知连接注销: userId={}, sessionId={}", userId, session.getId());
    }

    /**
     * 给指定用户所有在线连接推送文本消息。
     *
     * @return 是否至少送达一个连接（用户不在线返回 false）
     */
    public boolean send(Long userId, String text) {
        Set<WebSocketSession> sessions = userSessions.get(userId);
        if (sessions == null || sessions.isEmpty()) {
            return false;
        }
        TextMessage message = new TextMessage(text);
        boolean delivered = false;
        for (WebSocketSession session : sessions) {
            if (!session.isOpen()) {
                continue;
            }
            try {
                synchronized (session) {
                    session.sendMessage(message);
                }
                delivered = true;
            } catch (Exception e) {
                log.warn("推送消息失败: userId={}, sessionId={}", userId, session.getId(), e);
            }
        }
        return delivered;
    }

    /**
     * 给指定 userType 的所有在线连接推送文本消息（纯内存广播，不访问数据库）。
     */
    public void broadcastToUserType(String userType, String text) {
        if (userType == null) {
            return;
        }
        TextMessage message = new TextMessage(text);
        for (Map.Entry<Long, String> entry : userTypes.entrySet()) {
            if (!userType.equals(entry.getValue())) {
                continue;
            }
            Set<WebSocketSession> sessions = userSessions.get(entry.getKey());
            if (sessions == null) {
                continue;
            }
            for (WebSocketSession session : sessions) {
                if (!session.isOpen()) {
                    continue;
                }
                try {
                    synchronized (session) {
                        session.sendMessage(message);
                    }
                } catch (Exception e) {
                    log.warn("广播推送失败: userId={}, sessionId={}", entry.getKey(), session.getId(), e);
                }
            }
        }
    }

    public Set<Long> getOnlineUserIds() {
        return Collections.unmodifiableSet(userSessions.keySet());
    }

    public boolean isOnline(Long userId) {
        Set<WebSocketSession> sessions = userSessions.get(userId);
        return sessions != null && !sessions.isEmpty();
    }
}
