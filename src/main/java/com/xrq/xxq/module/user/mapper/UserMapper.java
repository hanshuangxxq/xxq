package com.xrq.xxq.module.user.mapper;

import java.util.Collection;
import java.util.Map;
import java.util.stream.Collectors;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.xrq.xxq.module.user.entity.User;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface UserMapper extends BaseMapper<User> {

    /** 批量解析 user.id -> 姓名 Map（空集合返回空 Map）。 */
    default Map<Long, String> toNameMap(Collection<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return Map.of();
        }
        return selectByIds(ids).stream()
                .collect(Collectors.toMap(User::getId, User::getName, (a, b) -> a));
    }
}
