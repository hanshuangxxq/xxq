package com.xrq.xxq.config;

import com.xrq.xxq.util.JwtUtils;
import com.xrq.xxq.util.auth.AuthFacade;
import com.xrq.xxq.util.auth.LoginSessionStore;
import com.xrq.xxq.util.auth.RequireAuth;
import com.xrq.xxq.util.auth.UserSession;
import com.xrq.xxq.util.auth.UserType;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import java.io.IOException;

/**
 * 登录门禁 + 类型鉴权拦截器。
 * <p>
 * 1. 解析 Bearer JWT 并校验 Redis 会话，将 userId/userType/role/tokenId 注入 request attribute；
 * 2. 执行 {@link RequireAuth} 注解鉴权：方法级优先、类级兜底，两者皆无默认拒绝（403），
 *    {@code value()} 为空数组表示任意已登录用户。无 token/过期/无效一律 401，先于 403。
 */
@Slf4j
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
            writeError(response, 401, "请先登录");
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
            UserSession session = sessionStore.get(tokenId);
            if (session == null) {
                // token 仍有效但 Redis 无会话（如后端/Redis 重启致数据丢失）：
                // 基于 claims 重建最小会话并放行，避免前端被迫重新登录。
                // 安全权衡：登出后在此 token 有效期内（默认 30m）仍可被重建访问。
                session = sessionStore.rebuildIfNeeded(tokenId,
                        Long.valueOf(claims.getSubject()),
                        claims.get("userType", String.class),
                        claims.get("role", String.class));
            }
            // 登录成功时登记的内网 IP 带入请求上下文，供访问日志按「公网IP|内网IP」显示；
            // 无登记（内网 IP 获取失败/重建会话/存量旧会话）则不注入，日志回退实时解析的 IP。
            if (session.getLoginPrivateIp() != null && !session.getLoginPrivateIp().isBlank()) {
                request.setAttribute(AuthFacade.ATTR_LOGIN_IP,
                        session.getLoginPublicIp() + "|" + session.getLoginPrivateIp());
            }

            return checkRequireAuth(handler, request, response, claims.get("userType", String.class));
        } catch (ExpiredJwtException e) {
            writeError(response, 401, "token已过期，请刷新");
            return false;
        } catch (JwtException e) {
            writeError(response, 401, "token无效");
            return false;
        }
    }

    /**
     * 执行 @RequireAuth 类型鉴权：方法级注解优先，类级兜底，两者皆无默认拒绝。
     * 非 Controller handler（如静态资源）跳过——token 校验已完成，且 /api/** 下目前不存在此类 handler。
     */
    private boolean checkRequireAuth(Object handler, HttpServletRequest request,
                                     HttpServletResponse response, String userType) throws IOException {
        if (!(handler instanceof HandlerMethod handlerMethod)) {
            return true;
        }
        RequireAuth requireAuth = handlerMethod.getMethodAnnotation(RequireAuth.class);
        if (requireAuth == null) {
            requireAuth = AnnotationUtils.findAnnotation(handlerMethod.getBeanType(), RequireAuth.class);
        }
        if (requireAuth == null) {
            log.warn("接口缺少 @RequireAuth 注解，默认拒绝: {} {}", request.getMethod(), request.getRequestURI());
            writeError(response, 403, "权限不足");
            return false;
        }
        UserType[] allowed = requireAuth.value();
        if (allowed.length == 0) {
            // 空数组 = 任意已登录用户
            return true;
        }
        for (UserType type : allowed) {
            if (type.getCode().equals(userType)) {
                return true;
            }
        }
        writeError(response, 403, "权限不足");
        return false;
    }

    private void writeError(HttpServletResponse response, int status, String message) throws IOException {
        response.setContentType("application/json;charset=UTF-8");
        response.setStatus(status);
        response.getWriter().write("{\"code\":" + status + ",\"message\":\"" + message + "\"}");
    }
}
