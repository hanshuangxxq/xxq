package com.xrq.xxq.module.analysis.entity;

import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/**
 * 课程级评教模板覆盖：teach_info 维度，未覆盖则用全局默认模板。
 */
@Data
@TableName("evaluation_template_override")
public class EvaluationTemplateOverride {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long teachInfoId;
    private Long templateId;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
