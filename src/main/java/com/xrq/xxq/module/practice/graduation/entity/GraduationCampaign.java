package com.xrq.xxq.module.practice.graduation.entity;

import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;

import lombok.Data;

/**
 * 毕设活动（阶段一顶层归属单位）。
 * <p>
 * 配置：参与年级、选题时间窗、教师名额参数（可分配上限/自由选择上限）、
 * 各过程环节时间窗（阶段二/三）、成绩权重（阶段四）。
 */
@Data
@TableName("graduation_campaign")
public class GraduationCampaign {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 活动名称（同年度内唯一） */
    private String name;

    /** 参与年级ID，逗号分隔（如 "1,2"） */
    private String allowedGradeIds;

    /** 选题开始时间 */
    private LocalDateTime topicStartTime;

    /** 选题截止时间 */
    private LocalDateTime topicEndTime;

    /** 每教师可分配学生数上限 */
    private Integer supervisorCapacity;

    /** 每教师自由选择学生数上限（<= supervisorCapacity） */
    private Integer freeSelectCapacity;

    /** 开题报告提交开始时间（阶段二） */
    private LocalDateTime openingStartTime;

    /** 开题报告提交截止时间（阶段二） */
    private LocalDateTime openingEndTime;

    /** 中期检查提交开始时间（阶段二） */
    private LocalDateTime midtermStartTime;

    /** 中期检查提交截止时间（阶段二） */
    private LocalDateTime midtermEndTime;

    /** 论文提交开始时间（阶段三） */
    private LocalDateTime thesisStartTime;

    /** 论文提交截止时间（阶段三） */
    private LocalDateTime thesisEndTime;

    /** 指导教师评分权重（阶段四，默认30%） */
    private Integer advisorWeight;

    /** 评阅教师评分权重（阶段四，默认20%） */
    private Integer reviewerWeight;

    /** 答辩评分权重（阶段四，默认50%） */
    private Integer defenseWeight;

    private CampaignStatusEnum status;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;

    @TableLogic
    private Integer deleted;
}
