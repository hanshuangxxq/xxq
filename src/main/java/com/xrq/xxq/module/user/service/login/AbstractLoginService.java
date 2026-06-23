package com.xrq.xxq.module.user.service.login;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.xrq.xxq.module.user.entity.AlipayUser;
import com.xrq.xxq.module.user.entity.QQUser;
import com.xrq.xxq.module.user.entity.User;
import com.xrq.xxq.module.user.entity.WXUser;
import com.xrq.xxq.module.user.entity.user.Dean;
import com.xrq.xxq.module.user.entity.user.Student;
import com.xrq.xxq.module.user.entity.user.Teacher;
import com.xrq.xxq.module.user.dto.LoginRequest;
import com.xrq.xxq.module.user.dto.UserSession;
import com.xrq.xxq.module.user.mapper.*;
import lombok.RequiredArgsConstructor;
import org.springframework.util.DigestUtils;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 登录抽象基类 —— 封装公共逻辑（验密、会话组装、OAuth 用户解析）。
 * 子类按登录渠道覆写 {@link #login}。
 */
@RequiredArgsConstructor
public abstract class AbstractLoginService implements LoginService {

    protected final TeacherMapper teacherMapper;
    protected final StudentMapper studentMapper;
    protected final DeanMapper deanMapper;
    protected final WXUserMapper wxUserMapper;
    protected final QQUserMapper qqUserMapper;
    protected final AlipayUserMapper alipayUserMapper;

    @Override
    public UserSession login(LoginRequest request) {
        throw new UnsupportedOperationException("当前登录渠道不支持该登录方式");
    }

    @Override
    public UserSession refreshAccessToken(String refreshToken) {
        throw new UnsupportedOperationException("TODO: Redis 集成后实现");
    }

    @Override
    public Boolean logout(String tokenId) {
        return true;
    }

    // ---- 公共工具方法 ----

    protected User lookupAcrossTables(String account) {
        Teacher teacher = teacherMapper.selectOne(new LambdaQueryWrapper<Teacher>()
                .eq(Teacher::getName, account).or()
                .eq(Teacher::getTeacherNo, account));
        if (teacher != null) return teacher;

        Student student = studentMapper.selectOne(new LambdaQueryWrapper<Student>()
                .eq(Student::getName, account).or()
                .eq(Student::getStudentNo, account));
        if (student != null) return student;

        Dean dean = deanMapper.selectOne(new LambdaQueryWrapper<Dean>()
                .eq(Dean::getName, account).or()
                .eq(Dean::getStaffNo, account));
        if (dean != null) return dean;

        return null;
    }

    protected UserSession buildOAuthSession(Object platformUser) {
        if (platformUser == null) return null;

        String table = switch (platformUser) {
            case WXUser w     -> w.getType();
            case QQUser q     -> q.getType();
            case AlipayUser a -> a.getType();
            default -> throw new IllegalArgumentException("未知的第三方平台实体");
        };
        Long uid = switch (platformUser) {
            case WXUser w     -> w.getUserId();
            case QQUser q     -> q.getUserId();
            case AlipayUser a -> a.getUserId();
            default -> throw new IllegalArgumentException("未知的第三方平台实体");
        };

        User user = switch (table) {
            case "teacher" -> teacherMapper.selectById(uid);
            case "student" -> studentMapper.selectById(uid);
            case "dean"    -> deanMapper.selectById(uid);
            default        -> throw new IllegalArgumentException("不支持的用户表: " + table);
        };
        return user != null ? buildSession(user, user.getName()) : null;
    }

    protected UserSession buildSession(User user, String account) {
        UserSession session = new UserSession();
        session.setUserId(user.getId());
        session.setUserType(detectUserType(user));
        session.setName(user.getName());
        session.setAccount(account);
        session.setAvatar(user.getAvatar());
        session.setRole(user.getRole());
        session.setTokenId(UUID.randomUUID().toString().replace("-", ""));
        session.setLoginTime(LocalDateTime.now());
        return session;
    }

    protected String detectUserType(User user) {
        if (user instanceof Teacher) return "teacher";
        if (user instanceof Student) return "student";
        if (user instanceof Dean)    return "dean";
        throw new IllegalArgumentException("未知的用户实体类型");
    }

    protected boolean matchPassword(String rawPassword, String storedPassword) {
        if (storedPassword == null) return false;
        String encoded = DigestUtils.md5DigestAsHex(rawPassword.getBytes(StandardCharsets.UTF_8));
        return encoded.equalsIgnoreCase(storedPassword);
    }
}
