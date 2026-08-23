package com.xrq.xxq.module.user.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.xrq.xxq.module.user.dto.UpdateProfileRequest;
import com.xrq.xxq.module.user.dto.UserProfileResponse;
import com.xrq.xxq.module.mojor.entity.Major;
import com.xrq.xxq.module.user.entity.User;
import com.xrq.xxq.module.user.entity.user.AcademicAdmin;
import com.xrq.xxq.module.user.entity.user.Department;
import com.xrq.xxq.module.clazz.mapper.ClassNameMapper;
import com.xrq.xxq.module.user.entity.user.Student;
import com.xrq.xxq.module.user.entity.user.Teacher;
import com.xrq.xxq.module.user.mapper.AcademicAdminMapper;
import com.xrq.xxq.module.user.mapper.DepartmentMapper;
import com.xrq.xxq.module.mojor.mapper.MajorMapper;
import com.xrq.xxq.module.user.mapper.GradeMapper;
import com.xrq.xxq.module.user.mapper.StudentMapper;
import com.xrq.xxq.module.user.mapper.TeacherMapper;
import com.xrq.xxq.module.user.mapper.UserMapper;
import com.xrq.xxq.module.college.mapper.CollegeMapper;
import com.xrq.xxq.module.college.entity.College;
import com.xrq.xxq.common.BusinessException;
import com.xrq.xxq.module.user.entity.user.Grade;
import com.xrq.xxq.module.user.service.UserService;
import com.xrq.xxq.util.auth.LoginSessionStore;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements UserService {

    private final UserMapper userMapper;
    private final StudentMapper studentMapper;
    private final TeacherMapper teacherMapper;
    private final AcademicAdminMapper academicAdminMapper;
    private final DepartmentMapper departmentMapper;
    private final ClassNameMapper classNameMapper;
    private final MajorMapper majorMapper;
    private final GradeMapper gradeMapper;
    private final LoginSessionStore sessionStore;
    private final CollegeMapper collegeMapper;

    @Override
    public UserProfileResponse getProfile(Long userId, String tokenId) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(404, "用户不存在");
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
    public Boolean updateProfile(Long userId, UpdateProfileRequest request) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(404, "用户不存在");
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
                    Grade grade = student.getGradeId() != null ? gradeMapper.selectById(student.getGradeId()) : null;
                    profile.setGrade(grade != null ? grade.getName() : null);
                    Major major = majorMapper.selectById(student.getMajorId());
                    profile.setMajor(major != null ? major.getMajorName() : null);
                    profile.setClassName(classNameMapper.selectById(student.getClassId()));
                    profile.setEnrollmentYear(student.getEnrollmentYear());
                }
            }
            case "teacher" -> {
                Teacher teacher = teacherMapper.findByUserId(userId);
                if (teacher != null) {
                    profile.setIdentifier(teacher.getTeacherNo());
                    profile.setTitle(teacher.getTitle());
                    profile.setDepartment(collegeNameOf(teacher.getCollegeId()));
                }
            }
            case "academic_admin" -> {
                AcademicAdmin admin = academicAdminMapper.selectOne(new LambdaQueryWrapper<AcademicAdmin>()
                        .eq(AcademicAdmin::getUserId, userId));
                if (admin != null) {
                    profile.setIdentifier(admin.getDepartmentNo());
                    profile.setPosition("教务管理员");
                }
            }
            case "department" -> {
                Department dept = departmentMapper.findByUserId(userId);
                if (dept != null) {
                    profile.setDepartment(collegeNameOf(dept.getCollegeId()));
                    profile.setPosition("院系管理员");
                }
            }
        }
    }

    /** 解析 college_id -> 院系名称（无匹配返回 null）。 */
    private String collegeNameOf(Long collegeId) {
        if (collegeId == null) {
            return null;
        }
        College college = collegeMapper.selectById(collegeId);
        return college != null ? college.getCollegeName() : null;
    }
}
