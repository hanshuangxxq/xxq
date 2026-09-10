package com.xrq.xxq.common.aop;

import com.xrq.xxq.common.BusinessException;
import com.xrq.xxq.util.auth.AuthFacade;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.validation.BindingResult;
import org.springframework.web.multipart.MultipartFile;
import tools.jackson.databind.ObjectMapper;

import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Controller 访问日志切面：拦截所有 {@code @RestController} 方法，记录
 * 「谁（userId/userType/role + 客户端 IP）、访问了哪个接口（HTTP 方法 + URI + 控制器方法）、
 * 干了什么（请求参数）、耗时多久」。
 * <p>
 * 用户上下文由 {@link com.xrq.xxq.config.AuthInterceptor} 解析 JWT 后注入 request attribute，
 * 统一经 {@link AuthFacade} 读取；未鉴权接口（如 /api/login）记为「匿名」。
 * IP 显示优先用登录成功时登记在会话中的「公网IP|内网IP」（内网 IP 为前端登录时上报的
 * X-Client-Private-IP，校园网 NAT 下可追溯具体设备）；未登录或无登记时回退实时解析
 * （X-Forwarded-For 第一跳 / X-Real-IP / remoteAddr）。
 * 参数序列化走 Jackson（业务统一 tools.jackson），{@code *password*} 字段打码；
 * Servlet/框架注入对象（request/response/session 等）不计入参数；
 * 文件上传只记文件名与大小；超长参数（如批量导入列表）截断。
 * 耗时超过 {@code api-log.slow-threshold-ms}（默认 3 分钟）的成功请求，额外写入 slow 日志文件便于运维排查。
 */
@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
public class ApiAccessLogAspect {

    /** 单行参数总长度上限，防止批量接口刷爆日志。 */
    private static final int MAX_PARAMS_LENGTH = 1000;

    /** JSON 风格 "xxxPasswordXxx":"value" 打码规则。 */
    private static final Pattern JSON_PASSWORD = Pattern.compile("(?i)(\"[^\"]*password[^\"]*\"\\s*:\\s*\")[^\"]*(\")");
    /** toString 风格 password=value 打码规则。 */
    private static final Pattern TOSTRING_PASSWORD = Pattern.compile("(?i)(password=)[^,)\\s\\]]+");
    private static final String MASK = "******";

    /** 慢请求专用 logger：耗时超阈值的成功请求额外写一份，logback 按此名字路由到 slow 日志文件。 */
    private static final Logger slowLog = LoggerFactory.getLogger("slow-api");

    private final AuthFacade authFacade;
    private final ObjectMapper objectMapper;

    /** 慢请求阈值（毫秒），默认 3 分钟；可用 api-log.slow-threshold-ms 覆盖。 */
    @Value("${api-log.slow-threshold-ms:180000}")
    private long slowThresholdMs;

    @Around("within(@org.springframework.web.bind.annotation.RestController *)")
    public Object logApiAccess(ProceedingJoinPoint joinPoint) throws Throwable {
        long start = System.currentTimeMillis();
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        String handler = signature.getDeclaringType().getSimpleName() + "#" + signature.getName();

        HttpServletRequest request = WebRequestUtils.currentRequest();
        String user = describeUser(request);
        String ip = describeClientIp(request);
        String api = request == null ? "-" : request.getMethod() + " " + request.getRequestURI();
        String params = describeParams(signature, joinPoint.getArgs());

        try {
            Object result = joinPoint.proceed();
            long elapsed = System.currentTimeMillis() - start;
            log.info("API访问 | {} | IP:{} | {} | {} | 参数: {} | 耗时: {}ms", user, ip, api, handler, params, elapsed);
            if (elapsed > slowThresholdMs) {
                // 慢请求额外记录到 slow 日志文件（info.log 中的原始记录保留）
                slowLog.warn("慢接口 | {} | IP:{} | {} | {} | 参数: {} | 耗时: {}ms", user, ip, api, handler, params, elapsed);
            }
            return result;
        } catch (Throwable e) {
            long elapsed = System.currentTimeMillis() - start;
            if (e instanceof BusinessException || e instanceof IllegalArgumentException) {
                // 业务异常：单行摘要即可（GlobalExceptionHandler 已记录）
                log.warn("API异常 | {} | IP:{} | {} | {} | 参数: {} | 耗时: {}ms | 异常: {}",
                        user, ip, api, handler, params, elapsed, e.toString());
            } else {
                // 非预期异常：附堆栈便于排查（GlobalExceptionHandler 只记 message）；末位 Throwable 由 slf4j 特殊处理，占位用 e.toString()
                log.error("API异常 | {} | IP:{} | {} | {} | 参数: {} | 耗时: {}ms | 异常: {}",
                        user, ip, api, handler, params, elapsed, e.toString(), e);
            }
            throw e;
        }
    }

