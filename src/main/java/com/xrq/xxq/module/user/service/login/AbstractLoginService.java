package com.xrq.xxq.module.user.service.login;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.xrq.xxq.module.user.entity.AlipayUser;
import com.xrq.xxq.module.user.entity.QQUser;
import com.xrq.xxq.module.user.entity.User;
import com.xrq.xxq.module.user.entity.WXUser;
import com.xrq.xxq.module.user.entity.user.AcademicAdmin;
import com.xrq.xxq.module.user.entity.user.Department;
import com.xrq.xxq.module.user.entity.user.Student;
import com.xrq.xxq.module.user.entity.user.Teacher;
import com.xrq.xxq.common.BusinessException;
import com.xrq.xxq.util.EncryptUtils;
import com.xrq.xxq.module.user.dto.ChangePasswordRequest;
import com.xrq.xxq.module.user.dto.LoginRequest;
import com.xrq.xxq.module.user.dto.RegisterRequest;
import com.xrq.xxq.module.user.mapper.*;
import com.xrq.xxq.util.JwtUtils;
import com.xrq.xxq.util.auth.LoginSessionStore;
import com.xrq.xxq.util.auth.UserSession;
import org.springframework.transaction.annotation.Transactional;
import lombok.RequiredArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@RequiredArgsConstructor
public abstract class AbstractLoginService implements LoginService {

    protected final UserMapper userMapper;
    protected final TeacherMapper teacherMapper;
    protected final StudentMapper studentMapper;
    protected final AcademicAdminMapper academicAdminMapper;
    protected final DepartmentMapper departmentMapper;
    protected final WXUserMapper wxUserMapper;
    protected final QQUserMapper qqUserMapper;
    protected final AlipayUserMapper alipayUserMapper;
    protected final LoginSessionStore sessionStore;
    protected final JwtUtils jwtUtils;

    @Override
    public UserSession login(LoginRequest request) {
        throw new UnsupportedOperationException("当前登录渠道不支持该登录方式");
    }

    @Override
    public UserSession refreshAccessToken(String refreshToken) {
        UserSession session = sessionStore.get(refreshToken);
        if (session == null) {
            throw new BusinessException(401, "refreshToken 无效或已过期");
        }

        String newAccessToken = jwtUtils.generateAccessToken(
                session.getUserId(), session.getUserType(), session.getRole(), refreshToken);
        session.setAccessToken(newAccessToken);

        sessionStore.put(refreshToken, session);
        return session;
    }

    @Override
    public Boolean logout(String tokenId) {
        sessionStore.remove(tokenId);
        return true;
    }

    // ---- 公共工具方法 ----

    /** 按账号（用户名 或 工号/学号/职工号）在 user 表 + 子表中查找用户 */
    protected User lookupAcrossTables(String account) {

        //先通过正则表达式判断所使用的登录方式，有编号登录、账号登录等
        if(account.matches("\\d+.*")){ // 编号登录
            String lastChar = account.substring(account.length() - 1);
            switch (lastChar) { // 根据最后一位是什么字母来判断进哪个case
                case "s" -> { Student student = studentMapper
                        .selectOne(new LambdaQueryWrapper<Student>()
                                .eq(Student::getStudentNo, account));
                    if (student != null) {return userMapper.selectById(student.getUserId());}
                }
                case "t" -> { Teacher teacher = teacherMapper.selectOne(new LambdaQueryWrapper<Teacher>()
                        .eq(Teacher::getTeacherNo, account));
                    if (teacher != null) {return userMapper.selectById(teacher.getUserId());}
                }
                case "d" -> { Department department = departmentMapper.selectOne(new LambdaQueryWrapper<Department>()
                        .eq(Department::getDepartmentNo, account));
                    if (department != null) {return userMapper.selectById(department.getUserId());}
                }
                case "a" -> { AcademicAdmin academicAdmin = academicAdminMapper.selectOne(new LambdaQueryWrapper<AcademicAdmin>()
                        .eq(AcademicAdmin::getDepartmentNo, account));
                    if (academicAdmin != null) {return userMapper.selectById(academicAdmin.getUserId());}
                }
                default -> throw new BusinessException(404, "用户不存在");
            }
        }else {
            return userMapper.selectOne(new LambdaQueryWrapper<User>()
                    .eq(User::getName, account));
        }
        return null;
    }

    /** 从第三方平台实体解析出 UserSession（直接通过 userId 查 user 表） */
    protected UserSession buildOAuthSession(Object platformUser) {
        if (platformUser == null) return null;

        Long userId = switch (platformUser) {
            case WXUser w     -> w.getUserId();
            case QQUser q     -> q.getUserId();
            case AlipayUser a -> a.getUserId();
            default -> throw new BusinessException(500, "未知的第三方平台实体");
        };

        User user = userMapper.selectById(userId);
        return user != null ? buildSession(user, user.getName()) : null;
    }

