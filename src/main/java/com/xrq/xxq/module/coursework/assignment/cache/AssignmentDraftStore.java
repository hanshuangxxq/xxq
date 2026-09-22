package com.xrq.xxq.module.coursework.assignment.cache;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;

/**
 * 作业作答草稿（Redis Hash：coursework:draft:{assignmentId}:{studentId}，field=questionId，value=答案 JSON）。
 * <p>
 * 草稿只进 Redis 不落 SQL —— 提交是唯一 SQL 写入口（提交成功后 {@link #clear}）。
 * TTL = max(截止时间 + 7 天, 7 天)：迟交窗口内草稿不失效，每次写入刷新。
 * Redis 丢失 = 学生重答，可接受的低价值易失数据。
 */
@Component
@RequiredArgsConstructor
public class AssignmentDraftStore {

    private static final String PREFIX = "coursework:draft:";
    private static final Duration MIN_TTL = Duration.ofDays(7);

    private final StringRedisTemplate redisTemplate;

    /** 草稿 TTL：max(deadline + 7 天, 现在 + 7 天)。已截止的作业也给 7 天迟交窗口。 */
    public static Duration ttlOf(LocalDateTime deadline) {
        Duration d = Duration.between(LocalDateTime.now(), deadline.plusDays(7));
        return d.compareTo(MIN_TTL) < 0 ? MIN_TTL : d;
    }

    /** 全量覆盖保存（DEL + HSET：被删掉的题/答案不会残留）。空 map = 清除草稿。 */
    public void save(Long assignmentId, Long studentId, LocalDateTime deadline, Map<String, String> answers) {
        String key = key(assignmentId, studentId);
        redisTemplate.delete(key);
        if (answers != null && !answers.isEmpty()) {
            redisTemplate.opsForHash().putAll(key, answers);
            redisTemplate.expire(key, ttlOf(deadline));
        }
    }

    /** 读取草稿：questionId（字符串） → 答案 JSON 子串。无草稿返回空 Map。 */
    public Map<String, String> load(Long assignmentId, Long studentId) {
        Map<Object, Object> raw = redisTemplate.opsForHash().entries(key(assignmentId, studentId));
        Map<String, String> result = new HashMap<>(raw.size());
        raw.forEach((k, v) -> result.put(String.valueOf(k), String.valueOf(v)));
        return result;
    }

    /** 提交成功后清除草稿。 */
    public void clear(Long assignmentId, Long studentId) {
        redisTemplate.delete(key(assignmentId, studentId));
    }

    /** 批量探测哪些学生有草稿（pipeline EXISTS，供教师名单「暂存中」状态，无 N+1）。 */
    public Set<Long> draftingStudents(Long assignmentId, Collection<Long> studentIds) {
        if (studentIds == null || studentIds.isEmpty()) {
            return Set.of();
        }
        List<Long> ids = List.copyOf(studentIds);
        List<Object> results = redisTemplate.executePipelined((RedisCallback<Object>) connection -> {
            for (Long sid : ids) {
                connection.keyCommands().exists(key(assignmentId, sid).getBytes(StandardCharsets.UTF_8));
            }
            return null;
        });
        Set<Long> drafting = new HashSet<>();
        for (int i = 0; i < ids.size(); i++) {
            if (Boolean.TRUE.equals(results.get(i))) {
                drafting.add(ids.get(i));
            }
        }
        return drafting;
    }

    private static String key(Long assignmentId, Long studentId) {
        return PREFIX + assignmentId + ":" + studentId;
    }
}
