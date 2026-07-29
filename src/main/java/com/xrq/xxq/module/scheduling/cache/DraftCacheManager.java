package com.xrq.xxq.module.scheduling.cache;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.xrq.xxq.module.clazz.entity.ClassName;
import com.xrq.xxq.module.course.entity.Course;
import com.xrq.xxq.module.teachinfo.entity.TeachInfo;
import com.xrq.xxq.module.clazz.mapper.ClassNameMapper;
import com.xrq.xxq.module.course.mapper.CourseMapper;
import com.xrq.xxq.module.user.entity.User;
import com.xrq.xxq.module.user.entity.user.Teacher;
import com.xrq.xxq.module.user.mapper.TeacherMapper;
import com.xrq.xxq.module.user.mapper.UserMapper;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

/**
 * 授课草稿缓存管理器。
 * <p>
 * 前端逐班提交授课安排到此缓存（不写库），排课时统一消费后批量入库。
 * 内存缓存 + Redis 持久化，服务重启后草稿不丢失。
 * 草稿存储为 {@link DraftItem}（含课程名、教师名），排课消费时无需再查库。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DraftCacheManager {

    private static final String REDIS_KEY = "draft:teach-info";

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final CourseMapper courseMapper;
    private final TeacherMapper teacherMapper;
    private final UserMapper userMapper;
    private final ClassNameMapper classNameMapper;

    private final List<DraftItem> drafts = Collections.synchronizedList(new ArrayList<>());

    @PostConstruct
    private void loadFromRedis() {
        String json = redisTemplate.opsForValue().get(REDIS_KEY);
        if (json == null || json.isEmpty()) {
            return;
        }
        try {
            List<DraftItem> saved = objectMapper.readValue(json, new TypeReference<List<DraftItem>>() {});
            drafts.addAll(saved);
            log.info("从 Redis 加载草稿成功, count={}", saved.size());
        } catch (Exception e) {
            log.warn("从 Redis 加载草稿失败，使用空列表", e);
        }
    }

    private void saveToRedis() {
        synchronized (drafts) {
            try {
                String json = objectMapper.writeValueAsString(new ArrayList<>(drafts));
                redisTemplate.opsForValue().set(REDIS_KEY, json);
            } catch (JacksonException e) {
                throw new RuntimeException("序列化草稿到 Redis 失败", e);
            }
        }
    }

    /**
     * 批量追加授课草稿，解析课程名、教师名和院系后富化存储。
     */
    public void addDrafts(List<TeachInfo> newDrafts) {
        if (newDrafts.isEmpty()) {
            return;
        }

        Map<Long, String> courseNames = loadCourseNames(newDrafts);
        Map<Long, String> teacherNames = loadTeacherNames(newDrafts);
        Map<String, String> classColleges = loadClassColleges(newDrafts);

        List<DraftItem> items = newDrafts.stream()
                .map(ti -> DraftItem.from(ti,
                        courseNames.getOrDefault(ti.getCourseId(), "未知课程"),
                        teacherNames.getOrDefault(ti.getTeacherId(), "未知教师"),
                        resolveCollege(ti.getClassName(), classColleges)))
                .toList();

        drafts.addAll(items);
        saveToRedis();
    }

    /** 获取当前所有草稿（副本）。 */
    public List<DraftItem> getAllDrafts() {
        synchronized (drafts) {
            return new ArrayList<>(drafts);
        }
    }

    /** 按院系名称过滤草稿——院系管理者只能查看本院系的草稿。 */
    public List<DraftItem> getDraftsByCollege(String collegeName) {
        synchronized (drafts) {
            return drafts.stream()
                    .filter(d -> {
                        String c = d.getCollege();
                        if (c == null || c.isEmpty()) {
                            return false;
                        }
                        for (String part : c.split(",")) {
                            if (part.strip().equals(collegeName)) {
                                return true;
                            }
                        }
                        return false;
                    })
                    .toList();
        }
    }

    /** 获取并清空草稿（供排课求解消费）。 */
    public List<DraftItem> consumeDrafts() {
        synchronized (drafts) {
            List<DraftItem> result = new ArrayList<>(drafts);
            drafts.clear();
            redisTemplate.delete(REDIS_KEY);
            return result;
        }
    }

    /** 清空所有草稿。 */
    public void clear() {
        drafts.clear();
        redisTemplate.delete(REDIS_KEY);
    }

    /** 按班级名称移除草稿。单班精确匹配则整条删除，合班则仅移除该班并重算院系。 */
    public void removeByClassName(String className) {
        synchronized (drafts) {
            var it = drafts.iterator();
            while (it.hasNext()) {
                DraftItem d = it.next();
                String cn = d.getClassName();
                if (className.equals(cn)) {
                    it.remove();
                } else if (containsClassName(cn, className)) {
                    String newClassName = removeClass(cn, className);
                    d.setClassName(newClassName);
                    d.setCollege(recalcCollege(newClassName));
                }
            }
        }
        saveToRedis();
    }

    private static boolean containsClassName(String draftClassName, String target) {
        if (draftClassName == null) {
            return false;
        }
        for (String part : draftClassName.split(",")) {
            if (part.strip().equals(target)) {
                return true;
            }
        }
        return false;
    }

    private static String removeClass(String className, String target) {
        List<String> parts = new ArrayList<>();
        for (String part : className.split(",")) {
            String trimmed = part.strip();
            if (!trimmed.isEmpty() && !trimmed.equals(target)) {
                parts.add(trimmed);
            }
        }
        return String.join(",", parts);
    }

    private String recalcCollege(String className) {
        if (className == null || className.isBlank()) {
            return "";
        }
        Set<String> names = new LinkedHashSet<>();
        for (String part : className.split(",")) {
            String trimmed = part.strip();
            if (!trimmed.isEmpty()) {
                names.add(trimmed);
            }
        }
        if (names.isEmpty()) {
            return "";
        }
        Map<String, String> classColleges = classNameMapper.selectList(
                new LambdaQueryWrapper<ClassName>().in(ClassName::getClassName, names))
                .stream()
                .collect(Collectors.toMap(ClassName::getClassName, ClassName::getCollege, (a, b) -> a));
        Set<String> colleges = new LinkedHashSet<>();
        for (String name : names) {
            String c = classColleges.get(name);
            if (c != null && !c.isEmpty()) {
                colleges.add(c);
            }
        }
        return String.join(",", colleges);
    }

    /** 按唯一键（courseId + teacherId + className）移除单条草稿。 */
    public boolean removeByKey(Long courseId, Long teacherId, String className) {
        synchronized (drafts) {
            boolean removed = drafts.removeIf(d ->
                    courseId.equals(d.getCourseId())
                            && teacherId.equals(d.getTeacherId())
                            && className.equals(d.getClassName()));
            if (removed) {
                saveToRedis();
            }
            return removed;
        }
    }

    /**
     * 更新草稿中指定 TeachInfo 的任课教师（用于选课分班后补录教师）。
     * 选课分班产生的草稿 id 与 TeachInfo.id 一一对应，按 id 精确匹配；
     * 若草稿不存在（已被排课消费）则静默跳过。
     */
    public void updateTeacher(Long teachInfoId, Long newTeacherId, String newTeacherName) {
        if (teachInfoId == null) {
            return;
        }
        synchronized (drafts) {
            for (DraftItem d : drafts) {
                if (teachInfoId.equals(d.getId())) {
                    d.setTeacherId(newTeacherId);
                    d.setTeacherName(newTeacherName != null && !newTeacherName.isBlank()
                            ? newTeacherName
                            : "未知教师");
                }
            }
        }
        saveToRedis();
    }

    /** 是否有草稿。 */
    public boolean isEmpty() {
        return drafts.isEmpty();
    }

    /** 草稿数量。 */
    public int size() {
        return drafts.size();
    }

    /** 获取缓存中涉及的所有班级名称（去重）。 */
    public Set<String> getClassNames() {
        synchronized (drafts) {
            return drafts.stream()
                    .map(DraftItem::getClassName)
                    .filter(name -> name != null && !name.isBlank())
                    .flatMap(name -> {
                        var list = new ArrayList<String>();
                        for (String part : name.split(",")) {
                            String trimmed = part.strip();
                            if (!trimmed.isEmpty()) {
                                list.add(trimmed);
                            }
                        }
                        return list.stream();
                    })
                    .collect(Collectors.toCollection(LinkedHashSet::new));
        }
    }

    /** 按班级名称统计草稿条目数。合班时每个班级各计 1。 */
    public Map<String, Long> countByClass() {
        synchronized (drafts) {
            return drafts.stream()
                    .filter(d -> d.getClassName() != null && !d.getClassName().isBlank())
                    .flatMap(d -> {
                        List<String> parts = new ArrayList<>();
                        for (String part : d.getClassName().split(",")) {
                            String trimmed = part.strip();
                            if (!trimmed.isEmpty()) {
                                parts.add(trimmed);
                            }
                        }
                        return parts.stream();
                    })
                    .collect(Collectors.groupingBy(
                            name -> name,
                            Collectors.counting()));
        }
    }

    // ────────────────────────── 名称解析 ──────────────────────────

    /** 解析草稿中每个班级名称对应的院系。多班级时逐个拆分后去重查询。 */
    private Map<String, String> loadClassColleges(List<TeachInfo> drafts) {
        Set<String> classNames = drafts.stream()
                .map(TeachInfo::getClassName)
                .filter(name -> name != null && !name.isBlank())
                .flatMap(name -> {
                    List<String> parts = new ArrayList<>();
                    for (String part : name.split(",")) {
                        String trimmed = part.strip();
                        if (!trimmed.isEmpty()) {
                            parts.add(trimmed);
                        }
                    }
                    return parts.stream();
                })
                .collect(Collectors.toSet());
        if (classNames.isEmpty()) {
            return Map.of();
        }
        return classNameMapper.selectList(
                        new LambdaQueryWrapper<ClassName>().in(ClassName::getClassName, classNames))
                .stream()
                .collect(Collectors.toMap(ClassName::getClassName, ClassName::getCollege, (a, b) -> a));
    }

    /** 将合班 className 解析为去重院系名，逗号分隔。 */
    private String resolveCollege(String className, Map<String, String> classColleges) {
        if (className == null || className.isBlank()) {
            return "";
        }
        Set<String> colleges = new LinkedHashSet<>();
        for (String part : className.split(",")) {
            String trimmed = part.strip();
            if (!trimmed.isEmpty()) {
                String c = classColleges.get(trimmed);
                if (c != null && !c.isEmpty()) {
                    colleges.add(c);
                }
            }
        }
        return String.join(",", colleges);
    }

    private Map<Long, String> loadCourseNames(List<TeachInfo> drafts) {
        Set<Long> courseIds = drafts.stream()
                .map(TeachInfo::getCourseId)
                .filter(id -> id != null)
                .collect(Collectors.toSet());
        if (courseIds.isEmpty()) {
            return Map.of();
        }
        return courseMapper.selectByIds(courseIds).stream()
                .collect(Collectors.toMap(Course::getId, Course::getCourseName, (a, b) -> a));
    }

    private Map<Long, String> loadTeacherNames(List<TeachInfo> drafts) {
        Set<Long> teacherIds = drafts.stream()
                .map(TeachInfo::getTeacherId)
                .filter(id -> id != null)
                .collect(Collectors.toSet());
        if (teacherIds.isEmpty()) {
            return Map.of();
        }

        List<Teacher> teachers = teacherMapper.selectByIds(teacherIds);
        Set<Long> userIds = teachers.stream()
                .map(Teacher::getUserId)
                .filter(id -> id != null)
                .collect(Collectors.toSet());
        Map<Long, String> userIdToName = userIds.isEmpty()
                ? Map.of()
                : userMapper.selectByIds(userIds).stream()
                        .collect(Collectors.toMap(User::getId, User::getName, (a, b) -> a));

        return teachers.stream()
                .collect(Collectors.toMap(
                        Teacher::getId,
                        t -> userIdToName.getOrDefault(t.getUserId(), "未知"),
                        (a, b) -> a));
    }
}
