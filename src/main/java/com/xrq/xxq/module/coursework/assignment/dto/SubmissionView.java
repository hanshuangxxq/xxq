package com.xrq.xxq.module.coursework.assignment.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import com.xrq.xxq.module.coursework.assignment.entity.SubmissionStatusEnum;
import lombok.Data;

/** 学生视角的提交详情。 */
@Data
public class SubmissionView {

    private Long id;
    private Long assignmentId;
    private LocalDateTime submitTime;
    private Integer late;
    private SubmissionStatusEnum status;
    private BigDecimal score;
    /** 客观题自动得分合计。 */
    private BigDecimal autoScore;
    private String comment;
    private Integer version;
    /** 逐题作答（my-submission/提交响应/批改响应携带）。 */
    private List<AnswerView> answers;
}
