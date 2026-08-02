package com.xrq.xxq.module.exam.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

import com.xrq.xxq.module.exam.entity.ExamStatusEnum;
import com.xrq.xxq.module.exam.entity.ExamTypeEnum;

import lombok.Data;

/**
 * 考试返回视图（富化课程名与地点）。
 */
@Data
public class ExamView {

    private Long id;
    private String examName;
    private Long courseId;
    private String courseName;
    private Long teachInfoId;
    private String className;        // 排考班级（单班级）
    private ExamTypeEnum examType;
    private Long semesterId;
    private LocalDate examDate;
    private LocalTime startTime;
    private LocalTime endTime;
    private Long localId;
    private String localName;        // 教学楼 + 教室
    private String notes;
    private ExamStatusEnum status;
    private LocalDateTime createTime;
}
