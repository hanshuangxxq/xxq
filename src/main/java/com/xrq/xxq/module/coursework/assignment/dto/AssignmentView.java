package com.xrq.xxq.module.coursework.assignment.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import com.xrq.xxq.module.coursework.assignment.entity.AnswerVisibleEnum;
import com.xrq.xxq.module.coursework.assignment.entity.AssignmentStatusEnum;
import lombok.Data;

/** 作业视图：教师视角带提交统计，学生视角带「我的提交」摘要。 */
@Data
public class AssignmentView {

    private Long id;
    private Long teachInfoId;
    private String title;
    private String content;
    private String fileName;
    private String fileOriginal;
    private LocalDateTime deadline;
    private BigDecimal totalScore;
    private AssignmentStatusEnum status;
    private Long teacherId;
    private LocalDateTime createTime;

    /** 答案可见性。 */
    private AnswerVisibleEnum answerVisible;

    // ---- 详情接口携带（list 不携带，避免 N+1） ----
    /** 去重题型 code 列表（前端按题型动态加载组件）。 */
    private List<String> questionTypes;

    /** 是否含大题（需要文件上传组件）。 */
    private Boolean hasFileQuestion;

    /** 题目列表（详情接口）。 */
    private List<AssignmentQuestionView> questions;

    // ---- 教师视角统计 ----
    private Integer submittedCount;
    private Integer gradedCount;

    // ---- 学生视角我的提交摘要 ----
    private Long mySubmissionId;
    private String mySubmissionStatus;
    private BigDecimal myScore;
    private Integer myLate;
    private Integer myVersion;
}
