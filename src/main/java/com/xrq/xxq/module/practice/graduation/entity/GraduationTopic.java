package com.xrq.xxq.module.practice.graduation.entity;

import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;

import lombok.Data;

/**
 * 毕业设计选题实体（教师发布）。
 * <p>
 * teacher_id 存教师 user.id；selected_count 记录已申请人数（含待审核），用于容量控制。
 */
@Data
@TableName("graduation_topic")
public class GraduationTopic {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long semesterId;
    private Long teacherId;          // 教师 user.id
    private String title;
    private String description;
    private String requirements;     // 选题要求
    private Integer capacity;        // 可带学生数上限
    private Integer selectedCount;   // 已申请人数
    private TopicStatusEnum status;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;

    @TableLogic
    private Integer deleted;
}
