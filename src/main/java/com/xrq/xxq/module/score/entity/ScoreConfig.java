package com.xrq.xxq.module.score.entity;

import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/**
 * 成绩占比配置：每条授课安排一行，由任课教师设置平时分占比。
 * 期末占比 = 100 - regular_ratio。
 */
@Data
@TableName("score_config")
public class ScoreConfig {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long teachInfoId;        // FK -> teach_info.id（唯一）
    private Integer regularRatio;    // 平时分占比 0-100
    private Long createUserId;       // 设置人 user.id
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
