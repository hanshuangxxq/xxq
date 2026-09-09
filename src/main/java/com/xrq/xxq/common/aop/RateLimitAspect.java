package com.xrq.xxq.common.aop;

import com.xrq.xxq.common.BusinessException;
import com.xrq.xxq.common.RateLimitException;
import com.xrq.xxq.common.Result;
import com.xrq.xxq.module.user.controller.LoginController;
import com.xrq.xxq.module.user.dto.LoginRequest;
import com.xrq.xxq.util.auth.AuthFacade;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * 限流/防抖切面：{@link Order} 最高优先级，先于 {@link ApiAccessLogAspect}（无 @Order，
 * 默认 LOWEST_PRECEDENCE）执行。被拒请求在 proceed() 之前抛出 {@link RateLimitException}，
 * 内层访问日志切面不会执行 —— 攻击流量不产生访问日志（先防抖再记日志）。
 * <p>
 * 三档规则（阈值见 application.yaml 的 rate-limit.*）：
 * 1. 全局限流：已登录按 userId 计数（硬性约定：校园网 NAT 下绝不对已登录请求用 IP 维度），
 *    未登录按 IP；/api/selection/** 走选课宽容档独立桶（选课高峰学生频繁刷新属正常行为）；
 * 2. 登录 IP 限流：POST /api/login 按客户端 IP；
 * 3. 账号锁定：account 渠道连续失败 N 次锁 M 分钟（OAuth 渠道无账号概念，仅受 IP 限流）。
 */
@Aspect
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
@RequiredArgsConstructor
public class RateLimitAspect {

    private static final String SELECTION_API_PREFIX = "/api/selection";

    private final RateLimitService rateLimitService;
    private final AuthFacade authFacade;

    @Around("within(@org.springframework.web.bind.annotation.RestController *)")
    public Object rateLimit(ProceedingJoinPoint joinPoint) throws Throwable {
        HttpServletRequest request = WebRequestUtils.currentRequest();
        if (request == null) {
            // 非 Web 线程（理论上不会命中 RestController 切点），防御性放行
            return joinPoint.proceed();
        }
        if (isLoginEndpoint(joinPoint)) {
            return rateLimitLogin(joinPoint, request);
        }

        Long userId = authFacade.currentUserId(request);
        String ip = WebRequestUtils.clientIp(request);
        String bucket = userId != null ? "u:" + userId : "ip:" + ip;
        String who = userId != null ? "用户[userId=" + userId + "]" : "匿名";
        String api = request.getMethod() + " " + request.getRequestURI();
        rateLimitService.checkGlobalLimit(bucket, who, api, ip,
                request.getRequestURI().startsWith(SELECTION_API_PREFIX));
        return joinPoint.proceed();
    }

    /** 登录防护：账号锁定检查 -> 登录 IP 限流 -> 全局桶（按 IP） -> 按结果累计失败/清计数。 */
    private Object rateLimitLogin(ProceedingJoinPoint joinPoint, HttpServletRequest request) throws Throwable {
        String ip = WebRequestUtils.clientIp(request);
        String account = extractAccount(joinPoint.getArgs());

        // 锁定检查在最前：锁定中的请求不再消耗 IP 计数与失败计数
        if (account != null) {
            rateLimitService.checkAccountLock(account);
        }
        rateLimitService.checkLoginIpLimit(ip);
        rateLimitService.checkGlobalLimit("ip:" + ip, "匿名", "POST /api/login", ip, false);

        try {
            Object result = joinPoint.proceed();
            if (account != null) {
                // OAuth 渠道失败是返回 Result.fail 而非抛异常，两种形态都要识别
                boolean success = result instanceof Result<?> r && Integer.valueOf(200).equals(r.getCode());
                if (success) {
                    rateLimitService.recordLoginSuccess(account);
                } else {
                    rateLimitService.recordLoginFailure(account, ip);
                }
            }
            return result;
        } catch (BusinessException e) {
            if (account != null) {
                rateLimitService.recordLoginFailure(account, ip);
            }
            throw e;
        }
    }

    private boolean isLoginEndpoint(ProceedingJoinPoint joinPoint) {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        return signature.getDeclaringType() == LoginController.class && "login".equals(signature.getName());
    }

    /** 仅 account 渠道提取登录账号；OAuth 渠道返回 null（无账号概念，仅受 IP 限流）。 */
    private String extractAccount(Object[] args) {
        for (Object arg : args) {
            if (arg instanceof LoginRequest req && "account".equals(req.getType())) {
                if (req.getData().get("account") instanceof String account && !account.isBlank()) {
                    return account;
                }
            }
        }
        return null;
    }
}
