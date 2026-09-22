package com.xrq.xxq.module.coursework.assignment.cache;

import java.time.LocalDateTime;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;

/**
 * 作业「已查看」集合（Redis Set：coursework:viewed:{assignmentId} -> studentUserId）。
 * 学生打开作业详情即 SADD（幂等）；教师名单一次 SMEMBERS 批量回填五态。
 * Redis 丢失 = 查看统计偏差，可接受；删除作业时 {@link #clear}。
 */
@Component
@RequiredArgsConstructor
public class AssignmentViewedStore {

    private static final String PREFIX = "coursework:viewed:";

    private final StringRedisTemplate redisTemplate;

    /** 记录已查看（幂等），并按草稿同款 TTL 续期。 */
    public void markViewed(Long assignmentId, Long studentId, LocalDateTime deadline) {
        String key = PREFIX + assignmentId;
        redisTemplate.opsForSet().add(key, String.valueOf(studentId));
        redisTemplate.expire(key, AssignmentDraftStore.ttlOf(deadline));
    }

    /** 已查看学生集合（无 key 返回空集）。 */
    public Set<Long> viewers(Long assignmentId) {
        Set<String> members = redisTemplate.opsForSet().members(PREFIX + assignmentId);
        if (members == null) {
            return Set.of();
        }
        return members.stream().map(Long::valueOf).collect(Collectors.toSet());
    }

    /** 删除作业时清理。 */
    public void clear(Long assignmentId) {
        redisTemplate.delete(PREFIX + assignmentId);
    }
}
