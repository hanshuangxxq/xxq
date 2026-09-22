package com.xrq.xxq.module.coursework.assignment.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import com.xrq.xxq.module.coursework.assignment.entity.AnswerVisibleEnum;
import lombok.Data;
import org.jspecify.annotations.NonNull;

/** 作业创建/修改请求（multipart 的 data 部分；附件经 filePath 或 file 部分携带）。 */
@Data
public class AssignmentSaveRequest {

    /** 授课安排 id（接受组内任意行，服务端归一到锚点）。创建必填，修改忽略。 */
    private Long teachInfoId;

    @NonNull
    private String title;

    private String content;

    /** 分片产物路径（与 multipart file 二选一）。 */
    private String filePath;

    /** 附件展示名（可空）。 */
    private String fileOriginal;

    @NonNull
    private LocalDateTime deadline;

    /** @deprecated 总分由题目配分求和派生，前端传值被忽略。 */
    private BigDecimal totalScore;

    /** true=创建后直接发布；修改时忽略。 */
    private Boolean publish;

    /** 答案可见性（默认 SUBMIT）。 */
    private AnswerVisibleEnum answerVisible;

    /** 从题库抽题（与 newQuestions 合并为完整题目集；顺序 = 本列表序 + newQuestions 序）。 */
    private List<QuestionRefInput> questionIds;

    /** 直接录入的题目（saveToBank 默认 true）。 */
    private List<QuestionInput> newQuestions;
}
