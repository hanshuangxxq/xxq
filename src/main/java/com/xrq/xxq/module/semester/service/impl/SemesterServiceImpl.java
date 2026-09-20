package com.xrq.xxq.module.semester.service.impl;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.xrq.xxq.module.semester.entity.Semester;
import com.xrq.xxq.module.semester.mapper.SemesterMapper;
import com.xrq.xxq.module.semester.service.SemesterService;
import org.springframework.stereotype.Service;

@Service
public class SemesterServiceImpl extends ServiceImpl<SemesterMapper, Semester> implements SemesterService {

    @Override
    public Semester getCurrent() {
        return getOne(new LambdaQueryWrapper<Semester>().eq(Semester::getStatus, "CURRENT"));
    }

    @Override
    public Long resolveOrDefault(Long semesterId) {
        if (semesterId != null) {
            return semesterId;
        }
        Semester current = getCurrent();
        return current != null ? current.getId() : null;
    }

    @Override
    public Map<Long, String> toNameMap(Collection<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return new HashMap<>(); // 不用 Map.of():get(null) 会 NPE,调用方会传可空外键
        }
        return listByIds(ids).stream()
                .collect(Collectors.toMap(Semester::getId, Semester::getName, (a, b) -> a));
    }
}
