package com.xrq.xxq.module.coursework.assignment.service;

import java.math.BigDecimal;
import java.util.List;

import com.xrq.xxq.module.coursework.assignment.dto.QuestionInput;
import com.xrq.xxq.module.coursework.assignment.dto.QuestionRefInput;
import com.xrq.xxq.module.coursework.assignment.entity.CourseAssignmentQuestion;

/** 作业题目快照：替换/查询/克隆/总分。 */
public interface AssignmentQuestionService {

    /** 按 sortOrder 升序的题目快照列表。 */
    List<CourseAssignmentQuestion> listQuestions(Long assignmentId);

    /**
     * 整体替换题目集（软删旧快照 + 重新快照）。
     * refs 在前、inputs 在后，sort_order 从 1 连续编号。
     * inputs 中 saveToBank!=false 的题同时入教师题库。
     */
    void replaceQuestions(Long assignmentId, Long teacherUserId,
                          List<QuestionRefInput> refs, List<QuestionInput> inputs);

    /** 克隆题目快照到新作业（保留 source_question_id 溯源）。 */
    void cloneQuestions(Long sourceAssignmentId, Long targetAssignmentId);

    /** 题目总分（无题 → 0）。 */
    BigDecimal totalScoreOf(Long assignmentId);
}
