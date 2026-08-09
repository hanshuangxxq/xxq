package com.xrq.xxq.module.practice.graduation.controller;

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
import com.xrq.xxq.module.practice.graduation.dto.SelectionApplyRequest;
import com.xrq.xxq.module.practice.graduation.dto.SelectionResponse;
import com.xrq.xxq.module.practice.graduation.dto.SelectionReviewRequest;
import com.xrq.xxq.module.practice.graduation.dto.TopicCreateRequest;
import com.xrq.xxq.module.practice.graduation.dto.TopicResponse;
import com.xrq.xxq.module.practice.graduation.dto.TopicUpdateRequest;
import com.xrq.xxq.module.practice.graduation.entity.TopicStatusEnum;
import com.xrq.xxq.module.practice.graduation.service.GraduationService;
import com.xrq.xxq.util.auth.AuthFacade;

import lombok.RequiredArgsConstructor;

/**
 * 毕业设计选题接口。
 * <p>
 * 发布/更新/状态/删除/审核：教师（本人）或教务；申请/撤销/我的申请：学生。
 */
@RestController
@RequestMapping("/api/practice/graduation/topics")
@RequiredArgsConstructor
public class GraduationController {

    private final GraduationService graduationService;
    private final AuthFacade authFacade;

    /** 教师发布选题。 */
    @PostMapping
    public Result<TopicResponse> create(HttpServletRequest request, @RequestBody TopicCreateRequest body) {
        Long teacherUserId = authFacade.requireUserTypesUserId(request, AuthFacade.USER_TYPE_TEACHER);
        return Result.ok(graduationService.createTopic(teacherUserId, body));
    }

    /** 更新选题（教师本人/教务）。 */
    @PutMapping("/{id}")
    public Result<TopicResponse> update(HttpServletRequest request, @PathVariable Long id,
                                        @RequestBody TopicUpdateRequest body) {
        AuthFacade.AuthContext ctx = authFacade.requireUserTypesContext(request,
                AuthFacade.USER_TYPE_TEACHER, AuthFacade.USER_TYPE_ACADEMIC_ADMIN);
        return Result.ok(graduationService.updateTopic(id, body, ctx.userId(), ctx.userType()));
    }

    /** 开放/关闭选题。 */
    @PutMapping("/{id}/status")
    public Result<Void> changeStatus(HttpServletRequest request, @PathVariable Long id,
                                     @RequestParam TopicStatusEnum status) {
        AuthFacade.AuthContext ctx = authFacade.requireUserTypesContext(request,
                AuthFacade.USER_TYPE_TEACHER, AuthFacade.USER_TYPE_ACADEMIC_ADMIN);
        graduationService.changeTopicStatus(id, status, ctx.userId(), ctx.userType());
        return Result.ok();
    }

    /** 选题列表（教师看自己 / 教务看全部，可按 teacherId/status 过滤）。 */
    @GetMapping
    public Result<PageResult<TopicResponse>> list(HttpServletRequest request,
                                                  @RequestParam(required = false) Long teacherId,
                                                  @RequestParam(required = false) TopicStatusEnum status,
                                                  @RequestParam(required = false) Integer page,
                                                  @RequestParam(required = false) Integer pageSize) {
        AuthFacade.AuthContext ctx = authFacade.requireUserTypesContext(request,
                AuthFacade.USER_TYPE_TEACHER, AuthFacade.USER_TYPE_ACADEMIC_ADMIN);
        return Result.ok(graduationService.listTopics(ctx.userId(), ctx.userType(),
                teacherId, status, new PageQuery(page, pageSize)));
    }

    /** 选题详情。 */
    @GetMapping("/{id}")
    public Result<TopicResponse> get(@PathVariable Long id) {
        return Result.ok(graduationService.getTopic(id));
    }

    /** 学生可选选题（OPEN 且未满）。 */
    @GetMapping("/available")
    public Result<List<TopicResponse>> listAvailable(HttpServletRequest request) {
        Long studentUserId = authFacade.requireStudentUserId(request);
        return Result.ok(graduationService.listAvailableTopics(studentUserId));
    }

    /** 删除选题（无活跃申请时）。 */
    @DeleteMapping("/{id}")
    public Result<Void> delete(HttpServletRequest request, @PathVariable Long id) {
        AuthFacade.AuthContext ctx = authFacade.requireUserTypesContext(request,
                AuthFacade.USER_TYPE_TEACHER, AuthFacade.USER_TYPE_ACADEMIC_ADMIN);
        graduationService.deleteTopic(id, ctx.userId(), ctx.userType());
        return Result.ok();
    }

    /** 学生申请选题。 */
    @PostMapping("/applications")
    public Result<SelectionResponse> apply(HttpServletRequest request, @RequestBody SelectionApplyRequest body) {
        Long studentUserId = authFacade.requireStudentUserId(request);
        return Result.ok(graduationService.applyTopic(studentUserId, body));
    }

    /** 学生撤销申请。 */
    @DeleteMapping("/applications/{id}")
    public Result<Void> cancel(HttpServletRequest request, @PathVariable Long id) {
        Long studentUserId = authFacade.requireStudentUserId(request);
        graduationService.cancelApplication(studentUserId, id);
        return Result.ok();
    }

    /** 审核申请（教师本人/教务）。 */
    @PostMapping("/applications/{id}/review")
    public Result<SelectionResponse> review(HttpServletRequest request, @PathVariable Long id,
                                            @RequestBody SelectionReviewRequest body) {
        AuthFacade.AuthContext ctx = authFacade.requireUserTypesContext(request,
                AuthFacade.USER_TYPE_TEACHER, AuthFacade.USER_TYPE_ACADEMIC_ADMIN);
        return Result.ok(graduationService.reviewApplication(id, body, ctx.userId(), ctx.userType()));
    }

    /** 学生查看我的申请。 */
    @GetMapping("/applications/my")
    public Result<List<SelectionResponse>> myApplications(HttpServletRequest request) {
        Long studentUserId = authFacade.requireStudentUserId(request);
        return Result.ok(graduationService.listMyApplications(studentUserId));
    }

    /** 选题下的申请列表（教师本人/教务）。 */
    @GetMapping("/{topicId}/applications")
    public Result<PageResult<SelectionResponse>> applicationsByTopic(HttpServletRequest request,
                                                                     @PathVariable Long topicId,
                                                                     @RequestParam(required = false) Integer page,
                                                                     @RequestParam(required = false) Integer pageSize) {
        AuthFacade.AuthContext ctx = authFacade.requireUserTypesContext(request,
                AuthFacade.USER_TYPE_TEACHER, AuthFacade.USER_TYPE_ACADEMIC_ADMIN);
        return Result.ok(graduationService.listApplicationsByTopic(topicId, ctx.userId(), ctx.userType(),
                new PageQuery(page, pageSize)));
    }
}
