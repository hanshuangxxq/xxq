package com.xrq.xxq.module.coursework.assignment.service;

import java.util.List;

import org.springframework.web.multipart.MultipartFile;

import com.xrq.xxq.module.coursework.assignment.dto.GradeRequest;
import com.xrq.xxq.module.coursework.assignment.dto.SubmissionRowView;
import com.xrq.xxq.module.coursework.assignment.dto.SubmissionView;
import com.xrq.xxq.module.coursework.assignment.dto.SubmitRequest;

/**
 * 作业提交服务：学生提交/重交（upsert + 迟交标记）、我的提交、教师名单、批改。
 */
public interface SubmissionService {

    /** 提交/重交：文本与附件至少一项；截止后允许迟交（late=1），CLOSED 拒绝。 */
    SubmissionView submit(Long studentUserId, Long assignmentId, SubmitRequest req, MultipartFile file);

    /** 我的提交（未提交返回 null data）。 */
    SubmissionView mySubmission(Long studentUserId, Long assignmentId);

    /** 教师视角提交名单：花名册 LEFT JOIN 提交，含未交学生。 */
    List<SubmissionRowView> roster(Long teacherUserId, Long assignmentId);

    /** 批改：分数须在 [0, 作业满分]；批改完成通知学生。 */
    SubmissionView grade(Long teacherUserId, Long submissionId, GradeRequest req);
}
