package com.xrq.xxq.module.course.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.xrq.xxq.module.course.entity.Semester;
import com.xrq.xxq.module.course.mapper.SemesterMapper;
import com.xrq.xxq.module.course.service.SemesterService;
import org.springframework.stereotype.Service;

@Service
public class SemesterServiceImpl extends ServiceImpl<SemesterMapper, Semester> implements SemesterService {

    @Override
    public Semester getCurrent() {
        return getOne(new LambdaQueryWrapper<Semester>().eq(Semester::getStatus, "CURRENT"));
    }
}
