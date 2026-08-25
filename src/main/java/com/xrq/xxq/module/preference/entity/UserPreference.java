package com.xrq.xxq.module.preference.entity;

import java.time.LocalDateTime;
import java.util.Map;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import lombok.Data;

/**
 * 用户个性化偏好。
 * 一个用户一行（应用层保证），prefs 为顶层 JSON 对象，存前端偏好设置（主题/语言/布局等）。
 */
@Data
@TableName(value = "user_preference", autoResultMap = true)
public class UserPreference {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;              // 所属用户 user.id
    @TableField(typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> prefs; // 偏好数据（顶层 JSON 对象）
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
