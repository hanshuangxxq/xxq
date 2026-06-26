package com.xrq.xxq.module.user.service.login.impl;

import com.xrq.xxq.module.user.entity.User;
import com.xrq.xxq.module.user.dto.LoginRequest;
import com.xrq.xxq.module.user.dto.UserSession;
import com.xrq.xxq.module.user.service.login.AbstractLoginService;
import com.xrq.xxq.module.user.mapper.*;
import com.xrq.xxq.module.user.session.LoginSessionStore;
import com.xrq.xxq.util.JwtUtils;
import org.springframework.stereotype.Service;

@Service
public class AccountLoginService extends AbstractLoginService {

    public AccountLoginService(UserMapper userMapper,
                               TeacherMapper teacherMapper,
                               StudentMapper studentMapper,
                               DeanMapper deanMapper,
                               WXUserMapper wxUserMapper,
                               QQUserMapper qqUserMapper,
                               AlipayUserMapper alipayUserMapper,
                               LoginSessionStore sessionStore,
                               JwtUtils jwtUtils) {
        super(userMapper, teacherMapper, studentMapper, deanMapper, wxUserMapper, qqUserMapper, alipayUserMapper, sessionStore, jwtUtils);
    }

    @Override
    public UserSession login(LoginRequest request) {
        String account = (String) request.getData().get("account");
        String password = (String) request.getData().get("password");

        User user = lookupAcrossTables(account);
        if (user == null) {
            throw new IllegalArgumentException("账号不存在");
        }
        if (!matchPassword(password, user.getPassword())) {
            throw new IllegalArgumentException("密码错误");
        }
        return buildSession(user, account);
    }
}
