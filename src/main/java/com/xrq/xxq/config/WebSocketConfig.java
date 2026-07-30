package com.xrq.xxq.config;

import com.xrq.xxq.module.notification.ws.NotificationHandshakeInterceptor;
import com.xrq.xxq.module.notification.ws.NotificationWebSocketHandler;
import com.xrq.xxq.module.user.service.avatar.AvatarWebSocketHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

@Configuration
@EnableWebSocket
@RequiredArgsConstructor
public class WebSocketConfig implements WebSocketConfigurer {

    private final AvatarWebSocketHandler avatarHandler;
    private final NotificationWebSocketHandler notificationHandler;
    private final NotificationHandshakeInterceptor notificationHandshakeInterceptor;

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(avatarHandler, "/ws/avatar")
                .setAllowedOrigins("*");

        // 消息提醒：握手时通过 ?token=xxx 鉴权绑定 userId
        registry.addHandler(notificationHandler, "/ws/notification")
                .addInterceptors(notificationHandshakeInterceptor)
                .setAllowedOrigins("*");
    }
}
