package com.xrq.xxq.module.score.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import com.xrq.xxq.module.score.entity.ReviewStatusEnum;

import lombok.Data;

/**
 * 成绩复核返回视图（富化学生/课程/教师信息）。
 */
@Data
public class ReviewView {

    private Long id;
    private Long scoreId;
    private Long studentUserId;
    private String studentName;
    private String studentNo;
    private Long courseId;
    private String courseName;
    private Long teacherId;
    private String teacherName;
    private BigDecimal currentTotalScore; // 当前成绩总评（便于前端展示）
    private String reason;
    private ReviewStatusEnum status;
    private String teacherReply;
    private String adminReply;
    private LocalDateTime escalateTime;
    private LocalDateTime resolvedTime;
    private LocalDateTime createTime;
}
