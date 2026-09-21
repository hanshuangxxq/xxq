package com.xrq.xxq.module.coursework.video.ws;

import java.net.URI;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

import com.xrq.xxq.common.BusinessException;
import com.xrq.xxq.module.coursework.common.CourseGroupResolver;
import com.xrq.xxq.module.coursework.video.entity.CourseVideo;
import com.xrq.xxq.module.coursework.video.mapper.CourseVideoMapper;
import com.xrq.xxq.module.teachinfo.entity.TeachInfo;
import com.xrq.xxq.util.JwtUtils;
import com.xrq.xxq.util.auth.AuthFacade;
import com.xrq.xxq.util.auth.LoginSessionStore;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 视频 WS 握手鉴权：query token 解析 JWT + Redis 会话存活（与 AuthInterceptor 同链路，
 * 参照 NotificationHandshakeInterceptor），再校验 videoId 存在与可见性
 * （教师=归属者 / 学生=授课组可见），通过后把 videoId/userId/userType 注入 attributes。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class VideoStreamHandshakeInterceptor implements HandshakeInterceptor {

    public static final String ATTR_USER_ID = "userId";
    public static final String ATTR_USER_TYPE = "userType";
    public static final String ATTR_VIDEO_ID = "videoId";

    private final JwtUtils jwtUtils;
    private final LoginSessionStore sessionStore;
    private final CourseVideoMapper videoMapper;
    private final CourseGroupResolver groupResolver;

    @Override
    public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response,
                                   WebSocketHandler wsHandler, Map<String, Object> attributes) {
        String token = extractToken(request.getURI());
        Long videoId = extractVideoId(request.getURI());
        if (token == null || videoId == null) {
            response.setStatusCode(HttpStatus.UNAUTHORIZED);
            return false;
        }
        try {
            Claims claims = jwtUtils.parseToken(token);
            String tokenId = claims.get("tokenId", String.class);
            if (tokenId == null) {
                response.setStatusCode(HttpStatus.UNAUTHORIZED);
                return false;
            }
            if (sessionStore.get(tokenId) == null) {
                // token 有效但 Redis 无会话（重启场景）：重建最小会话，免重登
                sessionStore.rebuildIfNeeded(tokenId, Long.valueOf(claims.getSubject()),
                        claims.get("userType", String.class));
            }
            Long userId = Long.valueOf(claims.getSubject());
            String userType = claims.get("userType", String.class);
            if (!canAccess(videoId, userId, userType)) {
                response.setStatusCode(HttpStatus.FORBIDDEN);
                return false;
            }
            attributes.put(ATTR_USER_ID, userId);
            attributes.put(ATTR_USER_TYPE, userType);
            attributes.put(ATTR_VIDEO_ID, videoId);
            return true;
        } catch (JwtException e) {
            log.warn("视频 WS 握手失败: token 无效 - {}", e.getMessage());
            response.setStatusCode(HttpStatus.UNAUTHORIZED);
            return false;
        } catch (BusinessException e) {
            response.setStatusCode(HttpStatus.FORBIDDEN);
            return false;
        }
    }

    @Override
    public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response,
                               WebSocketHandler wsHandler, Exception exception) {
        // no-op
    }

    private boolean canAccess(Long videoId, Long userId, String userType) {
        CourseVideo v = videoMapper.selectById(videoId);
        if (v == null) {
            return false;
        }
        if (AuthFacade.USER_TYPE_TEACHER.equals(userType)) {
            TeachInfo anchor = groupResolver.requireAnchor(v.getTeachInfoId());
            return groupResolver.isOwnedBy(anchor, userId);
        }
        if (AuthFacade.USER_TYPE_STUDENT.equals(userType)) {
            return groupResolver.isVisibleToStudent(v.getTeachInfoId(), userId);
        }
        return false;
    }

    /** 从路径末段解析 videoId（/ws/video/{id}）。 */
    private Long extractVideoId(URI uri) {
        String path = uri.getPath();
        if (path == null) {
            return null;
        }
        int idx = path.lastIndexOf('/');
        if (idx < 0 || idx == path.length() - 1) {
            return null;
        }
        try {
            return Long.valueOf(path.substring(idx + 1));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private String extractToken(URI uri) {
        String query = uri.getQuery();
        if (query == null || query.isBlank()) {
            return null;
        }
        for (String pair : query.split("&")) {
            int idx = pair.indexOf('=');
            if (idx > 0 && "token".equals(pair.substring(0, idx))) {
                return pair.substring(idx + 1);
            }
        }
        return null;
    }
}
