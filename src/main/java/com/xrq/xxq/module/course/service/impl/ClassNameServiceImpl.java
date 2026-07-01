package com.xrq.xxq.module.course.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.xrq.xxq.module.course.entity.ClassName;
import com.xrq.xxq.module.course.mapper.ClassNameMapper;
import com.xrq.xxq.module.course.service.ClassNameService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ClassNameServiceImpl extends ServiceImpl<ClassNameMapper, ClassName> implements ClassNameService {
    private final ClassNameMapper classNameMapper;
}
