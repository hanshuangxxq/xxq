package com.xrq.xxq.module.practice.internship.cache;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;

/**
 * 实习「待审核报名」的 Redis 跟踪。
 * <p>
 * 待审核（PENDING）报名不占 internship.selected_count（容量仅统计审核通过人数），
 * 以 Set「practice:internship:pending:{internshipId} -> studentId」驻留 Redis；
 * 审核（通过/驳回）或学生撤销时移出，删除实习项目时清理 key。
 * internship_application 表仍是事实源，本组件只提供待审核人数的快速读数。
 */
@Component
@RequiredArgsConstructor
public class InternshipPendingStore {

    private static final String PREFIX = "practice:internship:pending:";

    private final StringRedisTemplate redisTemplate;

    /** 学生报名后登记为待审核。 */
    public void markPending(Long internshipId, Long studentId) {
        redisTemplate.opsForSet().add(key(internshipId), String.valueOf(studentId));
    }

    /** 审核（通过/驳回）或学生撤销后移出待审核集合。 */
    public void unmarkPending(Long internshipId, Long studentId) {
        redisTemplate.opsForSet().remove(key(internshipId), String.valueOf(studentId));
    }

    /** 当前待审核人数（SCARD，key 不存在时为 0）。 */
    public int pendingCount(Long internshipId) {
        Long size = redisTemplate.opsForSet().size(key(internshipId));
        return size == null ? 0 : size.intValue();
    }

    /** 删除实习项目时清理 key。 */
    public void clear(Long internshipId) {
        redisTemplate.delete(key(internshipId));
    }

    private String key(Long internshipId) {
        return PREFIX + internshipId;
    }
}
