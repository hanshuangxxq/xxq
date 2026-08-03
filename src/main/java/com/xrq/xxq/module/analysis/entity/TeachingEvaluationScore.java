package com.xrq.xxq.module.analysis.entity;

import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/**
 * 评教得分明细：按模板指标动态存储，替代 teaching_evaluation 旧 4 固定列。
 * item_name 为提交时快照，历史不可变。
 */
@Data
@TableName("teaching_evaluation_score")
public class TeachingEvaluationScore {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long evaluationId;      // FK -> teaching_evaluation.id
    private Long itemId;
    private String itemName;        // 指标名称快照
    private Integer maxScore;       // 满分快照
    private Integer score;          // 1-max_score
    private LocalDateTime createTime;
}
