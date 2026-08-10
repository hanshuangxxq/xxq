package com.xrq.xxq.module.college.service.impl;

import java.util.Collection;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.xrq.xxq.common.BusinessException;
import com.xrq.xxq.module.college.dto.CollegeCreateRequest;
import com.xrq.xxq.module.college.dto.CollegeResponse;
import com.xrq.xxq.module.college.dto.CollegeUpdateRequest;
import com.xrq.xxq.module.college.entity.College;
import com.xrq.xxq.module.college.mapper.CollegeMapper;
import com.xrq.xxq.module.college.service.CollegeService;
import com.xrq.xxq.util.ParamValidator;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class CollegeServiceImpl
        extends ServiceImpl<CollegeMapper, College>
        implements CollegeService {

    @Override
    @Transactional
    public CollegeResponse create(CollegeCreateRequest request) {
        ParamValidator.requireNonBlank(request.getCollegeName(), "院系名称");
        ensureNameNotExists(request.getCollegeName(), null);
        College college = new College();
        college.setCollegeName(request.getCollegeName());
        college.setCollegeCode(request.getCollegeCode());
        college.setCollegeNo(request.getCollegeNo());
        save(college);
        return toResponse(college);
    }

    @Override
    @Transactional
    public CollegeResponse update(Long id, CollegeUpdateRequest request) {
        College college = baseMapper.selectById(id);
        if (college == null) {
            throw new BusinessException(404, "院系不存在");
        }
        if (request.getCollegeName() != null) {
            ParamValidator.requireNonBlank(request.getCollegeName(), "院系名称");
            ensureNameNotExists(request.getCollegeName(), id);
            college.setCollegeName(request.getCollegeName());
        }
        if (request.getCollegeCode() != null) {
            college.setCollegeCode(request.getCollegeCode());
        }
        if (request.getCollegeNo() != null) {
            college.setCollegeNo(request.getCollegeNo());
        }
        updateById(college);
        return toResponse(college);
    }

    @Override
    @Transactional
    public void delete(Long id) {
        if (baseMapper.selectById(id) == null) {
            throw new BusinessException(404, "院系不存在");
        }
        removeById(id);
    }

    @Override
    public College getByName(String name) {
        return baseMapper.findByName(name);
    }

    @Override
    public Map<Long, String> toNameMap(Collection<Long> ids) {
        return baseMapper.toNameMap(ids);
    }

    // ---- helpers ----

    private void ensureNameNotExists(String name, Long excludeId) {
        Long count = baseMapper.selectCount(new LambdaQueryWrapper<College>()
                .eq(College::getCollegeName, name)
                .ne(excludeId != null, College::getId, excludeId));
        if (count != null && count > 0) {
            throw new BusinessException(409, "院系名称已存在");
        }
    }

    private CollegeResponse toResponse(College college) {
        CollegeResponse resp = new CollegeResponse();
        resp.setId(college.getId());
        resp.setCollegeName(college.getCollegeName());
        resp.setCollegeCode(college.getCollegeCode());
        resp.setCollegeNo(college.getCollegeNo());
        resp.setCreateTime(college.getCreateTime());
        resp.setUpdateTime(college.getUpdateTime());
        return resp;
    }
}
