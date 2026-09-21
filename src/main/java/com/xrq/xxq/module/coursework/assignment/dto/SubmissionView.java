package com.xrq.xxq.module.coursework.assignment.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import com.xrq.xxq.module.coursework.assignment.entity.SubmissionStatusEnum;
import lombok.Data;

/** 学生视角的提交详情。 */
@Data
public class SubmissionView {

    private Long id;
    private Long assignmentId;
    private String content;
    private String fileName;
    private String fileOriginal;
    private LocalDateTime submitTime;
    private Integer late;
    private SubmissionStatusEnum status;
    private BigDecimal score;
    private String comment;
    private Integer version;
}