    protected UserSession buildSession(User user, String account) {
        String tokenId = UUID.randomUUID().toString().replace("-", "");
        String accessToken = jwtUtils.generateAccessToken(
                user.getId(), user.getUserType(), user.getRole(), tokenId);

        UserSession session = new UserSession();
        session.setUserId(user.getId());
        session.setUserType(user.getUserType());
        session.setName(user.getName());
        session.setAccount(account);
        session.setAvatar(user.getAvatar());
        session.setRole(user.getRole());
        session.setTokenId(tokenId);
        session.setAccessToken(accessToken);
        session.setRefreshToken(tokenId);
        session.setLoginTime(LocalDateTime.now());
        session.setLastLoginTime(user.getLastLoginTime());

        user.setLastLoginTime(LocalDateTime.now());
        userMapper.updateById(user);

        sessionStore.login(user.getId(), tokenId, session);
        return session;
    }

    protected boolean matchPassword(String rawPassword, String storedPassword) {
        if (storedPassword == null) return false;
        return EncryptUtils.verifyPbkdf2(rawPassword, storedPassword);
    }

    @Override
    public Boolean changePassword(ChangePasswordRequest request) {
        User user = lookupAcrossTables(request.getAccount());
        if (user == null) {
            throw new BusinessException(404, "用户不存在");
        }
        if (!matchPassword(request.getOldPassword(), user.getPassword())) {
            throw new BusinessException(401, "原密码错误");
        }
        user.setPassword(EncryptUtils.hashWithPbkdf2(request.getNewPassword()));
        userMapper.updateById(user);
        return true;
    }

    @Override
    @Transactional
    public Boolean register(RegisterRequest request) {
        String account = request.getAccount();
        if (lookupAcrossTables(account) != null) {
            throw new BusinessException(409, "账号已存在");
        }

        User user = new User();
        user.setName(account);
        user.setPassword(EncryptUtils.hashWithPbkdf2(request.getPassword()));
        user.setUserType(request.getUserType());
        user.setCreateTime(LocalDateTime.now());
        user.setStatus(1);
        userMapper.insert(user);

        String identifier = request.getIdentifier();
        switch (request.getUserType()) {
            case "teacher" -> {
                if (identifier != null && !identifier.isBlank()) {
                    Long cnt = teacherMapper.selectCount(new LambdaQueryWrapper<Teacher>()
                            .eq(Teacher::getTeacherNo, identifier));
                    if (cnt != null && cnt > 0) {
                        throw new BusinessException(409, "工号已存在");
                    }
                }
                Teacher teacher = new Teacher();
                teacher.setUserId(user.getId());
                teacher.setTeacherNo(identifier);
                teacherMapper.insert(teacher);
            }
            case "student" -> {
                if (identifier != null && !identifier.isBlank()) {
                    Long cnt = studentMapper.selectCount(new LambdaQueryWrapper<Student>()
                            .eq(Student::getStudentNo, identifier));
                    if (cnt != null && cnt > 0) {
                        throw new BusinessException(409, "学号已存在");
                    }
                }
                Student student = new Student();
                student.setUserId(user.getId());
                student.setStudentNo(identifier);
                studentMapper.insert(student);
            }
            case "academic_admin" -> {
                if (identifier != null && !identifier.isBlank()) {
                    Long cnt = academicAdminMapper.selectCount(new LambdaQueryWrapper<AcademicAdmin>()
                            .eq(AcademicAdmin::getDepartmentNo, identifier));
                    if (cnt != null && cnt > 0) {
                        throw new BusinessException(409, "部门编号已存在");
                    }
                }
                AcademicAdmin admin = new AcademicAdmin();
                admin.setUserId(user.getId());
                admin.setDepartmentNo(identifier);
                academicAdminMapper.insert(admin);
            }
            case "department" -> {
                if (identifier != null && !identifier.isBlank()) {
                    Long cnt = departmentMapper.selectCount(new LambdaQueryWrapper<Department>()
                            .eq(Department::getDepartmentNo, identifier));
                    if (cnt != null && cnt > 0) {
                        throw new BusinessException(409, "部门编号已存在");
                    }
                }
                Department dept = new Department();
                dept.setUserId(user.getId());
                dept.setDepartmentNo(identifier);
                departmentMapper.insert(dept);
            }
            default -> throw new IllegalArgumentException("不支持的用户类型: " + request.getUserType());
        }
        return true;
    }
}
