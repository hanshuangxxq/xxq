package com.xrq.xxq.module.user.mapper;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.xrq.xxq.module.user.entity.User;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface UserMapper extends BaseMapper<User> {

    /**
     * 批量解析 user.id -> 姓名 Map（空集合返回空 Map，允许 null key 查询）。
     * 空集合分支用 {@code HashMap} 而非 {@code Map.of()}：后者 {@code get(null)} 抛 NPE。
     */
    default Map<Long, String> toNameMap(Collection<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return new HashMap<>();
        }
        return selectByIds(ids).stream()
                .collect(Collectors.toMap(User::getId, User::getName, (a, b) -> a));
    }
}
