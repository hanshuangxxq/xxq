package com.xrq.xxq.module.notification.ws;

import com.xrq.xxq.module.notification.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

/**
 * 通知 WebSocket 处理器：连接建立时注册会话并推送当前未读数，连接关闭时注销。
 * <p>
 * 客户端可发送文本 {@code "ping"} 触发未读数重推。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationWebSocketHandler extends TextWebSocketHandler {

    private final NotificationSessionManager sessionManager;
    private final NotificationService notificationService;

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        Long userId = (Long) session.getAttributes().get(NotificationHandshakeInterceptor.ATTR_USER_ID);
        if (userId == null) {
            log.warn("通知连接缺少 userId，关闭: {}", session.getId());
            closeQuietly(session);
            return;
        }
        sessionManager.register(userId, session);
        // 连接建立即推送当前未读数
        notificationService.pushUnreadCount(userId);
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        Long userId = (Long) session.getAttributes().get(NotificationHandshakeInterceptor.ATTR_USER_ID);
        if (userId == null) {
            return;
        }
        if ("ping".equalsIgnoreCase(message.getPayload().trim())) {
            notificationService.pushUnreadCount(userId);
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        Long userId = (Long) session.getAttributes().get(NotificationHandshakeInterceptor.ATTR_USER_ID);
        if (userId != null) {
            sessionManager.unregister(userId, session);
        }
    }

    private void closeQuietly(WebSocketSession session) {
        try {
            session.close(CloseStatus.POLICY_VIOLATION);
        } catch (Exception ignored) {
            // no-op
        }
    }
}
