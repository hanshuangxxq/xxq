package com.xrq.xxq.module.user.service;

import java.time.LocalDateTime;
import java.util.List;

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
    private final PlatformTransactionManager transactionManager;

    public BatchImportResponse batchImport(List<UserImportItem> items) {
        BatchImportResponse response = new BatchImportResponse();
        response.setTotal(items.size());
        // 每行独立事务：单行失败仅回滚该行（user+子类型），不影响其他行，避免孤立 user
        TransactionTemplate txTemplate = new TransactionTemplate(transactionManager);

        int index = 0;
        for (UserImportItem item : items) {
            index++;
            try {
                txTemplate.executeWithoutResult(status -> importOne(item));
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

    private void importOne(UserImportItem item) {
        String userType = item.getUserType();
        if (userType == null || (!"student".equals(userType) && !"teacher".equals(userType))) {
            throw new IllegalArgumentException("用户类型只允许 student 或 teacher，收到: " + userType);
        }

        ParamValidator.requireNonBlank(item.getUsername(), "用户名");
        ParamValidator.requireNonBlank(item.getPassword(), "密码");

        // 用户名唯一性预检
        Long userCnt = userMapper.selectCount(new LambdaQueryWrapper<User>()
                .eq(User::getName, item.getUsername().strip()));
        if (userCnt != null && userCnt > 0) {
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
                Long cnt = studentMapper.selectCount(new LambdaQueryWrapper<Student>()
                        .eq(Student::getStudentNo, item.getIdentifier()));
                if (cnt != null && cnt > 0) {
                    throw new BusinessException(409, "学号已存在：" + item.getIdentifier());
                }
            }
            Student student = new Student();
            student.setUserId(user.getId());
            student.setStudentNo(item.getIdentifier());
            student.setGradeId(resolveGradeId(item.getClassName()));
            student.setMajorId(resolveMajorId(item.getDepartment()));
            studentMapper.insert(student);
        } else {
            if (item.getIdentifier() != null && !item.getIdentifier().isBlank()) {
                Long cnt = teacherMapper.selectCount(new LambdaQueryWrapper<Teacher>()
                        .eq(Teacher::getTeacherNo, item.getIdentifier()));
                if (cnt != null && cnt > 0) {
                    throw new BusinessException(409, "工号已存在：" + item.getIdentifier());
                }
            }
            Teacher teacher = new Teacher();
            teacher.setUserId(user.getId());
            teacher.setTeacherNo(item.getIdentifier());
            teacher.setDepartment(item.getDepartment());
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

    private Long resolveMajorId(String majorName) {
        if (majorName == null || majorName.isBlank()) {
            return null;
        }
        Major major = majorMapper.selectOne(
                new LambdaQueryWrapper<Major>().eq(Major::getMajorName, majorName.strip()));
        if (major == null) {
            throw new BusinessException(400, "专业不存在：" + majorName.strip() + "，请先在基础数据中创建");
        }
        return major.getId();
    }

    private Long resolveGradeId(String gradeName) {
        if (gradeName == null || gradeName.isBlank()) {
            return null;
        }
        Grade grade = gradeMapper.selectOne(
                new LambdaQueryWrapper<Grade>().eq(Grade::getName, gradeName.strip()));
        if (grade == null) {
            throw new BusinessException(400, "年级不存在：" + gradeName.strip() + "，请先在基础数据中创建");
        }
        return grade.getId();
    }
}
