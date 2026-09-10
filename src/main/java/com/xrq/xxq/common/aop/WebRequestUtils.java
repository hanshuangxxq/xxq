package com.xrq.xxq.common.aop;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.regex.Pattern;

/** AOP 层共享的 Web 请求工具：当前请求获取与客户端 IP 解析。 */
final class WebRequestUtils {

    /** 前端上报内网 IP 头的合法字符集（IPv4/IPv6，最长 45）。 */
    private static final Pattern PRIVATE_IP_PATTERN = Pattern.compile("[0-9a-fA-F.:]{1,45}");

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

    /**
     * 登录限流维度 key：公网出口 IP（X-Forwarded-For 第一跳，见 {@link #clientIp}）
     * + 前端上报的内网 IP（X-Client-Private-IP），以 "|" 拼接。登录前无法精确到用户，
     * 校园网 NAT 下同公网 IP 的不同设备靠内网 IP 区分额度；头缺失或格式非法时退化为纯公网 IP。
     */
    static String loginClientKey(HttpServletRequest request) {
        String publicIp = clientIp(request);
        String privateIp = clientPrivateIp(request);
        return privateIp == null ? publicIp : publicIp + "|" + privateIp;
    }

    /**
     * 前端上报的内网 IP（X-Client-Private-IP），缺失或格式非法返回 null。
     * 头完全由客户端控制，仅接受 IP 字符集（IPv4/IPv6），防日志注入与 Redis key 污染。
     */
    static String clientPrivateIp(HttpServletRequest request) {
        if (request == null) {
            return null;
        }
        String privateIp = request.getHeader("X-Client-Private-IP");
        if (isUnknownIp(privateIp)) {
            return null;
        }
        String trimmed = privateIp.trim();
        return PRIVATE_IP_PATTERN.matcher(trimmed).matches() ? trimmed : null;
    }
}
