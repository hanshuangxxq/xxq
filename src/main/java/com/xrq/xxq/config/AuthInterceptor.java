package com.xrq.xxq.config;

import com.xrq.xxq.util.JwtUtils;
import com.xrq.xxq.util.auth.LoginSessionStore;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
@RequiredArgsConstructor
public class AuthInterceptor implements HandlerInterceptor {

    private final JwtUtils jwtUtils;
    private final LoginSessionStore sessionStore;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }

        String authHeader = request.getHeader("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            response.setContentType("application/json;charset=UTF-8");
            response.setStatus(401);
            response.getWriter().write("{\"code\":401,\"message\":\"请先登录\"}");
            return false;
        }

        String token = authHeader.substring(7);
        try {
            Claims claims = jwtUtils.parseToken(token);
            request.setAttribute("userId", Long.valueOf(claims.getSubject()));
            request.setAttribute("userType", claims.get("userType", String.class));
            request.setAttribute("role", claims.get("role", String.class));
            request.setAttribute("tokenId", claims.get("tokenId", String.class));

            String tokenId = claims.get("tokenId", String.class);
            if (sessionStore.get(tokenId) == null) {
                // token 仍有效但 Redis 无会话（如后端/Redis 重启致数据丢失）：
                // 基于 claims 重建最小会话并放行，避免前端被迫重新登录。
                // 安全权衡：登出后在此 token 有效期内（默认 30m）仍可被重建访问。
                sessionStore.rebuildIfNeeded(tokenId,
                        Long.valueOf(claims.getSubject()),
                        claims.get("userType", String.class),
                        claims.get("role", String.class));
            }
        } catch (ExpiredJwtException e) {
            response.setContentType("application/json;charset=UTF-8");
            response.setStatus(401);
            response.getWriter().write("{\"code\":401,\"message\":\"token已过期，请刷新\"}");
            return false;
        } catch (JwtException e) {
            response.setContentType("application/json;charset=UTF-8");
            response.setStatus(401);
            response.getWriter().write("{\"code\":401,\"message\":\"token无效\"}");
            return false;
        }

        return true;
    }
}
