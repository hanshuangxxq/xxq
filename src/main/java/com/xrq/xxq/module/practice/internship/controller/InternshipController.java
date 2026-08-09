package com.xrq.xxq.module.practice.internship.controller;

import java.util.List;

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.xrq.xxq.common.PageQuery;
import com.xrq.xxq.common.PageResult;
import com.xrq.xxq.common.Result;
import com.xrq.xxq.module.practice.internship.dto.InternshipApplicationResponse;
import com.xrq.xxq.module.practice.internship.dto.InternshipApplyRequest;
import com.xrq.xxq.module.practice.internship.dto.InternshipCreateRequest;
import com.xrq.xxq.module.practice.internship.dto.InternshipResponse;
import com.xrq.xxq.module.practice.internship.dto.InternshipReviewRequest;
import com.xrq.xxq.module.practice.internship.dto.InternshipUpdateRequest;
import com.xrq.xxq.module.practice.internship.entity.InternshipStatusEnum;
import com.xrq.xxq.module.practice.internship.service.InternshipService;
import com.xrq.xxq.util.auth.AuthFacade;

import lombok.RequiredArgsConstructor;

/**
 * 实习项目接口。
 * <p>
 * 发布/更新/状态/删除/审核：院系管理者（负责本人）或教务；报名/撤销/我的报名：学生。
 */
@RestController
@RequestMapping("/api/practice/internships")
@RequiredArgsConstructor
public class InternshipController {

    private final InternshipService internshipService;
    private final AuthFacade authFacade;

    @PostMapping
    public Result<InternshipResponse> create(HttpServletRequest request, @RequestBody InternshipCreateRequest body) {
        AuthFacade.AuthContext ctx = authFacade.requireUserTypesContext(request,
                AuthFacade.USER_TYPE_DEPARTMENT);
        return Result.ok(internshipService.createInternship(ctx.userId(), ctx.userType(), body));
    }

    @PutMapping("/{id}")
    public Result<InternshipResponse> update(HttpServletRequest request, @PathVariable Long id,
                                             @RequestBody InternshipUpdateRequest body) {
        AuthFacade.AuthContext ctx = authFacade.requireUserTypesContext(request,
                AuthFacade.USER_TYPE_DEPARTMENT, AuthFacade.USER_TYPE_ACADEMIC_ADMIN);
        return Result.ok(internshipService.updateInternship(id, body, ctx.userId(), ctx.userType()));
    }

    @PutMapping("/{id}/status")
    public Result<Void> changeStatus(HttpServletRequest request, @PathVariable Long id,
                                     @RequestParam InternshipStatusEnum status) {
        AuthFacade.AuthContext ctx = authFacade.requireUserTypesContext(request,
                AuthFacade.USER_TYPE_DEPARTMENT, AuthFacade.USER_TYPE_ACADEMIC_ADMIN);
        internshipService.changeInternshipStatus(id, status, ctx.userId(), ctx.userType());
        return Result.ok();
    }

    @GetMapping
    public Result<PageResult<InternshipResponse>> list(HttpServletRequest request,
                                                       @RequestParam(required = false) Long supervisorId,
                                                       @RequestParam(required = false) InternshipStatusEnum status,
                                                       @RequestParam(required = false) Integer page,
                                                       @RequestParam(required = false) Integer pageSize) {
        AuthFacade.AuthContext ctx = authFacade.requireUserTypesContext(request,
                AuthFacade.USER_TYPE_DEPARTMENT, AuthFacade.USER_TYPE_ACADEMIC_ADMIN);
        return Result.ok(internshipService.listInternships(ctx.userId(), ctx.userType(),
                supervisorId, status, new PageQuery(page, pageSize)));
    }

    @GetMapping("/{id}")
    public Result<InternshipResponse> get(@PathVariable Long id) {
        return Result.ok(internshipService.getInternship(id));
    }

    @GetMapping("/available")
    public Result<List<InternshipResponse>> listAvailable(HttpServletRequest request) {
        Long studentUserId = authFacade.requireStudentUserId(request);
        return Result.ok(internshipService.listAvailableInternships(studentUserId));
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(HttpServletRequest request, @PathVariable Long id) {
        AuthFacade.AuthContext ctx = authFacade.requireUserTypesContext(request,
                AuthFacade.USER_TYPE_DEPARTMENT, AuthFacade.USER_TYPE_ACADEMIC_ADMIN);
        internshipService.deleteInternship(id, ctx.userId(), ctx.userType());
        return Result.ok();
    }

    @PostMapping("/applications")
    public Result<InternshipApplicationResponse> apply(HttpServletRequest request,
                                                       @RequestBody InternshipApplyRequest body) {
        Long studentUserId = authFacade.requireStudentUserId(request);
        return Result.ok(internshipService.applyInternship(studentUserId, body));
    }

    @DeleteMapping("/applications/{id}")
    public Result<Void> cancel(HttpServletRequest request, @PathVariable Long id) {
        Long studentUserId = authFacade.requireStudentUserId(request);
        internshipService.cancelApplication(studentUserId, id);
        return Result.ok();
    }

    @PostMapping("/applications/{id}/review")
    public Result<InternshipApplicationResponse> review(HttpServletRequest request, @PathVariable Long id,
                                                        @RequestBody InternshipReviewRequest body) {
        AuthFacade.AuthContext ctx = authFacade.requireUserTypesContext(request,
                AuthFacade.USER_TYPE_DEPARTMENT, AuthFacade.USER_TYPE_ACADEMIC_ADMIN);
        return Result.ok(internshipService.reviewApplication(id, body, ctx.userId(), ctx.userType()));
    }

    @GetMapping("/applications/my")
    public Result<List<InternshipApplicationResponse>> myApplications(HttpServletRequest request) {
        Long studentUserId = authFacade.requireStudentUserId(request);
        return Result.ok(internshipService.listMyApplications(studentUserId));
    }

    @GetMapping("/{internshipId}/applications")
    public Result<PageResult<InternshipApplicationResponse>> applicationsByInternship(HttpServletRequest request,
                                                                                      @PathVariable Long internshipId,
                                                                                      @RequestParam(required = false) Integer page,
                                                                                      @RequestParam(required = false) Integer pageSize) {
        AuthFacade.AuthContext ctx = authFacade.requireUserTypesContext(request,
                AuthFacade.USER_TYPE_DEPARTMENT, AuthFacade.USER_TYPE_ACADEMIC_ADMIN);
        return Result.ok(internshipService.listApplicationsByInternship(internshipId, ctx.userId(), ctx.userType(),
                new PageQuery(page, pageSize)));
    }
}
