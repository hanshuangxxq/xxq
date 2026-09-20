package com.xrq.xxq.module.mojor.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.xrq.xxq.common.BusinessException;
import com.xrq.xxq.module.clazz.entity.ClassName;
import com.xrq.xxq.module.clazz.mapper.ClassNameMapper;
import com.xrq.xxq.module.college.mapper.CollegeMapper;
import com.xrq.xxq.module.mojor.entity.Major;
import com.xrq.xxq.module.mojor.mapper.MajorMapper;
import com.xrq.xxq.module.mojor.service.MajorService;
import com.xrq.xxq.util.ParamValidator;
import com.xrq.xxq.util.ReferenceValidator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class MajorServiceImpl extends ServiceImpl<MajorMapper, Major> implements MajorService {

    private final CollegeMapper collegeMapper;
    private final ClassNameMapper classNameMapper;
    private final ReferenceValidator referenceValidator;

    @Override
    @Transactional
    public Major create(Major major) {
        ParamValidator.requireNonBlank(major.getMajorName(), "专业名称");
        // 院系必填：学生的院系链是 class_name -> major.college_id -> college，
        // 专业不挂院系会让其下所有班级与学生一起失去院系归属
        ParamValidator.requireNonNull(major.getCollegeId(), "所属院系");
        referenceValidator.requireExists(collegeMapper, major.getCollegeId(), "院系");
        save(major);
        return major;
    }

    @Override
    @Transactional
    public Major update(Long id, Major major) {
        if (getById(id) == null) {
            throw new BusinessException(404, "专业不存在");
        }
        // collegeId 传 null 时 MyBatis Plus 按 NOT_NULL 策略忽略，即保持原院系不变
        if (major.getCollegeId() != null) {
            referenceValidator.requireExists(collegeMapper, major.getCollegeId(), "院系");
        }
        major.setId(id);
        updateById(major);
        return major;
    }

    @Override
    @Transactional
    public void delete(Long id) {
        if (getById(id) == null) {
            throw new BusinessException(404, "专业不存在");
        }
        // 班级挂专业：删专业会让这些班级及其学生的专业与院系推导全为 NULL，故要求先改班级归属
        Long refs = classNameMapper.selectCount(
                new LambdaQueryWrapper<ClassName>().eq(ClassName::getMajorId, id));
        if (refs != null && refs > 0) {
            throw new BusinessException(409, "该专业下仍有 " + refs + " 个班级，无法删除");
        }
        removeById(id);
    }
}
