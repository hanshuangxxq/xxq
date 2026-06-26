package com.xrq.xxq.module.user.service.login;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.xrq.xxq.module.user.entity.AlipayUser;
import com.xrq.xxq.module.user.entity.QQUser;
import com.xrq.xxq.module.user.entity.User;
import com.xrq.xxq.module.user.entity.WXUser;
import com.xrq.xxq.module.user.entity.user.Dean;
import com.xrq.xxq.module.user.entity.user.Student;
import com.xrq.xxq.module.user.entity.user.Teacher;
import com.xrq.xxq.util.EncryptUtils;
import com.xrq.xxq.module.user.dto.LoginRequest;
import com.xrq.xxq.module.user.dto.RegisterRequest;
import com.xrq.xxq.module.user.dto.UserSession;
import com.xrq.xxq.module.user.mapper.*;
import com.xrq.xxq.module.user.session.LoginSessionStore;
import com.xrq.xxq.util.JwtUtils;
import lombok.RequiredArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@RequiredArgsConstructor
public abstract class AbstractLoginService implements LoginService {

    protected final UserMapper userMapper;
    protected final TeacherMapper teacherMapper;
    protected final StudentMapper studentMapper;
    protected final DeanMapper deanMapper;
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
            throw new IllegalArgumentException("refreshToken 无效或已过期");
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
        // 1. 先在 user 表按 name 查
        User user = userMapper.selectOne(new LambdaQueryWrapper<User>()
                .eq(User::getName, account));
        if (user != null) return user;

        // 2. 按工号查教师
        Teacher teacher = teacherMapper.selectOne(new LambdaQueryWrapper<Teacher>()
                .eq(Teacher::getTeacherNo, account));
        if (teacher != null) return userMapper.selectById(teacher.getUserId());

        // 3. 按学号查学生
        Student student = studentMapper.selectOne(new LambdaQueryWrapper<Student>()
                .eq(Student::getStudentNo, account));
        if (student != null) return userMapper.selectById(student.getUserId());

        // 4. 按职工号查教务主任
        Dean dean = deanMapper.selectOne(new LambdaQueryWrapper<Dean>()
                .eq(Dean::getStaffNo, account));
        if (dean != null) return userMapper.selectById(dean.getUserId());

        return null;
    }

    /** 从第三方平台实体解析出 UserSession（直接通过 userId 查 user 表） */
    protected UserSession buildOAuthSession(Object platformUser) {
        if (platformUser == null) return null;

        Long userId = switch (platformUser) {
            case WXUser w     -> w.getUserId();
            case QQUser q     -> q.getUserId();
            case AlipayUser a -> a.getUserId();
            default -> throw new IllegalArgumentException("未知的第三方平台实体");
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

        sessionStore.put(tokenId, session);
        return session;
    }

    protected boolean matchPassword(String rawPassword, String storedPassword) {
        if (storedPassword == null) return false;
        return EncryptUtils.verifyPbkdf2(rawPassword, storedPassword);
    }

    @Override
    public Boolean register(RegisterRequest request) {
        String account = request.getAccount();
        if (lookupAcrossTables(account) != null) {
            throw new IllegalArgumentException("账号已存在");
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
                Teacher teacher = new Teacher();
                teacher.setUserId(user.getId());
                teacher.setTeacherNo(identifier);
                teacherMapper.insert(teacher);
            }
            case "student" -> {
                Student student = new Student();
                student.setUserId(user.getId());
                student.setStudentNo(identifier);
                studentMapper.insert(student);
            }
            case "dean" -> {
                Dean dean = new Dean();
                dean.setUserId(user.getId());
                dean.setStaffNo(identifier);
                deanMapper.insert(dean);
            }
            default -> throw new IllegalArgumentException("不支持的用户类型: " + request.getUserType());
        }
        return true;
    }
}
