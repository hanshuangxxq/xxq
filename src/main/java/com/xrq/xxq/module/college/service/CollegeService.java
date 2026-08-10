package com.xrq.xxq.module.college.service;

import java.util.Collection;
import java.util.Map;

import com.baomidou.mybatisplus.spring.service.IService;
import com.xrq.xxq.module.college.dto.CollegeCreateRequest;
import com.xrq.xxq.module.college.dto.CollegeResponse;
import com.xrq.xxq.module.college.dto.CollegeUpdateRequest;
import com.xrq.xxq.module.college.entity.College;

/**
 * 院系服务：教务 CRUD + 名称解析。
 */
public interface CollegeService extends IService<College> {

    CollegeResponse create(CollegeCreateRequest request);

    CollegeResponse update(Long id, CollegeUpdateRequest request);

    void delete(Long id);

    /** 按名称查询院系（无匹配返回 null），供批量导入等场景解析 college_id。 */
    College getByName(String name);

    /** 批量解析 college.id -> 名称 Map（空集合返回空 Map）。 */
    Map<Long, String> toNameMap(Collection<Long> ids);
}
