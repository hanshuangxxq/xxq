package com.xrq.xxq.module.coursework.assignment.entity;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/**
 * 课程作业：挂在授课组锚点行上（见 {@code CourseGroupResolver}）。
 * 状态机：DRAFT → PUBLISHED → CLOSED；仅 DRAFT 可删除。
 */
@Data
@TableName("course_assignment")
public class CourseAssignment {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long teachInfoId;        // 授课组锚点行 teach_info.id
    private String title;
    private String content;          // 作业要求
    private String fileName;         // 附件 objects/ 相对路径（可空）
    private String fileOriginal;     // 附件展示名（可空）
    private LocalDateTime deadline;
    private BigDecimal totalScore;   // 满分
    private AssignmentStatusEnum status;
    private Long teacherId;          // 发布人 user.id
    private LocalDateTime createTime;
    private LocalDateTime updateTime;

    @TableLogic
    private Integer deleted;
}
