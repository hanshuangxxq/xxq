package com.xrq.xxq.module.analysis.entity;

import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/**
 * 评教周期：按学期一行，教务统一开启/关闭。未开启或已关闭时学生不可评教。
 */
@Data
@TableName("evaluation_period")
public class EvaluationPeriod {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long semesterId;
    private EvaluationStatusEnum status;
    private Long openUserId;     // 开启人 user.id
    private LocalDateTime openTime;
    private LocalDateTime closeTime;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
