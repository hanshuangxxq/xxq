package com.xrq.xxq.module.analysis.entity;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/**
 * 教师评教记录：一学生对一授课安排一条评教。
 * <p>评分明细存 teaching_evaluation_score（按模板指标动态），template_id 为提交时所评教模板快照；
 * avg_score 由服务端按各指标原始分均值派生。
 */
@Data
@TableName("teaching_evaluation")
public class TeachingEvaluation {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long teachInfoId;
    private Long teacherId;          // teacher.id（快照）
    private Long courseId;           // 课程ID（快照，公选课为 NULL）
    private Long campaignId;         // 公选课活动ID（快照，常规课为 NULL）
    private Long semesterId;
    private Long studentUserId;
    private Long templateId;         // 提交时所评教模板 id（快照）
    private BigDecimal avgScore;     // 各指标原始分均值（派生）
    private String comment;
    private Integer anonymous;        // 0:实名 1:匿名
    private LocalDateTime createTime;
    private LocalDateTime updateTime;

    @TableLogic
    private Integer deleted;
}
