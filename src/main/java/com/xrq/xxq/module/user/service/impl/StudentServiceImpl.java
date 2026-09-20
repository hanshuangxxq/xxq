package com.xrq.xxq.module.user.service.impl;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.xrq.xxq.common.BusinessException;
import com.xrq.xxq.common.PageQuery;
import com.xrq.xxq.common.PageResult;
import com.xrq.xxq.module.clazz.entity.ClassName;
import com.xrq.xxq.module.clazz.mapper.ClassNameMapper;
import com.xrq.xxq.module.clazz.service.ClassNameService;
import com.xrq.xxq.module.user.dto.StudentDto;
import com.xrq.xxq.module.user.dto.UpdateStudentRequest;
import com.xrq.xxq.module.mojor.entity.Major;
import com.xrq.xxq.module.user.entity.User;
import com.xrq.xxq.module.user.entity.user.Grade;
import com.xrq.xxq.module.user.entity.user.Student;
import com.xrq.xxq.module.mojor.mapper.MajorMapper;
import com.xrq.xxq.module.user.mapper.GradeMapper;
import com.xrq.xxq.module.user.mapper.StudentMapper;
import com.xrq.xxq.module.user.mapper.UserMapper;
import com.xrq.xxq.module.user.service.StudentService;

import lombok.RequiredArgsConstructor;

/**
 * 学生服务实现，继承 MyBatis Plus ServiceImpl 提供通用 CRUD。
 */
@Service
@RequiredArgsConstructor
public class StudentServiceImpl extends ServiceImpl<StudentMapper, Student> implements StudentService {

    private final StudentMapper studentMapper;
    private final UserMapper userMapper;
    private final ClassNameMapper classNameMapper;
    private final ClassNameService classNameService;
    private final MajorMapper majorMapper;
    private final GradeMapper gradeMapper;

    @Override
    public PageResult<StudentDto> queryStudents(Long gradeId, List<Long> classIds, List<Long> majorIds, Boolean unassigned, String name, PageQuery pageQuery) {
        LambdaQueryWrapper<Student> wrapper = new LambdaQueryWrapper<>();

        if (name != null && !name.isBlank()) {
            List<Long> matchedUserIds = userMapper.selectList(
                    new LambdaQueryWrapper<User>().like(User::getName, name))
                    .stream()
                    .map(User::getId)
                    .toList();
            if (matchedUserIds.isEmpty()) {
                return new PageResult<>(List.of(), 0L, pageQuery.resolvedPage(), pageQuery.resolvedSize(), 0L);
            }
            wrapper.in(Student::getUserId, matchedUserIds);
        }

        if (gradeId != null) {
            wrapper.eq(Student::getGradeId, gradeId);
        }
        // 专业经班级推导（student 不再直存 major_id）：把专业筛转换成班级筛选，与班级参数取交集
        List<Long> scopedClassIds = classIds;
        if (majorIds != null && !majorIds.isEmpty()) {
            List<Long> classIdsOfMajors = classNameService.classIdsByMajorIds(majorIds);
            scopedClassIds = classIds == null || classIds.isEmpty()
                    ? classIdsOfMajors
                    : classIdsOfMajors.stream().filter(classIds::contains).toList();
            if (scopedClassIds.isEmpty()) {
                // 空集合会让 IN 生成非法 SQL，且语义上本就是无匹配
                return new PageResult<>(List.of(), 0L, pageQuery.resolvedPage(), pageQuery.resolvedSize(), 0L);
            }
        }
        if (scopedClassIds != null && !scopedClassIds.isEmpty()) {
            wrapper.in(Student::getClassId, scopedClassIds);
        }
        if (Boolean.TRUE.equals(unassigned)) {
            wrapper.isNull(Student::getClassId);
        }
        wrapper.orderByAsc(Student::getId);

        Page<Student> page = studentMapper.selectPage(pageQuery.toPage(), wrapper);
        List<Student> students = page.getRecords();
        if (students.isEmpty()) {
            return PageResult.of(page, List.of());
        }

        Set<Long> userIds = students.stream()
                .map(Student::getUserId)
                .collect(Collectors.toSet());
        Map<Long, User> userMap = userMapper.selectByIds(userIds).stream()
                .collect(Collectors.toMap(User::getId, u -> u));

        Set<Long> queriedClassIds = students.stream()
                .map(Student::getClassId)
                .collect(Collectors.toSet());
        Map<Long, String> classNameMap = classNameService.toNameMap(queriedClassIds);

        // 专业经班级推导（student 不再直存 major_id）
        Map<Long, Long> majorIdByClassId = classNameService.toMajorIdMap(queriedClassIds);
        Map<Long, String> majorNameMap = majorIdByClassId.isEmpty() ? Map.of()
                : majorMapper.selectByIds(majorIdByClassId.values().stream().distinct().toList()).stream()
                        .collect(Collectors.toMap(Major::getId, Major::getMajorName));

        Set<Long> queriedGradeIds = students.stream()
                .map(Student::getGradeId)
                .filter(java.util.Objects::nonNull)
                .collect(Collectors.toSet());
        Map<Long, String> gradeNameMap = queriedGradeIds.isEmpty() ? Map.of()
                : gradeMapper.selectByIds(queriedGradeIds).stream()
                        .collect(Collectors.toMap(Grade::getId, Grade::getName));

        List<StudentDto> records = students.stream()
                .map(s -> {
                    // 先取 majorId 再查名：classId 为 null 时不能拿 null 去 get（空 Map 会抛 NPE）
                    Long majorId = s.getClassId() == null ? null : majorIdByClassId.get(s.getClassId());
                    return toDto(s, userMap.get(s.getUserId()),
                            classNameMap.get(s.getClassId()),
                            majorId == null ? null : majorNameMap.get(majorId),
                            s.getGradeId() != null ? gradeNameMap.get(s.getGradeId()) : null);
                })
                .toList();
        return PageResult.of(page, records);
    }

