package com.xrq.xxq.module.coursework.assignment.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import com.xrq.xxq.module.coursework.assignment.entity.SubmissionStatusEnum;
import lombok.Data;

/** 教师视角单份提交详情：含学生信息与全部逐题作答（恒含标准答案）。 */
@Data
public class TeacherSubmissionView {

    private Long id;
    private Long assignmentId;
    private Long studentUserId;
    private String studentName;
    private String studentNo;
    private LocalDateTime submitTime;
    private Integer late;
    private SubmissionStatusEnum status;
    private BigDecimal score;
    private BigDecimal autoScore;
    private String comment;
    private Integer version;
    private List<AnswerView> answers;
}
