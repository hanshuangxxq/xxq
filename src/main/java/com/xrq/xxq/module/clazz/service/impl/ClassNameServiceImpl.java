package com.xrq.xxq.module.clazz.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.xrq.xxq.module.clazz.entity.ClassName;
import com.xrq.xxq.module.clazz.mapper.ClassNameMapper;
import com.xrq.xxq.module.clazz.service.ClassNameService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ClassNameServiceImpl extends ServiceImpl<ClassNameMapper, ClassName> implements ClassNameService {
    private final ClassNameMapper classNameMapper;
}
