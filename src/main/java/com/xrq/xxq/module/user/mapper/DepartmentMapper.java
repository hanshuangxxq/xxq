package com.xrq.xxq.module.user.mapper;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.xrq.xxq.module.user.entity.user.Department;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface DepartmentMapper extends BaseMapper<Department> {

    /** 按 userId 查询院系管理员行（无匹配返回 null）。 */
    default Department findByUserId(Long userId) {
        return selectOne(new LambdaQueryWrapper<Department>().eq(Department::getUserId, userId));
    }
}
