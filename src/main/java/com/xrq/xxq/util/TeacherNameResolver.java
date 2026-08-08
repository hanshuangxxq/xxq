package com.xrq.xxq.util;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import com.xrq.xxq.module.user.entity.user.Teacher;
import com.xrq.xxq.module.user.mapper.TeacherMapper;
import com.xrq.xxq.module.user.mapper.UserMapper;

import lombok.RequiredArgsConstructor;

/**
 * 教师 id -> 姓名 解析器。
 * <p>
 * 教师姓名需经 {@code teacher.id -> teacher.userId -> user.name} 两跳查询，原散落在
 * TeachingEvaluation / Score / Progress / Scheduling / TeachInfo / Exam 等服务中重复实现
 * {@code teacherMapper.selectByIds -> Collectors.toMap -> userMapper.toNameMap -> 回填} 样板。
 * 本组件集中持有 TeacherMapper / UserMapper，统一对外提供解析，消除重复并收敛注入点。
 */
@Component
@RequiredArgsConstructor
public class TeacherNameResolver {

    private final TeacherMapper teacherMapper;
    private final UserMapper userMapper;

    /**
     * 按 teacher.id 批量解析姓名（内部先查 teacher.userId 再查 user.name）。
     * teacherId 无对应教师或 userId 为 null 时，该 id 不出现在结果中。
     */
    public Map<Long, String> namesByIds(Collection<Long> teacherIds) {
        List<Long> ids = teacherIds == null ? List.of()
                : teacherIds.stream().filter(Objects::nonNull).distinct().toList();
        if (ids.isEmpty()) {
            return Map.of();
        }
        return namesForTeachers(teacherMapper.selectByIds(ids));
    }

    /**
     * 按已加载的 Teacher 实体批量解析姓名。
     * <p>供已持有 Teacher 实体（如需 department 等其它字段而加载过）的调用方复用，避免重复查 teacher 表。
     */
    public Map<Long, String> namesForTeachers(Collection<Teacher> teachers) {
        if (teachers == null || teachers.isEmpty()) {
            return Map.of();
        }
        Map<Long, Long> idToUserId = teachers.stream()
                .filter(t -> t.getId() != null && t.getUserId() != null)
                .collect(Collectors.toMap(Teacher::getId, Teacher::getUserId, (a, b) -> a));
        if (idToUserId.isEmpty()) {
            return Map.of();
        }
        Map<Long, String> userName = userMapper.toNameMap(idToUserId.values());
        Map<Long, String> result = new HashMap<>();
        idToUserId.forEach((tid, uid) -> result.put(tid, userName.get(uid)));
        return result;
    }
}
