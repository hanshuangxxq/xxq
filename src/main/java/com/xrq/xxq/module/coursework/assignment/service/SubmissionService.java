package com.xrq.xxq.module.coursework.assignment.service;

import java.util.List;

import com.xrq.xxq.module.coursework.assignment.dto.DraftSaveRequest;
import com.xrq.xxq.module.coursework.assignment.dto.GradeRequest;
import com.xrq.xxq.module.coursework.assignment.dto.SubmissionRowView;
import com.xrq.xxq.module.coursework.assignment.dto.SubmissionView;
import com.xrq.xxq.module.coursework.assignment.dto.SubmitAnswersRequest;
import com.xrq.xxq.module.coursework.assignment.dto.TeacherSubmissionView;

/**
 * 作业提交服务：暂存（仅 Redis）、提交/重交（唯一 SQL 写入口）、我的提交、教师名单、逐题批改。
 */
public interface SubmissionService {

    /** 暂存草稿（仅 Redis）。CLOSED 拒绝；未发布按不存在处理。 */
    void saveDraft(Long studentUserId, Long assignmentId, DraftSaveRequest req);

    /** 提交/重交：客观题即时判分；截止后允许迟交（late=1），CLOSED 拒绝。 */
    SubmissionView submit(Long studentUserId, Long assignmentId, SubmitAnswersRequest req);

    /** 我的提交（未提交返回 null data）。标准答案按 answer_visible 门控。 */
    SubmissionView mySubmission(Long studentUserId, Long assignmentId);

    /** 教师视角提交名单：花名册 + 五态（GRADED/SUBMITTED/DRAFTING/VIEWED/NOT_VIEWED）。 */
    List<SubmissionRowView> roster(Long teacherUserId, Long assignmentId);

    /** 教师视角单份提交详情（恒含标准答案）。 */
    TeacherSubmissionView detailForTeacher(Long teacherUserId, Long submissionId);

    /** 逐题批改（仅大题；客观题不可改判）。全部判完 → GRADED + 通知。 */
    TeacherSubmissionView grade(Long teacherUserId, Long submissionId, GradeRequest req);
}
