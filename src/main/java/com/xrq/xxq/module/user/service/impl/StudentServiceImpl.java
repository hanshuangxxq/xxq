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
                return new PageResult<>(List.of(), 0, pageQuery.resolvedPage(), pageQuery.resolvedSize(), 0);
            }
            wrapper.in(Student::getUserId, matchedUserIds);
        }

        if (gradeId != null) {
            wrapper.eq(Student::getGradeId, gradeId);
        }
        if (classIds != null && !classIds.isEmpty()) {
            wrapper.in(Student::getClassId, classIds);
        }
        if (majorIds != null && !majorIds.isEmpty()) {
            wrapper.in(Student::getMajorId, majorIds);
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

        Set<Long> queriedMajorIds = students.stream()
                .map(Student::getMajorId)
                .collect(Collectors.toSet());
        Map<Long, String> majorNameMap = majorMapper.selectByIds(queriedMajorIds).stream()
                .collect(Collectors.toMap(Major::getId, Major::getMajorName));

        Set<Long> queriedGradeIds = students.stream()
                .map(Student::getGradeId)
                .filter(java.util.Objects::nonNull)
                .collect(Collectors.toSet());
        Map<Long, String> gradeNameMap = queriedGradeIds.isEmpty() ? Map.of()
                : gradeMapper.selectByIds(queriedGradeIds).stream()
                        .collect(Collectors.toMap(Grade::getId, Grade::getName));

        List<StudentDto> records = students.stream()
                .map(s -> toDto(s, userMap.get(s.getUserId()),
                        classNameMap.get(s.getClassId()),
                        majorNameMap.get(s.getMajorId()),
                        s.getGradeId() != null ? gradeNameMap.get(s.getGradeId()) : null))
                .toList();
        return PageResult.of(page, records);
    }

    @Override
    public boolean updateStudentInfo(Long studentId, UpdateStudentRequest request) {
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
            wrapper.set(Student::getClassId, cn.getId());
        }
        if (request.getMajorName() != null && !request.getMajorName().isBlank()) {
            Major major = majorMapper.selectOne(
                    new LambdaQueryWrapper<Major>().eq(Major::getMajorName, request.getMajorName()));
            if (major == null) {
                throw new BusinessException(404, "专业不存在: " + request.getMajorName());
            }
            wrapper.set(Student::getMajorId, major.getId());
        }
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
