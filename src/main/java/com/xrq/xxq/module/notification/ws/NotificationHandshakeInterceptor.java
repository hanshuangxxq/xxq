package com.xrq.xxq.module.notification.ws;

import com.xrq.xxq.util.JwtUtils;
import com.xrq.xxq.util.auth.LoginSessionStore;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

import java.net.URI;
import java.util.Map;

/**
 * 通知 WebSocket 握手鉴权：从连接 URL 的 query 参数 {@code token} 解析 JWT，
 * 校验 Redis 会话仍存活，通过后把 userId 注入会话 attributes。
 * <p>
 * 浏览器原生 WebSocket 不支持自定义请求头，故采用 query token 方案，
 * 鉴权链路与 {@link com.xrq.xxq.config.AuthInterceptor} 一致。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationHandshakeInterceptor implements HandshakeInterceptor {

    public static final String ATTR_USER_ID = "userId";
    public static final String ATTR_USER_TYPE = "userType";

    private final JwtUtils jwtUtils;
    private final LoginSessionStore sessionStore;

    @Override
    public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response,
                                   WebSocketHandler wsHandler, Map<String, Object> attributes) {
        String token = extractToken(request.getURI());
        if (token == null) {
            log.warn("通知 WebSocket 握手失败: 缺少 token");
            response.setStatusCode(HttpStatus.UNAUTHORIZED);
            return false;
        }
        try {
            Claims claims = jwtUtils.parseToken(token);
            String tokenId = claims.get("tokenId", String.class);
            if (tokenId == null) {
                log.warn("通知 WebSocket 握手失败: 缺少 tokenId");
                response.setStatusCode(HttpStatus.UNAUTHORIZED);
                return false;
            }
            if (sessionStore.get(tokenId) == null) {
                // token 仍有效但 Redis 无会话（如后端/Redis 重启）：重建最小会话，免重连重登
                sessionStore.rebuildIfNeeded(tokenId,
                        Long.valueOf(claims.getSubject()),
                        claims.get("userType", String.class),
                        claims.get("role", String.class));
            }
            Long userId = Long.valueOf(claims.getSubject());
            attributes.put(ATTR_USER_ID, userId);
            attributes.put(ATTR_USER_TYPE, claims.get("userType", String.class));
            return true;
        } catch (JwtException e) {
            log.warn("通知 WebSocket 握手失败: token 无效 - {}", e.getMessage());
            response.setStatusCode(HttpStatus.UNAUTHORIZED);
            return false;
        }
    }

    @Override
    public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response,
                               WebSocketHandler wsHandler, Exception exception) {
        // no-op
    }

    private String extractToken(URI uri) {
        String query = uri.getQuery();
        if (query == null || query.isBlank()) {
            return null;
        }
        for (String pair : query.split("&")) {
            Integer idx = pair.indexOf('=');
            if (idx > 0 && "token".equals(pair.substring(0, idx))) {
                return pair.substring(idx + 1);
            }
        }
        return null;
    }
}
