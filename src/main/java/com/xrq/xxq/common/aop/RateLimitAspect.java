package com.xrq.xxq.common.aop;

import com.xrq.xxq.common.BusinessException;
import com.xrq.xxq.common.RateLimitException;
import com.xrq.xxq.common.Result;
import com.xrq.xxq.module.user.controller.LoginController;
import com.xrq.xxq.module.user.dto.LoginRequest;
import com.xrq.xxq.util.auth.AuthFacade;
import com.xrq.xxq.util.auth.LoginSessionStore;
import com.xrq.xxq.util.auth.UserSession;
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
 * 2. 登录限流：POST /api/login 按复合客户端维度（公网 IP 即 X-Forwarded-For 第一跳
 *    + 前端上报的 X-Client-Private-IP 内网 IP，缺失时纯公网 IP）；
 * 3. 账号锁定：account 渠道连续失败 N 次锁 M 分钟（OAuth 渠道无账号概念，仅受登录限流）。
 * <p>
 * 登录成功（全渠道，按返回 Result.code=200 判定）后追加两笔「登记」：
 * a. 把登录时的公网/内网 IP 写入会话（{@link UserSession#loginPublicIp}/{@link UserSession#loginPrivateIp}），
 *    供后续操作日志按「公网IP|内网IP」显示（见 ApiAccessLogAspect）；
 * b. 内网 IP 获取失败时删除纯公网 IP 的当分钟登录计数登记 —— 纯公网桶被校园网 NAT 同出口
 *    所有设备共享，成功登录已证明是正常用户，不应消耗共享额度。
 */
@Aspect
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
@RequiredArgsConstructor
public class RateLimitAspect {

    private static final String SELECTION_API_PREFIX = "/api/selection";
    private static final String FILE_API_PREFIX = "/api/file";

    private final RateLimitService rateLimitService;
    private final AuthFacade authFacade;
    private final LoginSessionStore sessionStore;

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
        rateLimitService.checkGlobalLimit(bucket, who, api, ip, tierOf(request.getRequestURI()));
        return joinPoint.proceed();
    }

    /**
     * 按 URI 前缀选择限流档位。宽容档使用独立计数桶，不与全局桶互相挤占额度。
     * <p>/api/file/** 必须独立：2GB ÷ 5MB = 410 个分片请求，落在 120/分钟的默认档上必然误伤。
     */
    private static RateLimitService.Tier tierOf(String uri) {
        if (uri.startsWith(FILE_API_PREFIX)) {
            return RateLimitService.Tier.FILE;
        }
        if (uri.startsWith(SELECTION_API_PREFIX)) {
            return RateLimitService.Tier.SELECTION;
        }
        return RateLimitService.Tier.GLOBAL;
    }

    /** 登录防护：账号锁定检查 -> 登录限流（复合客户端 key） -> 全局桶（同 key） -> 按结果累计失败/清计数。 */
    private Object rateLimitLogin(ProceedingJoinPoint joinPoint, HttpServletRequest request) throws Throwable {
        String ip = WebRequestUtils.clientIp(request);
        String clientKey = WebRequestUtils.loginClientKey(request);
        String account = extractAccount(joinPoint.getArgs());

        // 锁定检查在最前：锁定中的请求不再消耗登录计数与失败计数
        if (account != null) {
            rateLimitService.checkAccountLock(account);
        }
        rateLimitService.checkLoginClientLimit(clientKey);
        rateLimitService.checkGlobalLimit("ip:" + clientKey, "匿名", "POST /api/login", ip,
                RateLimitService.Tier.GLOBAL);

        try {
            Object result = joinPoint.proceed();
            // OAuth 渠道失败是返回 Result.fail 而非抛异常，两种形态都要识别
            boolean success = result instanceof Result<?> r && Integer.valueOf(200).equals(r.getCode());
            if (success) {
                afterLoginSuccess(result, request, ip);
                if (account != null) {
                    rateLimitService.recordLoginSuccess(account);
                }
            } else if (account != null) {
                rateLimitService.recordLoginFailure(account, ip);
            }
            return result;
        } catch (BusinessException e) {
            if (account != null) {
                rateLimitService.recordLoginFailure(account, ip);
            }
            throw e;
        }
    }

    /**
     * 登录成功后的两笔登记（全渠道）：
     * 1. 内网 IP 获取失败时，删除纯公网 IP 的当分钟登录计数登记（NAT 共享桶不误伤正常用户）；
     * 2. 登录时的公网/内网 IP 写入会话，供后续操作日志按「公网IP|内网IP」显示。
     *    注意必须重新反序列化一份会话再回写，不能改动响应里的 UserSession 实例，
     *    否则登录响应体会带出 IP 字段。
     */
    private void afterLoginSuccess(Object result, HttpServletRequest request, String ip) {
        String privateIp = WebRequestUtils.clientPrivateIp(request);
        if (privateIp == null) {
            rateLimitService.clearLoginClientLimit(ip);
        }
        if (result instanceof Result<?> r && r.getData() instanceof UserSession session
                && session.getTokenId() != null) {
            UserSession stored = sessionStore.get(session.getTokenId());
            if (stored != null) {
                stored.setLoginPublicIp(ip);
                stored.setLoginPrivateIp(privateIp);
                sessionStore.put(session.getTokenId(), stored);
            }
        }
    }

    private boolean isLoginEndpoint(ProceedingJoinPoint joinPoint) {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        return signature.getDeclaringType() == LoginController.class && "login".equals(signature.getName());
    }

    /** 仅 account 渠道提取登录账号；OAuth 渠道返回 null（无账号概念，仅受登录限流）。 */
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
