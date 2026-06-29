package com.xrq.xxq.module.user.service.login.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.xrq.xxq.module.user.entity.WXUser;
import com.xrq.xxq.module.user.dto.LoginRequest;
import com.xrq.xxq.module.user.dto.UserSession;
import com.xrq.xxq.module.user.mapper.*;
import com.xrq.xxq.module.user.service.login.AbstractLoginService;
import com.xrq.xxq.module.user.mapper.*;
import com.xrq.xxq.module.user.session.LoginSessionStore;
import com.xrq.xxq.util.JwtUtils;
import org.springframework.stereotype.Service;

@Service
public class WeChatLoginService extends AbstractLoginService {

    public WeChatLoginService(UserMapper userMapper,
                              TeacherMapper teacherMapper,
                              StudentMapper studentMapper,
                              AcademicAdminMapper academicAdminMapper,
                              DepartmentMapper departmentMapper,
                              WXUserMapper wxUserMapper,
                              QQUserMapper qqUserMapper,
                              AlipayUserMapper alipayUserMapper,
                              LoginSessionStore sessionStore,
                              JwtUtils jwtUtils) {
        super(userMapper, teacherMapper, studentMapper, academicAdminMapper, departmentMapper, wxUserMapper, qqUserMapper, alipayUserMapper, sessionStore, jwtUtils);
    }

    @Override
    public UserSession login(LoginRequest request) {
        String code = (String) request.getData().get("code");
        WXUser wx = wxUserMapper.selectOne(new LambdaQueryWrapper<WXUser>()
                .eq(WXUser::getWxOpenid, code));
        return buildOAuthSession(wx);
    }
}
