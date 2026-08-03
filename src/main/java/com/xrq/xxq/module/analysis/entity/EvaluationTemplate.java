package com.xrq.xxq.module.analysis.entity;

import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/**
 * 评教模板：一组评教指标的集合。is_default=1 为全局默认模板（全局唯一）。
 * 学生评教时按「课程覆盖 > 全局默认」解析所用模板。
 */
@Data
@TableName("evaluation_template")
public class EvaluationTemplate {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String name;
    private String description;
    private EvaluationTemplateStatusEnum status;  // ENABLED/DISABLED
    private Integer isDefault;      // 0:普通 1:全局默认
    private Long createUserId;      // 创建人 user.id
    private LocalDateTime createTime;
    private LocalDateTime updateTime;

    @TableLogic
    private Integer deleted;
}
