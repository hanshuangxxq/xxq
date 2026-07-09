package com.xrq.xxq.module.semester.service;

import com.baomidou.mybatisplus.spring.service.IService;
import com.xrq.xxq.module.semester.entity.Semester;

public interface SemesterService extends IService<Semester> {
    Semester getCurrent();
}
