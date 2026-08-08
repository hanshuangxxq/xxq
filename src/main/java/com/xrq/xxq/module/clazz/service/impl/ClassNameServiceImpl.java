package com.xrq.xxq.module.clazz.service.impl;

import java.util.Collection;
import java.util.Map;
import java.util.stream.Collectors;

import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.xrq.xxq.module.clazz.entity.ClassName;
import com.xrq.xxq.module.clazz.mapper.ClassNameMapper;
import com.xrq.xxq.module.clazz.service.ClassNameService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ClassNameServiceImpl extends ServiceImpl<ClassNameMapper, ClassName> implements ClassNameService {
    private final ClassNameMapper classNameMapper;

    @Override
    public Map<Long, String> toNameMap(Collection<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return Map.of();
        }
        return listByIds(ids).stream()
                .collect(Collectors.toMap(ClassName::getId, ClassName::getClassName, (a, b) -> a));
    }
}
