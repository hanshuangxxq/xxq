package com.xrq.xxq.common.aop;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/** AOP 层共享的 Web 请求工具：当前请求获取与客户端 IP 解析。 */
final class WebRequestUtils {

    private WebRequestUtils() {
    }

    /** 当前线程绑定的请求；非 Web 环境返回 null。 */
    static HttpServletRequest currentRequest() {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes) {
            return attributes.getRequest();
        }
        return null;
    }

    /** 客户端 IP：反向代理场景优先取转发头，X-Forwarded-For 多级时取第一个（真实客户端）。 */
    static String clientIp(HttpServletRequest request) {
        if (request == null) {
            return "-";
        }
        String ip = request.getHeader("X-Forwarded-For");
        if (isUnknownIp(ip)) {
            ip = request.getHeader("X-Real-IP");
        }
        if (isUnknownIp(ip)) {
            ip = request.getRemoteAddr();
        }
        if (ip != null && ip.contains(",")) {
            ip = ip.split(",")[0].trim();
        }
        return ip;
    }

    private static boolean isUnknownIp(String ip) {
        return ip == null || ip.isBlank() || "unknown".equalsIgnoreCase(ip);
    }
}
