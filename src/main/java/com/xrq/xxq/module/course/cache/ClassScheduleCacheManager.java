package com.xrq.xxq.module.course.cache;

import java.time.Duration;
import java.util.List;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import com.xrq.xxq.module.course.dto.ClassCourseDto;
import com.xrq.xxq.module.course.dto.CourseDto;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

@Slf4j
@Component
@RequiredArgsConstructor
public class ClassScheduleCacheManager {

    private static final String CLASS_PREFIX = "schedule:class:";
    private static final String USER_PREFIX = "schedule:user:";
    private static final String CLASS_COURSES_PREFIX = "schedule:class-courses:";
    private static final Duration CLASS_TTL = Duration.ofHours(1);
    private static final Duration USER_TTL = Duration.ofMinutes(15);
    private static final Duration CLASS_COURSES_TTL = Duration.ofHours(1);

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    // ── 班级+周次维度（已有） ──

    public List<CourseDto> get(String className, Integer week) {
        String json = redisTemplate.opsForValue().get(CLASS_PREFIX + className + ":week:" + week);
        if (json == null) {
            return null;
        }
        try {
            return objectMapper.readValue(json, new TypeReference<List<CourseDto>>() {});
        } catch (JacksonException e) {
            log.warn("反序列化课表缓存失败, className={}, week={}", className, week, e);
            return null;
        }
    }

    public void put(String className, Integer week, List<CourseDto> courses) {
        try {
            String json = objectMapper.writeValueAsString(courses);
            redisTemplate.opsForValue().set(CLASS_PREFIX + className + ":week:" + week, json, CLASS_TTL);
        } catch (JacksonException e) {
            log.warn("序列化课表缓存失败, className={}, week={}", className, week, e);
        }
    }

    public void evict(String className) {
        var keys = redisTemplate.keys(CLASS_PREFIX + className + ":week:*");
        if (keys != null && !keys.isEmpty()) {
            redisTemplate.delete(keys);
        }
    }

    // ── 用户维度：listByUserScope ──

    private String userKey(String userType, Long userId, Long teacherId, Long courseId, Integer week) {
        return USER_PREFIX + userType + ":" + userId
                + ":t" + (teacherId != null ? teacherId : "_")
                + ":c" + (courseId != null ? courseId : "_")
                + ":w" + (week != null ? week : "_");
    }

    private static final String USER_KEY_PREFIX_LIKE = "schedule:user:";

    public List<CourseDto> getUserScope(String userType, Long userId, Long teacherId, Long courseId, Integer week) {
        String json = redisTemplate.opsForValue().get(userKey(userType, userId, teacherId, courseId, week));
        if (json == null) {
            return null;
        }
        try {
            return objectMapper.readValue(json, new TypeReference<List<CourseDto>>() {});
        } catch (JacksonException e) {
            log.warn("反序列化用户课表缓存失败, userId={}, userType={}", userId, userType, e);
            return null;
        }
    }

    public void putUserScope(String userType, Long userId, Long teacherId, Long courseId,
                             Integer week, List<CourseDto> courses) {
        try {
            String json = objectMapper.writeValueAsString(courses);
            redisTemplate.opsForValue().set(userKey(userType, userId, teacherId, courseId, week), json, USER_TTL);
        } catch (JacksonException e) {
            log.warn("序列化用户课表缓存失败, userId={}, userType={}", userId, userType, e);
        }
    }

    /** 清除指定用户的所有维度缓存（学生/教师/院系切换班级或授课变更时调用）。 */
    public void evictUserScope(Long userId) {
        var keys = redisTemplate.keys(USER_KEY_PREFIX_LIKE + "*:" + userId + ":*");
        if (keys != null && !keys.isEmpty()) {
            redisTemplate.delete(keys);
        }
    }

    // ── 班级课程维度：listClassCourses ──

    public List<ClassCourseDto> getClassCourses(Long userId) {
        String json = redisTemplate.opsForValue().get(CLASS_COURSES_PREFIX + userId);
        if (json == null) {
            return null;
        }
        try {
            return objectMapper.readValue(json, new TypeReference<List<ClassCourseDto>>() {});
        } catch (JacksonException e) {
            log.warn("反序列化班级课程缓存失败, userId={}", userId, e);
            return null;
        }
    }

    public void putClassCourses(Long userId, List<ClassCourseDto> courses) {
        try {
            String json = objectMapper.writeValueAsString(courses);
            redisTemplate.opsForValue().set(CLASS_COURSES_PREFIX + userId, json, CLASS_COURSES_TTL);
        } catch (JacksonException e) {
            log.warn("序列化班级课程缓存失败, userId={}", userId, e);
        }
    }

    /** 针对 className 中每一个班级名执行淘汰（合班时拆分）。 */
    public void evictByClassNames(String classNames) {
        if (classNames == null || classNames.isBlank()) {
            return;
        }
        for (String name : classNames.split(",")) {
            String trimmed = name.strip();
            if (!trimmed.isEmpty()) {
                evict(trimmed);
            }
        }
    }

    /** 批量删除匹配的所有 key（用于全量刷新场景，谨慎使用）。 */
    public void clearAll() {
        for (String prefix : List.of(CLASS_PREFIX, USER_PREFIX, CLASS_COURSES_PREFIX)) {
            var keys = redisTemplate.keys(prefix + "*");
            if (keys != null && !keys.isEmpty()) {
                redisTemplate.delete(keys);
            }
        }
    }
}
