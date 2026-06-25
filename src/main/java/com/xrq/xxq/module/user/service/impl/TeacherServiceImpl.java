package com.xrq.xxq.module.user.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.xrq.xxq.module.user.entity.user.Teacher;
import com.xrq.xxq.module.user.mapper.TeacherMapper;
import com.xrq.xxq.module.user.service.TeacherService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 教师服务实现，继承 MyBatis Plus ServiceImpl 提供通用 CRUD。
 *
 * @类名 TeacherServiceImpl
 * @Date 2026/6/22
 */
@Service
@RequiredArgsConstructor
public class TeacherServiceImpl extends ServiceImpl<TeacherMapper, Teacher> implements TeacherService {
    private final TeacherMapper teacherMapper;
}
