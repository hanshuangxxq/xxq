package com.xrq.xxq.module.user.service.impl;

import org.springframework.stereotype.Service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.xrq.xxq.common.BusinessException;
import com.xrq.xxq.module.user.entity.user.Grade;
import com.xrq.xxq.module.user.mapper.GradeMapper;
import com.xrq.xxq.module.user.service.GradeService;

@Service
public class GradeServiceImpl extends ServiceImpl<GradeMapper, Grade> implements GradeService {

    @Override
    public boolean save(Grade grade) {
        if (grade.getName() != null && !grade.getName().isBlank()) {
            Long cnt = baseMapper.selectCount(new LambdaQueryWrapper<Grade>()
                    .eq(Grade::getName, grade.getName()));
            if (cnt != null && cnt > 0) {
                throw new BusinessException(409, "年级名称已存在");
            }
        }
        return super.save(grade);
    }

    @Override
    public boolean updateById(Grade grade) {
        if (grade.getName() != null && !grade.getName().isBlank()) {
            Long cnt = baseMapper.selectCount(new LambdaQueryWrapper<Grade>()
                    .eq(Grade::getName, grade.getName())
                    .ne(grade.getId() != null, Grade::getId, grade.getId()));
            if (cnt != null && cnt > 0) {
                throw new BusinessException(409, "年级名称已存在");
            }
        }
        return super.updateById(grade);
    }
}
