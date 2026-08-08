package com.xrq.xxq.module.clazz.service;

import java.util.Collection;
import java.util.Map;

import com.baomidou.mybatisplus.spring.service.IService;
import com.xrq.xxq.module.clazz.entity.ClassName;

public interface ClassNameService extends IService<ClassName> {

    /** 批量解析 classId -> 班级名 Map（空集合返回空 Map）。 */
    Map<Long, String> toNameMap(Collection<Long> ids);
}
