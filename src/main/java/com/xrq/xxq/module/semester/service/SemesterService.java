package com.xrq.xxq.module.semester.service;

import java.util.Collection;
import java.util.Map;

import com.baomidou.mybatisplus.spring.service.IService;
import com.xrq.xxq.module.semester.entity.Semester;

public interface SemesterService extends IService<Semester> {
    Semester getCurrent();

    /** 解析学期 id：非空直返，否则取当前学期 id（无当前学期返回 null）。 */
    Long resolveOrDefault(Long semesterId);

    /** 批量解析学期 id -> 名称 Map（空集合返回空 Map）。 */
    Map<Long, String> toNameMap(Collection<Long> ids);
}
