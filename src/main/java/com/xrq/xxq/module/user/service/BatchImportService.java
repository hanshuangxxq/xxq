package com.xrq.xxq.module.user.service;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.xrq.xxq.common.BusinessException;
import com.xrq.xxq.module.user.dto.BatchImportResponse;
import com.xrq.xxq.module.user.dto.BatchImportResponse.ImportResultDetail;
import com.xrq.xxq.module.user.dto.UserImportItem;
import com.xrq.xxq.module.user.entity.GenderEnum;
import com.xrq.xxq.module.mojor.entity.Major;
import com.xrq.xxq.module.user.entity.User;
import com.xrq.xxq.module.user.entity.user.Grade;
import com.xrq.xxq.module.user.entity.user.Student;
import com.xrq.xxq.module.user.entity.user.Teacher;
import com.xrq.xxq.module.mojor.mapper.MajorMapper;
import com.xrq.xxq.module.college.mapper.CollegeMapper;
import com.xrq.xxq.module.college.entity.College;
import com.xrq.xxq.module.user.mapper.GradeMapper;
import com.xrq.xxq.module.user.mapper.StudentMapper;
import com.xrq.xxq.module.user.mapper.TeacherMapper;
import com.xrq.xxq.module.user.mapper.UserMapper;
import com.xrq.xxq.util.EncryptUtils;
import com.xrq.xxq.util.ParamValidator;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class BatchImportService {

    private final UserMapper userMapper;
    private final StudentMapper studentMapper;
    private final TeacherMapper teacherMapper;
    private final MajorMapper majorMapper;
    private final GradeMapper gradeMapper;
    private final CollegeMapper collegeMapper;
    private final PlatformTransactionManager transactionManager;

    public BatchImportResponse batchImport(List<UserImportItem> items) {
        BatchImportResponse response = new BatchImportResponse();
        response.setTotal(items.size());
        // 每行独立事务：单行失败仅回滚该行（user+子类型），不影响其他行，避免孤立 user
        TransactionTemplate txTemplate = new TransactionTemplate(transactionManager);
        // 批量预加载（替代逐行查库）：专业/年级/院系字典全量 + 已占用用户名/学号/工号一次 IN 批查
        ImportContext ctx = preload(items);

        int index = 0;
        for (UserImportItem item : items) {
            index++;
            try {
                txTemplate.executeWithoutResult(status -> importOne(item, ctx));
                // 本行提交成功后登记占用，保证后续行的查重语义与逐行查库一致
                ctx.markImported(item);
                ImportResultDetail detail = new ImportResultDetail();
                detail.setIndex(index);
                detail.setUsername(item.getUsername());
                detail.setSuccess(true);
                detail.setMessage("导入成功");
                response.getDetails().add(detail);
                response.setSuccessCount(response.getSuccessCount() + 1);
            } catch (Exception e) {
                ImportResultDetail detail = new ImportResultDetail();
                detail.setIndex(index);
                detail.setUsername(item.getUsername());
                detail.setSuccess(false);
                detail.setMessage(e.getMessage());
                response.getDetails().add(detail);
                response.setFailCount(response.getFailCount() + 1);
            }
        }
        return response;
    }

    /** 预加载上下文：字典映射 + 已占用标识集合（导入过程中随成功行递增）。 */
    private static class ImportContext {
        private Map<String, Long> majorIdByName = Map.of();
        private Map<String, Long> gradeIdByName = Map.of();
        private Map<String, Long> collegeIdByName = Map.of();
        private final Set<String> usedUsernames = new HashSet<>();
        private final Set<String> usedStudentNos = new HashSet<>();
        private final Set<String> usedTeacherNos = new HashSet<>();

        private void markImported(UserImportItem item) {
            if (item.getUsername() != null) {
                usedUsernames.add(item.getUsername().strip());
            }
            if (item.getIdentifier() != null && !item.getIdentifier().isBlank()) {
                if ("student".equals(item.getUserType())) {
                    usedStudentNos.add(item.getIdentifier());
                } else if ("teacher".equals(item.getUserType())) {
                    usedTeacherNos.add(item.getIdentifier());
                }
            }
        }
    }

    private ImportContext preload(List<UserImportItem> items) {
        ImportContext ctx = new ImportContext();
        // 字典表体量小（专业/年级/院系数十行），全量加载为名称 -> id 映射
        ctx.majorIdByName = majorMapper.selectList(null).stream()
                .collect(Collectors.toMap(Major::getMajorName, Major::getId, (a, b) -> a));
        ctx.gradeIdByName = gradeMapper.selectList(null).stream()
                .collect(Collectors.toMap(Grade::getName, Grade::getId, (a, b) -> a));
        ctx.collegeIdByName = collegeMapper.selectList(null).stream()
                .collect(Collectors.toMap(College::getCollegeName, College::getId, (a, b) -> a));
        List<String> usernames = items.stream().map(UserImportItem::getUsername)
                .filter(Objects::nonNull).map(String::strip).filter(s -> !s.isEmpty())
                .distinct().toList();
        if (!usernames.isEmpty()) {
            userMapper.selectList(new LambdaQueryWrapper<User>()
                            .select(User::getId, User::getName)
                            .in(User::getName, usernames))
                    .forEach(u -> ctx.usedUsernames.add(u.getName()));
        }
        List<String> identifiers = items.stream().map(UserImportItem::getIdentifier)
                .filter(s -> s != null && !s.isBlank()).distinct().toList();
        if (!identifiers.isEmpty()) {
            studentMapper.selectList(new LambdaQueryWrapper<Student>()
                            .select(Student::getId, Student::getStudentNo)
                            .in(Student::getStudentNo, identifiers))
                    .forEach(s -> ctx.usedStudentNos.add(s.getStudentNo()));
            teacherMapper.selectList(new LambdaQueryWrapper<Teacher>()
                            .select(Teacher::getId, Teacher::getTeacherNo)
                            .in(Teacher::getTeacherNo, identifiers))
                    .forEach(t -> ctx.usedTeacherNos.add(t.getTeacherNo()));
        }
        return ctx;
    }

    private void importOne(UserImportItem item, ImportContext ctx) {
        String userType = item.getUserType();
        if (userType == null || (!"student".equals(userType) && !"teacher".equals(userType))) {
            throw new IllegalArgumentException("用户类型只允许 student 或 teacher，收到: " + userType);
        }

        ParamValidator.requireNonBlank(item.getUsername(), "用户名");
        ParamValidator.requireNonBlank(item.getPassword(), "密码");

        // 用户名唯一性预检（内存集合，含本次导入已提交行）
        if (ctx.usedUsernames.contains(item.getUsername().strip())) {
            throw new BusinessException(409, "用户名已存在：" + item.getUsername().strip());
        }

        User user = new User();
        user.setName(item.getUsername().strip());
        user.setPassword(EncryptUtils.hashWithPbkdf2(item.getPassword()));
        user.setGender(parseGender(item.getGender()));
        user.setRole(userType);
        user.setUserType(userType);
        user.setCreateTime(LocalDateTime.now());
        user.setStatus(1);
        userMapper.insert(user);

        if ("student".equals(userType)) {
            if (item.getIdentifier() != null && !item.getIdentifier().isBlank()) {
                if (ctx.usedStudentNos.contains(item.getIdentifier())) {
                    throw new BusinessException(409, "学号已存在：" + item.getIdentifier());
                }
            }
            Student student = new Student();
            student.setUserId(user.getId());
            student.setStudentNo(item.getIdentifier());
            student.setGradeId(resolveGradeId(item.getClassName(), ctx));
            student.setMajorId(resolveMajorId(item.getDepartment(), ctx));
            studentMapper.insert(student);
        } else {
            if (item.getIdentifier() != null && !item.getIdentifier().isBlank()) {
                if (ctx.usedTeacherNos.contains(item.getIdentifier())) {
                    throw new BusinessException(409, "工号已存在：" + item.getIdentifier());
                }
            }
            Teacher teacher = new Teacher();
            teacher.setUserId(user.getId());
            teacher.setTeacherNo(item.getIdentifier());
            teacher.setCollegeId(resolveCollegeId(item.getDepartment(), ctx));
            teacherMapper.insert(teacher);
        }
    }

    private GenderEnum parseGender(String genderStr) {
        if (genderStr == null || genderStr.isBlank()) {
            return GenderEnum.MALE;
        }
        for (GenderEnum g : GenderEnum.values()) {
            if (g.getDesc().equals(genderStr.strip())) {
                return g;
            }
        }
        return GenderEnum.MALE;
    }

    private Long resolveMajorId(String majorName, ImportContext ctx) {
        if (majorName == null || majorName.isBlank()) {
            return null;
        }
        Long id = ctx.majorIdByName.get(majorName.strip());
        if (id == null) {
            throw new BusinessException(400, "专业不存在：" + majorName.strip() + "，请先在基础数据中创建");
        }
        return id;
    }

    private Long resolveGradeId(String gradeName, ImportContext ctx) {
        if (gradeName == null || gradeName.isBlank()) {
            return null;
        }
        Long id = ctx.gradeIdByName.get(gradeName.strip());
        if (id == null) {
            throw new BusinessException(400, "年级不存在：" + gradeName.strip() + "，请先在基础数据中创建");
        }
        return id;
    }

    /** 按院系名称解析 college_id（教师导入：department 字段为院系名；找不到则报错）。 */
    private Long resolveCollegeId(String collegeName, ImportContext ctx) {
        if (collegeName == null || collegeName.isBlank()) {
            return null;
        }
        Long id = ctx.collegeIdByName.get(collegeName.strip());
        if (id == null) {
            throw new BusinessException(400, "院系不存在：" + collegeName.strip() + "，请先在基础数据中创建");
        }
        return id;
    }
}
