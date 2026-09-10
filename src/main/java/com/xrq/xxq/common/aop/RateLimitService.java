package com.xrq.xxq.common.aop;

import com.xrq.xxq.common.RateLimitException;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * 限流计数服务：全局限流（按账号/按 IP）、选课宽容档、登录限流（公网 IP + 内网 IP 复合客户端维度）、
 * 公网 IP 登录计数删除登记、登录失败计数与账号锁定。
 * <p>
 * 计数全部走 Redis 固定窗口（分钟桶 key 后缀 yyyyMMddHHmm），INCR+EXPIRE 用 Lua 脚本原子完成
 * （沿用 selection 模块 Redis 计数器的 Lua 先例）。Redis 同时是登录会话存储，
 * Redis 故障时限流随之失败，与 AuthInterceptor 行为一致，不做额外降级。
 * <p>
 * 日志纪律：只在「首次触限」（计数恰好 = 阈值+1）或「账号锁定设置」时向 rate-limit
 * 专用 logger 记一条；窗口内后续拒绝完全静默，攻击者刷请求不会产生日志洪泛。
 */
@Component
@RequiredArgsConstructor
public class RateLimitService {

    private static final String GLOBAL_PREFIX = "ratelimit:global:";
    private static final String SELECTION_PREFIX = "ratelimit:selection:";
    private static final String LOGIN_IP_PREFIX = "ratelimit:login:ip:";
    private static final String FAIL_PREFIX = "ratelimit:login:fail:";
    private static final String LOCK_PREFIX = "ratelimit:login:lock:";

    /** 分钟桶 TTL：略大于窗口长度，覆盖边界竞态。 */
    private static final int BUCKET_TTL_SECONDS = 70;
    /** 失败计数窗口：失败间隔超过 10 分钟则重新累计。 */
    private static final int FAIL_WINDOW_SECONDS = 600;

    private static final DateTimeFormatter MINUTE_BUCKET = DateTimeFormatter.ofPattern("yyyyMMddHHmm");

    /** 固定窗口自增：首次自增时设置过期，原子完成。 */
    private static final DefaultRedisScript<Long> INCR_WITH_EXPIRE = new DefaultRedisScript<>(
            "local c = redis.call('INCR', KEYS[1]) "
                    + "if c == 1 then redis.call('EXPIRE', KEYS[1], ARGV[1]) end "
                    + "return c",
            Long.class);

    /** 限流专用 logger：additivity=false，只进 limit 日志文件（见 logback-spring.xml）。 */
    private static final Logger limitLog = LoggerFactory.getLogger("rate-limit");

    private final StringRedisTemplate redisTemplate;

    @Value("${rate-limit.global-per-minute:120}")
    private long globalPerMinute;

    @Value("${rate-limit.selection-per-minute:300}")
    private long selectionPerMinute;

    @Value("${rate-limit.login-ip-per-minute:20}")
    private long loginIpPerMinute;

    @Value("${rate-limit.login-max-failures:5}")
    private long loginMaxFailures;

    @Value("${rate-limit.login-lock-minutes:5}")
    private long loginLockMinutes;

    /**
     * 全局限流：同一维度一分钟内超阈值抛 429。
     * selectionScope=true（/api/selection/**）走选课宽容档独立桶，与全局桶互不累计。
     *
     * @param bucket 计数维度：已登录 "u:{userId}"，未登录 "ip:{ip}"
     * @param who    日志用身份描述，如 "用户[userId=12]" / "匿名"
     * @param api    日志用接口描述，如 "POST /api/scores"
     * @param ip     客户端 IP（日志用）
     */
    public void checkGlobalLimit(String bucket, String who, String api, String ip, boolean selectionScope) {
        long limit = selectionScope ? selectionPerMinute : globalPerMinute;
        String prefix = selectionScope ? SELECTION_PREFIX : GLOBAL_PREFIX;
        Long count = incrWithExpire(prefix + bucket + ":" + minuteBucket(), BUCKET_TTL_SECONDS);
        if (count == null) {
            return; // 脚本恒返回计数，防御性放行
        }
        if (count == limit + 1) {
            limitLog.warn("限流触发 | {} | IP:{} | {} | 阈值: {}/分钟{}",
                    who, ip, api, limit, selectionScope ? "(选课宽容档)" : "");
        }
        if (count > limit) {
            throw new RateLimitException("请求过于频繁，请稍后再试");
        }
    }

