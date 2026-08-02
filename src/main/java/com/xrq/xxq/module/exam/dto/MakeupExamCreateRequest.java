package com.xrq.xxq.module.exam.dto;

import java.time.LocalDate;
import java.time.LocalTime;

import com.xrq.xxq.module.exam.entity.ExamTypeEnum;

import lombok.Data;

/**
 * 补考/重修考试创建请求：建考时按不及格名单自动生成考生。
 */
@Data
public class MakeupExamCreateRequest {

    private String examName;
    private Long courseId;
    private ExamTypeEnum examType;      // MAKEUP/RETAKE
    private Long semesterId;            // 考试所在学期
    private Long sourceSemesterId;      // 不及格成绩来源学期；为空则取 semesterId
    private LocalDate examDate;
    private LocalTime startTime;
    private Integer durationMinutes; // 考试时长（分钟），后端据此计算 endTime
    private Long localId;
    private String notes;
}
