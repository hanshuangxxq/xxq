package com.xrq.xxq.module.exam.entity;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/**
 * 考试实体。
 * <p>
 * 期末/期中考试绑 {@code teachInfoId}（授课安排），学生通过所属班级/选课班匹配；
 * 补考/重修考试 {@code teachInfoId} 为 NULL，考生名单存 {@link ExamStudent}（建考时按不及格名单自动生成）。
 */
@Data
@TableName("exam")
public class Exam {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String examName;        // 考试名称
    private Long courseId;          // FK -> course.id（公选课为 NULL）
    private Long campaignId;        // 公选课 FK -> selection_campaign.id（常规课为 NULL）
    private Long teachInfoId;       // 期末/期中绑 teach_info.id；补考/重修为 NULL
    private String className;       // 排考班级（单班级名；期末/期中必填，补考/重修为 NULL）
    private ExamTypeEnum examType;  // FINAL/MIDTERM/MAKEUP/RETAKE
    private Long semesterId;        // FK -> semester.id
    private LocalDate examDate;     // 考试日期
    private LocalTime startTime;    // 开始时间
    private LocalTime endTime;      // 结束时间
    private Long localId;           // FK -> local.id 考试地点
    private String notes;           // 备注
    private ExamStatusEnum status;  // SCHEDULED/CANCELED/COMPLETED
    private Long createUserId;      // 创建人 user.id
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