    @Override
    public Boolean updateStudentInfo(Long studentId, UpdateStudentRequest request) {
        Student student = studentMapper.selectById(studentId);
        if (student == null) {
            throw new BusinessException(404, "学生不存在");
        }

        LambdaUpdateWrapper<Student> wrapper = new LambdaUpdateWrapper<>();
        wrapper.eq(Student::getId, studentId);

        if (request.getStudentNo() != null && !request.getStudentNo().isBlank()) {
            wrapper.set(Student::getStudentNo, request.getStudentNo());
        }
        if (request.getClassName() != null && !request.getClassName().isBlank()) {
            ClassName cn = classNameMapper.selectOne(
                    new LambdaQueryWrapper<ClassName>().eq(ClassName::getClassName, request.getClassName()));
            if (cn == null) {
                throw new BusinessException(404, "班级不存在: " + request.getClassName());
            }
            // 学生的专业与院系全部经班级推导，换到未挂专业的班级会让两项静默变 NULL
            if (cn.getMajorId() == null) {
                throw new BusinessException(400,
                        "班级未挂专业: " + request.getClassName() + "，请先在班级管理中为该班指定专业");
            }
            wrapper.set(Student::getClassId, cn.getId());
        }
        // 专业不在本表：改专业只能通过换班级（班级挂专业），故请求体不再接受 majorName
        if (request.getGradeName() != null && !request.getGradeName().isBlank()) {
            Grade grade = gradeMapper.selectOne(
                    new LambdaQueryWrapper<Grade>().eq(Grade::getName, request.getGradeName()));
            if (grade == null) {
                throw new BusinessException(404, "年级不存在: " + request.getGradeName());
            }
            wrapper.set(Student::getGradeId, grade.getId());
        }
        if (request.getEnrollmentYear() != null) {
            wrapper.set(Student::getEnrollmentYear, request.getEnrollmentYear());
        }

        return studentMapper.update(null, wrapper) > 0;
    }

    private StudentDto toDto(Student s, User u, String className, String majorName, String gradeName) {
        StudentDto dto = new StudentDto();
        dto.setStudentId(s.getId());
        dto.setStudentNo(s.getStudentNo());
        dto.setGradeId(s.getGradeId());
        dto.setGradeName(gradeName);
        dto.setMajorName(majorName);
        dto.setClassName(className);
        dto.setEnrollmentYear(s.getEnrollmentYear());

        if (u != null) {
            dto.setUserId(u.getId());
            dto.setName(u.getName());
            dto.setEmail(u.getEmail());
            dto.setPhone(u.getPhone());
            dto.setCreateTime(u.getCreateTime());
        }
        return dto;
    }
}
