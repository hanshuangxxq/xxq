package com.xrq.xxq.module.course.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.xrq.xxq.module.course.entity.Course;
import org.apache.ibatis.annotations.Mapper;

/**
 * 课程表 Mapper，继承 MyBatis Plus BaseMapper 提供通用 CRUD。
 *
 * @类名 CourseMapper
 * @Date 2026/6/30
 */
@Mapper
public interface CourseMapper extends BaseMapper<Course> {
}
