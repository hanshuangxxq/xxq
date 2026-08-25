package com.xrq.xxq.module.preference.service.impl;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.stereotype.Service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import com.xrq.xxq.common.BusinessException;
import com.xrq.xxq.module.preference.entity.UserPreference;
import com.xrq.xxq.module.preference.mapper.UserPreferenceMapper;
import com.xrq.xxq.module.preference.service.UserPreferenceService;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class UserPreferenceServiceImpl extends ServiceImpl<UserPreferenceMapper, UserPreference>
        implements UserPreferenceService {

    /** 偏好 JSON 序列化后的最大字节数（64KB） */
    private static final int MAX_PREFS_BYTES = 64 * 1024;

    private final ObjectMapper objectMapper;

    @Override
    public Map<String, Object> getPrefs(Long userId) {
        UserPreference existing = findByUserId(userId);
        if (existing == null || existing.getPrefs() == null) {
            return Collections.emptyMap();
        }
        return new LinkedHashMap<>(existing.getPrefs());
    }

    @Override
    public Map<String, Object> mergePrefs(Long userId, Map<String, Object> delta) {
        if (delta == null) {
            throw new BusinessException(400, "请求体不能为空");
        }
        UserPreference existing = findByUserId(userId);
        Map<String, Object> merged = existing != null && existing.getPrefs() != null
                ? new LinkedHashMap<>(existing.getPrefs())
                : new LinkedHashMap<>();
        delta.forEach((key, value) -> {
            if (value == null) {
                merged.remove(key);
            } else {
                merged.put(key, value);
            }
        });
        checkSize(merged);
        if (existing == null) {
            UserPreference row = new UserPreference();
            row.setUserId(userId);
            row.setPrefs(merged);
            save(row);
        } else {
            existing.setPrefs(merged);
            // 项目无 MetaObjectHandler：置 null 使其不进 SET 子句，
            // update_time 交由 DB 的 ON UPDATE CURRENT_TIMESTAMP 刷新
            existing.setCreateTime(null);
            existing.setUpdateTime(null);
            updateById(existing);
        }
        return merged;
    }

    @Override
    public void resetPrefs(Long userId) {
        remove(new LambdaQueryWrapper<UserPreference>()
                .eq(UserPreference::getUserId, userId));
    }

    /**
     * 一个用户一行（应用层保证）；getOne(..., false) 表示万一存在脏数据多行时取首行，不抛异常。
     */
    private UserPreference findByUserId(Long userId) {
        return getOne(new LambdaQueryWrapper<UserPreference>()
                .eq(UserPreference::getUserId, userId), false);
    }

    private void checkSize(Map<String, Object> prefs) {
        final byte[] bytes;
        try {
            bytes = objectMapper.writeValueAsBytes(prefs);
        } catch (JacksonException e) {
            throw new BusinessException(400, "偏好数据序列化失败");
        }
        if (bytes.length > MAX_PREFS_BYTES) {
            throw new BusinessException(400, "偏好数据过大");
        }
    }
}
