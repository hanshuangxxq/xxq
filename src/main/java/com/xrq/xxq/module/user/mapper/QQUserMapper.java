package com.xrq.xxq.module.user.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.xrq.xxq.module.user.entity.QQUser;
import org.apache.ibatis.annotations.Mapper;

/**
 * QQ登录关联表 Mapper，继承 MyBatis Plus BaseMapper 提供通用 CRUD。
 *
 * @类名 QQUserMapper
 * @Date 2026/6/22
 */
@Mapper
public interface QQUserMapper extends BaseMapper<QQUser> {
}
