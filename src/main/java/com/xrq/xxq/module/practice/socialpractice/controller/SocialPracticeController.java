package com.xrq.xxq.module.practice.socialpractice.controller;

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
import com.xrq.xxq.module.practice.socialpractice.dto.SocialPracticeApplicationResponse;
import com.xrq.xxq.module.practice.socialpractice.dto.SocialPracticeApplyRequest;
import com.xrq.xxq.module.practice.socialpractice.dto.SocialPracticeCreateRequest;
import com.xrq.xxq.module.practice.socialpractice.dto.SocialPracticeResponse;
import com.xrq.xxq.module.practice.socialpractice.dto.SocialPracticeReviewRequest;
import com.xrq.xxq.module.practice.socialpractice.dto.SocialPracticeUpdateRequest;
import com.xrq.xxq.module.practice.socialpractice.entity.SocialPracticeStatusEnum;
import com.xrq.xxq.module.practice.socialpractice.service.SocialPracticeService;
import com.xrq.xxq.util.auth.AuthFacade;
import com.xrq.xxq.util.auth.RequireAcademicAdmin;
import com.xrq.xxq.util.auth.RequireLogin;
import com.xrq.xxq.util.auth.RequireStudent;

import lombok.RequiredArgsConstructor;

/**
 * 社会实践项目接口。
 * <p>
 * 发布/更新/状态/删除/审核/查看全部：教务；申报/撤销/我的申报：学生。
 */
@RestController
@RequestMapping("/api/practice/social-practices")
@RequiredArgsConstructor
public class SocialPracticeController {

    private final SocialPracticeService socialPracticeService;
    private final AuthFacade authFacade;

    @PostMapping
    @RequireAcademicAdmin
    public Result<SocialPracticeResponse> create(HttpServletRequest request, @RequestBody SocialPracticeCreateRequest body) {
        return Result.ok(socialPracticeService.createPractice(body));
    }

    @PutMapping("/{id}")
    @RequireAcademicAdmin
    public Result<SocialPracticeResponse> update(HttpServletRequest request, @PathVariable Long id,
                                                 @RequestBody SocialPracticeUpdateRequest body) {
        return Result.ok(socialPracticeService.updatePractice(id, body));
    }

    @PutMapping("/{id}/status")
    @RequireAcademicAdmin
    public Result<Void> changeStatus(HttpServletRequest request, @PathVariable Long id,
                                     @RequestParam SocialPracticeStatusEnum status) {
        socialPracticeService.changePracticeStatus(id, status);
        return Result.ok();
    }

    @GetMapping
    @RequireAcademicAdmin
    public Result<PageResult<SocialPracticeResponse>> list(HttpServletRequest request,
                                                           @RequestParam(required = false) SocialPracticeStatusEnum status,
                                                           @RequestParam(required = false) Integer page,
                                                           @RequestParam(required = false) Integer pageSize) {
        return Result.ok(socialPracticeService.listPractices(status, new PageQuery(page, pageSize)));
    }

    @GetMapping("/{id}")
    @RequireLogin
    public Result<SocialPracticeResponse> get(@PathVariable Long id) {
        return Result.ok(socialPracticeService.getPractice(id));
    }

    @GetMapping("/available")
    @RequireStudent
    public Result<List<SocialPracticeResponse>> listAvailable(HttpServletRequest request) {
        Long studentUserId = authFacade.currentUserId(request);
        return Result.ok(socialPracticeService.listAvailablePractices(studentUserId));
    }

    @DeleteMapping("/{id}")
    @RequireAcademicAdmin
    public Result<Void> delete(HttpServletRequest request, @PathVariable Long id) {
        socialPracticeService.deletePractice(id);
        return Result.ok();
    }

    @PostMapping("/applications")
    @RequireStudent
    public Result<SocialPracticeApplicationResponse> apply(HttpServletRequest request,
                                                           @RequestBody SocialPracticeApplyRequest body) {
        Long studentUserId = authFacade.currentUserId(request);
        return Result.ok(socialPracticeService.apply(studentUserId, body));
    }

    @DeleteMapping("/applications/{id}")
    @RequireStudent
    public Result<Void> cancel(HttpServletRequest request, @PathVariable Long id) {
        Long studentUserId = authFacade.currentUserId(request);
        socialPracticeService.cancelApplication(studentUserId, id);
        return Result.ok();
    }

    @PostMapping("/applications/{id}/review")
    @RequireAcademicAdmin
    public Result<SocialPracticeApplicationResponse> review(HttpServletRequest request, @PathVariable Long id,
                                                           @RequestBody SocialPracticeReviewRequest body) {
        return Result.ok(socialPracticeService.reviewApplication(id, body));
    }

    @GetMapping("/applications/my")
    @RequireStudent
    public Result<List<SocialPracticeApplicationResponse>> myApplications(HttpServletRequest request) {
        Long studentUserId = authFacade.currentUserId(request);
        return Result.ok(socialPracticeService.listMyApplications(studentUserId));
    }

    @GetMapping("/{practiceId}/applications")
    @RequireAcademicAdmin
    public Result<PageResult<SocialPracticeApplicationResponse>> applicationsByPractice(HttpServletRequest request,
                                                                                       @PathVariable Long practiceId,
                                                                                       @RequestParam(required = false) Integer page,
                                                                                       @RequestParam(required = false) Integer pageSize) {
        return Result.ok(socialPracticeService.listApplicationsByPractice(practiceId, new PageQuery(page, pageSize)));
    }
}
