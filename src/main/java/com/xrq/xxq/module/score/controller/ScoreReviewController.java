package com.xrq.xxq.module.score.controller;

import java.util.List;

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.xrq.xxq.common.PageQuery;
import com.xrq.xxq.common.PageResult;
import com.xrq.xxq.common.Result;
import com.xrq.xxq.module.score.dto.ReviewApplyRequest;
import com.xrq.xxq.module.score.dto.ReviewReplyRequest;
import com.xrq.xxq.module.score.dto.ReviewResolveRequest;
import com.xrq.xxq.module.score.dto.ReviewView;
import com.xrq.xxq.module.score.entity.ReviewStatusEnum;
import com.xrq.xxq.module.score.service.ScoreReviewService;
import com.xrq.xxq.util.auth.AuthFacade;

import lombok.RequiredArgsConstructor;

/**
 * 成绩复核接口。
 * <p>
 * 申请/升级：学生；回复：任课教师；终审：教务管理员。
 */
@RestController
@RequestMapping("/api/scores/reviews")
@RequiredArgsConstructor
public class ScoreReviewController {

    private final ScoreReviewService scoreReviewService;
    private final AuthFacade authFacade;

    /** 学生提交复核申请。 */
    @PostMapping
    public Result<ReviewView> apply(HttpServletRequest request,
                                    @RequestBody ReviewApplyRequest body) {
        Long studentUserId = authFacade.requireStudentUserId(request);
        return Result.ok(scoreReviewService.apply(body, studentUserId));
    }

    /** 学生查询自己的复核申请。 */
    @GetMapping("/my")
    public Result<List<ReviewView>> listMy(HttpServletRequest request) {
        Long studentUserId = authFacade.requireStudentUserId(request);
        return Result.ok(scoreReviewService.listMy(studentUserId));
    }

    /** 处理人查询待办（教师其课程 / 教务全部，可按状态过滤）。 */
    @GetMapping
    public Result<PageResult<ReviewView>> listForHandler(HttpServletRequest request,
                                                         @RequestParam(required = false) ReviewStatusEnum status,
                                                         @RequestParam(required = false) Integer page,
                                                         @RequestParam(required = false) Integer pageSize) {
        AuthFacade.AuthContext ctx = authFacade.requireUserTypesContext(request,
                AuthFacade.USER_TYPE_TEACHER, AuthFacade.USER_TYPE_ACADEMIC_ADMIN);
        return Result.ok(scoreReviewService.listForHandler(ctx.userId(), ctx.userType(), status, new PageQuery(page, pageSize)));
    }

    /** 教师回复复核申请（可调分）。 */
    @PostMapping("/{id}/reply")
    public Result<ReviewView> teacherReply(HttpServletRequest request,
                                           @PathVariable Long id,
                                           @RequestBody ReviewReplyRequest body) {
        AuthFacade.AuthContext ctx = authFacade.requireUserTypesContext(request, AuthFacade.USER_TYPE_TEACHER);
        return Result.ok(scoreReviewService.teacherReply(id, body, ctx.userId(), ctx.userType()));
    }

    /** 学生升级到教务。 */
    @PostMapping("/{id}/escalate")
    public Result<Void> escalate(HttpServletRequest request,
                                 @PathVariable Long id) {
        Long studentUserId = authFacade.requireStudentUserId(request);
        scoreReviewService.escalate(id, studentUserId);
        return Result.ok();
    }

    /** 教务终审（可调分并锁定成绩）。 */
    @PostMapping("/{id}/resolve")
    public Result<ReviewView> adminResolve(HttpServletRequest request,
                                           @PathVariable Long id,
                                           @RequestBody ReviewResolveRequest body) {
        Long adminUserId = authFacade.requireAcademicAdminUserId(request);
        return Result.ok(scoreReviewService.adminResolve(id, body, adminUserId));
    }
}
