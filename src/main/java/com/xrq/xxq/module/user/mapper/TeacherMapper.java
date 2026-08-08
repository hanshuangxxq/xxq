package com.xrq.xxq.module.user.mapper;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.xrq.xxq.module.user.entity.user.Teacher;
import org.apache.ibatis.annotations.Mapper;

/**
 * 教师表 Mapper，继承 MyBatis Plus BaseMapper 提供通用 CRUD。
 *
 * @类名 TeacherMapper
 * @Date 2026/6/22
 */
@Mapper
public interface TeacherMapper extends BaseMapper<Teacher> {

    /** 按 userId 查询教师行（无匹配返回 null）。 */
    default Teacher findByUserId(Long userId) {
        return selectOne(new LambdaQueryWrapper<Teacher>().eq(Teacher::getUserId, userId));
    }
}
