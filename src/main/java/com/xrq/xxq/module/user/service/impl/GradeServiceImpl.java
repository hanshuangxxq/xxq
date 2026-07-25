package com.xrq.xxq.module.user.service.impl;

import org.springframework.stereotype.Service;

import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.xrq.xxq.module.user.entity.user.Grade;
import com.xrq.xxq.module.user.mapper.GradeMapper;
import com.xrq.xxq.module.user.service.GradeService;

@Service
public class GradeServiceImpl extends ServiceImpl<GradeMapper, Grade> implements GradeService {
}
