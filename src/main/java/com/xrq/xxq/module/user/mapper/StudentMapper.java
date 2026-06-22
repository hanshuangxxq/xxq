package com.xrq.xxq.module.user.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.xrq.xxq.module.user.entity.user.Student;
import org.apache.ibatis.annotations.Mapper;

/**
 * @类名 StudentMapper
 * @Date 2026/6/22
 * @Description 学生Mapper
 */
@Mapper
public interface StudentMapper extends BaseMapper<Student> {
}
