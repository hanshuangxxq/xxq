package com.xrq.xxq.module.practice.internship.entity;

import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;

import lombok.Data;

/**
 * 培训课程实体。
 * <p>
 * teacher_id 存授课/负责教师 user.id；enrolled_count 记录已报名人数。
 */
@Data
@TableName("training_course")
public class TrainingCourse {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long semesterId;
    private String title;
    private String description;
    private Long teacherId;             // 教师 user.id
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private Integer capacity;
    private Integer enrolledCount;
    private TrainingStatusEnum status;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;

    @TableLogic
    private Integer deleted;
}
