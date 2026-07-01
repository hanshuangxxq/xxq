package com.xrq.xxq.module.course.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.xrq.xxq.module.course.entity.Course;
import com.xrq.xxq.module.course.mapper.CourseMapper;
import com.xrq.xxq.module.course.service.CourseService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 课程服务实现，继承 MyBatis Plus ServiceImpl 提供通用 CRUD。
 *
 * @类名 CourseServiceImpl
 * @Date 2026/6/30
 */
@Service
@RequiredArgsConstructor
public class CourseServiceImpl extends ServiceImpl<CourseMapper, Course> implements CourseService {
    private final CourseMapper courseMapper;
}