    private String describeUser(HttpServletRequest request) {
        if (request == null) {
            return "用户[-]";
        }
        Long userId = authFacade.currentUserId(request);
        if (userId == null) {
            // /api/login、/api/login/refresh 等放行接口，attribute 尚未注入
            return "用户[匿名]";
        }
        return "用户[userId=%d, userType=%s, role=%s]".formatted(
                userId, authFacade.currentUserType(request), authFacade.currentRole(request));
    }

    /**
     * 客户端 IP 显示：已登录请求优先用登录成功时登记的「公网IP|内网IP」（会话携带，
     * 校园网 NAT 下可追溯具体设备）；未登录/无登记时回退实时解析（X-Forwarded-For 第一跳等）。
     */
    private String describeClientIp(HttpServletRequest request) {
        if (request != null) {
            String loginIp = authFacade.currentLoginIp(request);
            if (loginIp != null) {
                return loginIp;
            }
        }
        return WebRequestUtils.clientIp(request);
    }

    private String describeParams(MethodSignature signature, Object[] args) {
        if (args == null || args.length == 0) {
            return "无";
        }
        String[] names = signature.getParameterNames();
        List<String> parts = new ArrayList<>(args.length);
        for (int i = 0; i < args.length; i++) {
            String value = describeArg(args[i]);
            if (value == null) {
                continue; // 框架注入对象，不属于业务参数
            }
            String name = names != null && i < names.length ? names[i] : "arg" + i;
            parts.add(name + "=" + value);
        }
        String joined = String.join(", ", parts);
        return joined.isEmpty() ? "无" : truncate(joined);
    }

    /** 返回 null 表示该参数是框架注入对象，无需记录。 */
    private String describeArg(Object arg) {
        switch (arg) {
            case null -> {
                return "null";
            }
            case HttpServletRequest ignored -> {
                return null;
            }
            case HttpServletResponse ignored -> {
                return null;
            }
            case HttpSession ignored -> {
                return null;
            }
            case BindingResult ignored -> {
                return null;
            }
            case InputStream ignored -> {
                return null;
            }
            case OutputStream ignored -> {
                return null;
            }
            case byte[] bytes -> {
                return "<字节数组, " + bytes.length + "B>";
            }
            case MultipartFile file -> {
                return "<文件:" + file.getOriginalFilename() + ", " + file.getSize() + "B>";
            }
            case MultipartFile[] files -> {
                return "<文件x" + files.length + ">";
            }
            case CharSequence s -> {
                return mask(s.toString());
            }
            case Number n -> {
                return n.toString();
            }
            case Boolean b -> {
                return b.toString();
            }
            case Enum<?> e -> {
                return e.toString();
            }
            default -> {
                return mask(serialize(arg));
            }
        }
    }

    private String serialize(Object arg) {
        try {
            return objectMapper.writeValueAsString(arg);
        } catch (Exception e) {
            return String.valueOf(arg);
        }
    }

    /** 密码类字段打码（兼容 JSON 与 Lombok toString 两种文本形态）。 */
    private String mask(String text) {
        String masked = JSON_PASSWORD.matcher(text).replaceAll("$1" + MASK + "$2");
        return TOSTRING_PASSWORD.matcher(masked).replaceAll("$1" + MASK);
    }

    private String truncate(String text) {
        if (text.length() <= MAX_PARAMS_LENGTH) {
            return text;
        }
        return text.substring(0, MAX_PARAMS_LENGTH) + "...(共" + text.length() + "字符,已截断)";
    }
}
