package com.xrq.xxq.module.teachinfo.cache;

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
import tools.jackson.databind.JavaType;
import tools.jackson.databind.ObjectMapper;

@Slf4j
@Component
@RequiredArgsConstructor
public class ClassScheduleCacheManager {

    private static final String USER_PREFIX = "schedule:user:";
    private static final String CLASS_COURSES_PREFIX = "schedule:class-courses:";
    private static final Duration USER_TTL = Duration.ofMinutes(15);
    private static final Duration CLASS_COURSES_TTL = Duration.ofHours(1);

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    private String writeCourses(List<CourseDto> courses) {
        try {
            // CourseDto 按 category 多态（@JsonTypeInfo），序列化时必须显式声明元素类型，
            // 否则泛型擦除后按 Object 处理，丢失 category 类型标识导致反序列化失败。
            JavaType listType = objectMapper.getTypeFactory().constructParametricType(List.class, CourseDto.class);
            return objectMapper.writerFor(listType).writeValueAsString(courses);
        } catch (JacksonException e) {
            log.warn("序列化课表缓存失败", e);
            return null;
        }
    }

    // ── 用户维度：listByUserScope ──

    private String userKey(String userType, Long userId, Long teacherId, Long courseId, Integer week) {
        return USER_PREFIX + userType + ":" + userId
                + ":t" + (teacherId != null ? teacherId : "_")
                + ":c" + (courseId != null ? courseId : "_")
                + ":w" + (week != null ? week : "_");
    }

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
        String json = writeCourses(courses);
        if (json != null) {
            redisTemplate.opsForValue().set(userKey(userType, userId, teacherId, courseId, week), json, USER_TTL);
        }
    }

    /** 清除指定用户的所有维度缓存（学生/教师/院系切换班级或授课变更时调用）。 */
    public void evictUserScope(Long userId) {
        var keys = redisTemplate.keys(USER_PREFIX + "*:" + userId + ":*");
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

    /**
     * 授课安排变更后的缓存淘汰（按班级名触发）。
     * <p>
     * 历史上的「班级+周次」维度缓存（schedule:class:）与学生/教师 user 维度本质是
     * 同一批课表数据以两套 key 各存一份，冗余且淘汰易漏；该维度的读写早已无调用方，
     * 故移除。班级名无法反查 user 维度的具体 key，而授课变更属低频管理操作，
     * 这里直接清空全部课表缓存保证一致性。参数保留仅为兼容既有调用方。
     */
    public void evictByClassNames(String classNames) {
        clearAll();
    }

    /** 清空所有课表缓存（授课变更、排课完成等全量刷新场景）。 */
    public void clearAll() {
        for (String prefix : List.of(USER_PREFIX, CLASS_COURSES_PREFIX)) {
            var keys = redisTemplate.keys(prefix + "*");
            if (keys != null && !keys.isEmpty()) {
                redisTemplate.delete(keys);
            }
        }
    }
}
