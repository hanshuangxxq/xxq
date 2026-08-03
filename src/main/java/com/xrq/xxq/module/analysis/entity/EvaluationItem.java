package com.xrq.xxq.module.analysis.entity;

import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/**
 * 评教指标库：教务自定义的评教指标（共享），可被多个模板引用。
 * 模板引用时在 evaluation_template_item 中快照指标名与满分。
 */
@Data
@TableName("evaluation_item")
public class EvaluationItem {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String name;            // 指标名称，如 教学态度
    private String description;     // 指标说明
    private Integer maxScore;       // 满分（评分上限），模板内各指标须一致
    private Long createUserId;      // 创建人 user.id
    private LocalDateTime createTime;
    private LocalDateTime updateTime;

    @TableLogic
    private Integer deleted;
}
