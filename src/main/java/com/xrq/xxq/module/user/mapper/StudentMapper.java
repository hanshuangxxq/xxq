package com.xrq.xxq.module.user.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.xrq.xxq.module.user.entity.user.Student;
import org.apache.ibatis.annotations.Mapper;

/**
 * 学生表 Mapper，继承 MyBatis Plus BaseMapper 提供通用 CRUD。
 *
 * @类名 StudentMapper
 * @Date 2026/6/22
 */
@Mapper
public interface StudentMapper extends BaseMapper<Student> {
}
