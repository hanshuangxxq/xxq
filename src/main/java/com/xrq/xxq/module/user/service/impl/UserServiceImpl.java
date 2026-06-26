package com.xrq.xxq.module.user.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.xrq.xxq.module.user.dto.UpdateProfileRequest;
import com.xrq.xxq.module.user.dto.UserProfileResponse;
import com.xrq.xxq.module.user.entity.User;
import com.xrq.xxq.module.user.entity.user.Dean;
import com.xrq.xxq.module.user.entity.user.Student;
import com.xrq.xxq.module.user.entity.user.Teacher;
import com.xrq.xxq.module.user.mapper.DeanMapper;
import com.xrq.xxq.module.user.mapper.StudentMapper;
import com.xrq.xxq.module.user.mapper.TeacherMapper;
import com.xrq.xxq.module.user.mapper.UserMapper;
import com.xrq.xxq.module.user.service.UserService;
import com.xrq.xxq.module.user.session.LoginSessionStore;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements UserService {

    private final UserMapper userMapper;
    private final StudentMapper studentMapper;
    private final TeacherMapper teacherMapper;
    private final DeanMapper deanMapper;
    private final LoginSessionStore sessionStore;

    @Override
    public UserProfileResponse getProfile(Long userId, String tokenId) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new IllegalArgumentException("用户不存在");
        }

        UserProfileResponse profile = new UserProfileResponse();
        profile.setUserId(user.getId());
        profile.setName(user.getName());
        profile.setEmail(user.getEmail());
        profile.setPhone(user.getPhone());
        profile.setGender(user.getGender());
        profile.setAvatar(user.getAvatar());
        profile.setDescription(user.getDescription());
        profile.setRole(user.getRole());
        profile.setUserType(user.getUserType());
        profile.setCreateTime(user.getCreateTime());
        profile.setStatus(user.getStatus());

        var session = sessionStore.get(tokenId);
        profile.setLastLoginTime(session != null ? session.getLastLoginTime() : user.getLastLoginTime());

        fillSubtypeInfo(user.getUserType(), userId, profile);
        return profile;
    }

    @Override
    public boolean updateProfile(Long userId, UpdateProfileRequest request) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new IllegalArgumentException("用户不存在");
        }

        if (request.getEmail() != null) user.setEmail(request.getEmail());
        if (request.getPhone() != null) user.setPhone(request.getPhone());
        if (request.getGender() != null) user.setGender(request.getGender());
        if (request.getAvatar() != null) user.setAvatar(request.getAvatar());
        if (request.getDescription() != null) user.setDescription(request.getDescription());

        return userMapper.updateById(user) > 0;
    }

    private void fillSubtypeInfo(String userType, Long userId, UserProfileResponse profile) {
        switch (userType) {
            case "student" -> {
                Student student = studentMapper.selectOne(new LambdaQueryWrapper<Student>()
                        .eq(Student::getUserId, userId));
                if (student != null) {
                    profile.setIdentifier(student.getStudentNo());
                    profile.setGrade(student.getGrade());
                    profile.setMajor(student.getMajor());
                    profile.setClassName(student.getClassName());
                    profile.setEnrollmentYear(student.getEnrollmentYear());
                }
            }
            case "teacher" -> {
                Teacher teacher = teacherMapper.selectOne(new LambdaQueryWrapper<Teacher>()
                        .eq(Teacher::getUserId, userId));
                if (teacher != null) {
                    profile.setIdentifier(teacher.getTeacherNo());
                    profile.setTitle(teacher.getTitle());
                    profile.setDepartment(teacher.getDepartment());
                }
            }
            case "dean" -> {
                Dean dean = deanMapper.selectOne(new LambdaQueryWrapper<Dean>()
                        .eq(Dean::getUserId, userId));
                if (dean != null) {
                    profile.setIdentifier(dean.getStaffNo());
                    profile.setDepartment(dean.getDepartment());
                    profile.setPosition(dean.getPosition());
                }
            }
        }
    }
}
