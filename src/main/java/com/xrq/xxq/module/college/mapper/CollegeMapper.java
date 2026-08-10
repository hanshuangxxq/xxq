package com.xrq.xxq.module.college.mapper;

import java.util.Collection;
import java.util.Map;
import java.util.stream.Collectors;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.xrq.xxq.module.college.entity.College;
import org.apache.ibatis.annotations.Mapper;

/**
 * 院系表 Mapper，继承 MyBatis Plus BaseMapper 提供通用 CRUD。
 */
@Mapper
public interface CollegeMapper extends BaseMapper<College> {

    /** 按院系名称查询（无匹配返回 null）。名称唯一性由应用层保证。 */
    default College findByName(String name) {
        if (name == null || name.isBlank()) {
            return null;
        }
        return selectOne(new LambdaQueryWrapper<College>().eq(College::getCollegeName, name));
    }

    /** 批量解析 college.id -> 名称 Map（空集合返回空 Map）。 */
    default Map<Long, String> toNameMap(Collection<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return Map.of();
        }
        return selectBatchIds(ids).stream()
                .collect(Collectors.toMap(College::getId, College::getCollegeName, (a, b) -> a));
    }
}