    /**
     * 登录限流：同一客户端一分钟内登录尝试超阈值抛 429。
     *
     * @param clientKey 复合客户端维度：公网 IP（X-Forwarded-For 第一跳）+ 前端上报的内网 IP
     *                  （X-Client-Private-IP），内网 IP 缺失时仅公网 IP —— 校园网 NAT 下
     *                  同出口 IP 的不同设备不再互相挤占登录额度
     */
    public void checkLoginClientLimit(String clientKey) {
        Long count = incrWithExpire(LOGIN_IP_PREFIX + clientKey + ":" + minuteBucket(), BUCKET_TTL_SECONDS);
        if (count == null) {
            return;
        }
        if (count == loginIpPerMinute + 1) {
            limitLog.warn("限流触发 | 匿名 | 客户端:{} | POST /api/login | 阈值: {}/分钟(登录)", clientKey, loginIpPerMinute);
        }
        if (count > loginIpPerMinute) {
            throw new RateLimitException("登录尝试过于频繁，请稍后再试");
        }
    }

    /**
     * 删除纯公网 IP 的登录计数登记：内网 IP 获取失败的成功登录不消耗共享额度。
     * 纯公网桶被校园网 NAT 同出口的所有设备共享，成功登录已证明是正常用户而非爆破源，
     * 继续计数只会让同 NAT 的其他人被误伤限流。
     */
    public void clearLoginClientLimit(String publicIp) {
        redisTemplate.delete(LOGIN_IP_PREFIX + publicIp + ":" + minuteBucket());
        limitLog.info("登录计数删除登记 | IP:{} | 登录成功且内网IP缺失，清除公网IP当分钟登录计数", publicIp);
    }

    /** 账号锁定检查：锁定中抛 429，消息带剩余分钟数（向上取整）。 */
    public void checkAccountLock(String account) {
        Long ttl = redisTemplate.getExpire(LOCK_PREFIX + account, TimeUnit.SECONDS);
        if (ttl != null && ttl > 0) {
            long minutes = (ttl + 59) / 60;
            throw new RateLimitException("账号已临时锁定，请 " + minutes + " 分钟后重试");
        }
    }

    /** 登录成功：清除失败计数（锁定计数归零，重新累计）。 */
    public void recordLoginSuccess(String account) {
        redisTemplate.delete(FAIL_PREFIX + account);
    }

    /** 登录失败：计数 +1；达阈值则锁定账号并清失败计数，记一条锁定日志。 */
    public void recordLoginFailure(String account, String ip) {
        Long count = incrWithExpire(FAIL_PREFIX + account, FAIL_WINDOW_SECONDS);
        if (count != null && count >= loginMaxFailures) {
            redisTemplate.delete(FAIL_PREFIX + account);
            redisTemplate.opsForValue().set(LOCK_PREFIX + account, "1", Duration.ofMinutes(loginLockMinutes));
            limitLog.warn("账号锁定 | 账号:{} | IP:{} | 连续失败: {} | 锁定时长: {}分钟",
                    account, ip, count, loginLockMinutes);
        }
    }

    private Long incrWithExpire(String key, int ttlSeconds) {
        return redisTemplate.execute(INCR_WITH_EXPIRE, List.of(key), String.valueOf(ttlSeconds));
    }

    private String minuteBucket() {
        return MINUTE_BUCKET.format(LocalDateTime.now());
    }
}
