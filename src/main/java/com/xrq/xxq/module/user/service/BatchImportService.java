package com.xrq.xxq.module.user.service;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.xrq.xxq.module.user.dto.BatchImportResponse;
import com.xrq.xxq.module.user.dto.BatchImportResponse.ImportResultDetail;
import com.xrq.xxq.module.user.dto.UserImportItem;
import com.xrq.xxq.module.user.entity.GenderEnum;
import com.xrq.xxq.module.mojor.entity.Major;
import com.xrq.xxq.module.user.entity.User;
import com.xrq.xxq.module.user.entity.user.Student;
import com.xrq.xxq.module.user.entity.user.Teacher;
import com.xrq.xxq.module.mojor.mapper.MajorMapper;
import com.xrq.xxq.module.user.mapper.StudentMapper;
import com.xrq.xxq.module.user.mapper.TeacherMapper;
import com.xrq.xxq.module.user.mapper.UserMapper;
import com.xrq.xxq.util.EncryptUtils;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class BatchImportService {

    private final UserMapper userMapper;
    private final StudentMapper studentMapper;
    private final TeacherMapper teacherMapper;
    private final MajorMapper majorMapper;

    @Transactional
    public BatchImportResponse batchImport(List<UserImportItem> items) {
        BatchImportResponse response = new BatchImportResponse();
        response.setTotal(items.size());

        int index = 0;
        for (UserImportItem item : items) {
            index++;
            try {
                importOne(item);
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

        if (item.getUsername() == null || item.getUsername().isBlank()) {
            throw new IllegalArgumentException("用户名不能为空");
        }
        if (item.getPassword() == null || item.getPassword().isBlank()) {
            throw new IllegalArgumentException("密码不能为空");
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
            Student student = new Student();
            student.setUserId(user.getId());
            student.setStudentNo(item.getIdentifier());
            student.setGrade(item.getClassName());
            student.setMajorId(resolveMajorId(item.getDepartment()));
            studentMapper.insert(student);
        } else {
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
        if (major != null) {
            return major.getId();
        }
        Major newMajor = new Major();
        newMajor.setMajorName(majorName.strip());
        majorMapper.insert(newMajor);
        return newMajor.getId();
    }
}
