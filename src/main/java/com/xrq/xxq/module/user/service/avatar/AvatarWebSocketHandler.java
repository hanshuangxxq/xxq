package com.xrq.xxq.module.user.service.avatar;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import tools.jackson.databind.ObjectMapper;

import java.util.Map;

@Component
@RequiredArgsConstructor
public class AvatarWebSocketHandler extends TextWebSocketHandler {

    private final AvatarService avatarService;
    private final ObjectMapper objectMapper;

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
    }

    @Override
    @SuppressWarnings("unchecked")
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        Map<String, Object> request = objectMapper.readValue(message.getPayload(), Map.class);

        Object userIdObj = request.get("userId");
        if (userIdObj == null) {
            sendError(session, "缺少 userId 参数");
            return;
        }

        Long userId = Long.valueOf(userIdObj.toString());
        String base64 = avatarService.getAvatarBase64(userId);
        String contentType = avatarService.getContentType(userId);

        Map<String, Object> response = Map.of(
                "userId", userId,
                "contentType", contentType != null ? contentType : "",
                "data", base64 != null ? base64 : ""
        );
        session.sendMessage(new TextMessage(objectMapper.writeValueAsString(response)));
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
    }

    private void sendError(WebSocketSession session, String errorMsg) throws Exception {
        Map<String, Object> error = Map.of("error", errorMsg);
        session.sendMessage(new TextMessage(objectMapper.writeValueAsString(error)));
    }
}
