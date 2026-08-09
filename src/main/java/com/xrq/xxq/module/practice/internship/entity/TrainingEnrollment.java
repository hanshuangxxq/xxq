package com.xrq.xxq.module.practice.internship.entity;

import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;

import lombok.Data;

/**
 * 培训报名记录。
 * <p>
 * 报名即生效（无需审核），student_id 存学生 user.id。
 */
@Data
@TableName("training_enrollment")
public class TrainingEnrollment {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long courseId;
    private Long studentId;             // 学生 user.id
    private LocalDateTime enrollTime;
    private EnrollStatusEnum status;

    @TableLogic
    private Integer deleted;
}
