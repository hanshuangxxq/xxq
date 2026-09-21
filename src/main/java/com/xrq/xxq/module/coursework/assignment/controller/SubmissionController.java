package com.xrq.xxq.module.coursework.assignment.controller;

import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.xrq.xxq.common.Result;
import com.xrq.xxq.module.coursework.assignment.dto.GradeRequest;
import com.xrq.xxq.module.coursework.assignment.dto.SubmissionView;
import com.xrq.xxq.module.coursework.assignment.service.SubmissionService;
import com.xrq.xxq.util.auth.AuthFacade;
import com.xrq.xxq.util.auth.RequireTeacher;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;

/** 作业提交批改端点（名单在 AssignmentController，此处只做批改）。 */
@RestController
@RequestMapping("/api/coursework/submissions")
@RequiredArgsConstructor
public class SubmissionController {

    private final SubmissionService submissionService;
    private final AuthFacade authFacade;

    /** 批改提交（打分 + 评语）。 */
    @PostMapping("/{id}/grade")
    @RequireTeacher
    public Result<SubmissionView> grade(HttpServletRequest request, @PathVariable Long id,
                                        @RequestBody GradeRequest body) {
        return Result.ok(submissionService.grade(authFacade.currentUserId(request), id, body));
    }
}
