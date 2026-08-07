package com.xrq.xxq.util;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import com.xrq.xxq.common.BusinessException;

import lombok.RequiredArgsConstructor;

/**
 * 基于 Redis 的分布式锁，用于在移除数据库唯一约束后保护高并发唯一性校验的竞态窗口。
 *
 * <p>
 * 实现：{@code SET key token NX EX seconds} 加锁 + Lua 比较删除释放（防误删他人锁）。
 * 复用项目既有 StringRedisTemplate 基础设施（与选课计数器同源）。
 *
 * <p>
 * 典型用法：把「查重 + 插入」包裹在锁内，关闭并发下两个请求同时通过查重的窗口。
 * <pre>
 * distributedLock.withLock("sel:" + campaignId + ":" + studentId, 30, () -&gt; {
 *     // 查重 + 插入
 * });
 * </pre>
 */
@Component
@RequiredArgsConstructor
public class DistributedLock {

    private static final String LOCK_PREFIX = "lock:";

    /** 释放锁的 Lua 脚本：仅当 token 匹配才删除，避免误删已被他人重新占用的锁。 */
    private static final DefaultRedisScript<Long> UNLOCK_SCRIPT = new DefaultRedisScript<>(
            "if redis.call('get',KEYS[1])==ARGV[1] then return redis.call('del',KEYS[1]) else return 0 end",
            Long.class);

    private final StringRedisTemplate redisTemplate;

    /**
     * 在分布式锁保护下执行动作。获取不到锁立即失败（快速拒绝并发请求）。
     *
     * @param key           锁键（不含前缀）
     * @param expireSeconds 锁过期秒数（防持有方崩溃导致死锁）
     * @param action        受保护动作（通常为“查重 + 插入”）
     * @return 动作返回值
     */
    public <T> T withLock(String key, long expireSeconds, Supplier<T> action) {
        String token = UUID.randomUUID().toString();
        String fullKey = LOCK_PREFIX + key;
        Boolean acquired = redisTemplate.opsForValue()
                .setIfAbsent(fullKey, token, expireSeconds, TimeUnit.SECONDS);
        if (!Boolean.TRUE.equals(acquired)) {
            throw new BusinessException(409, "操作过于频繁，请稍后重试");
        }
        try {
            return action.get();
        } finally {
            redisTemplate.execute(UNLOCK_SCRIPT, List.of(fullKey), token);
        }
    }
}
