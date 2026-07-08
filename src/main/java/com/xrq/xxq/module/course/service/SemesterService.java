package com.xrq.xxq.module.course.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.xrq.xxq.module.course.entity.Semester;

public interface SemesterService extends IService<Semester> {
    Semester getCurrent();
}
