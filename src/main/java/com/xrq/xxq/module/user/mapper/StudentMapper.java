package com.xrq.xxq.module.user.mapper;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.xrq.xxq.module.user.entity.user.Student;
import org.apache.ibatis.annotations.Mapper;

/**
 * 学生表 Mapper，继承 MyBatis Plus BaseMapper 提供通用 CRUD。
 *
 * @类名 StudentMapper
 * @Date 2026/6/22
 */
@Mapper
public interface StudentMapper extends BaseMapper<Student> {

    /** 批量解析 student.userId -> 学号 Map（空集合返回空 Map，允许 null key 查询）。 */
    default Map<Long, String> toStudentNoMap(Collection<Long> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return new HashMap<>(); // 不用 Map.of():get(null) 会 NPE,调用方会传可空外键
        }
        return selectList(new LambdaQueryWrapper<Student>().in(Student::getUserId, userIds)).stream()
                .collect(Collectors.toMap(Student::getUserId, Student::getStudentNo, (a, b) -> a));
    }
}
