package com.xrq.xxq.module.coursework.assignment.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import com.xrq.xxq.module.coursework.assignment.entity.SubmissionStatusEnum;
import lombok.Data;

/** 教师视角提交名单行：花名册 LEFT JOIN 提交，未交学生 submitted=false 其余提交字段为空。 */
@Data
public class SubmissionRowView {

    private Long submissionId;      // 未交为 null
    private Long studentUserId;
    private String studentName;
    private String studentNo;
    private Boolean submitted;
    private Integer late;
    private SubmissionStatusEnum status;
    private BigDecimal score;
    private String comment;
    private Integer version;
    private LocalDateTime submitTime;
}
