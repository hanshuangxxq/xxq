package com.xrq.xxq.module.user.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.xrq.xxq.module.user.entity.AlipayUser;
import org.apache.ibatis.annotations.Mapper;

/**
 * 支付宝登录关联表 Mapper，继承 MyBatis Plus BaseMapper 提供通用 CRUD。
 *
 * @类名 AlipayUserMapper
 * @Date 2026/6/22
 */
@Mapper
public interface AlipayUserMapper extends BaseMapper<AlipayUser> {
}
