package com.xrq.xxq.module.preference.service;

import java.util.Map;

import com.baomidou.mybatisplus.spring.service.IService;
import com.xrq.xxq.module.preference.entity.UserPreference;

public interface UserPreferenceService extends IService<UserPreference> {

    /**
     * 获取用户偏好；从未保存过（或刚重置）返回空 Map。
     */
    Map<String, Object> getPrefs(Long userId);

    /**
     * 顶层浅合并：key 已存在则更新、不存在则添加、value 为 null 则删除该 key；
     * delta 未提及的 key 保持不变（嵌套对象整体替换，不做深合并）。
     *
     * @return 合并后的完整偏好
     */
    Map<String, Object> mergePrefs(Long userId, Map<String, Object> delta);

    /**
     * 重置用户全部偏好：物理删除该用户的偏好行，回到系统默认。幂等，无行可删时不报错。
     */
    void resetPrefs(Long userId);
}
