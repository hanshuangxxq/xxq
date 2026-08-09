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
import com.xrq.xxq.module.practice.internship.dto.TrainingCreateRequest;
import com.xrq.xxq.module.practice.internship.dto.TrainingEnrollmentResponse;
import com.xrq.xxq.module.practice.internship.dto.TrainingResponse;
import com.xrq.xxq.module.practice.internship.dto.TrainingUpdateRequest;
import com.xrq.xxq.module.practice.internship.entity.TrainingStatusEnum;
import com.xrq.xxq.module.practice.internship.service.TrainingService;
import com.xrq.xxq.util.auth.AuthFacade;

import lombok.RequiredArgsConstructor;

/**
 * 培训课程接口。
 * <p>
 * 发布/更新/状态/删除：教师（本人）或教务；报名/退课/我的报名：学生（即报即生效）。
 */
@RestController
@RequestMapping("/api/practice/trainings")
@RequiredArgsConstructor
public class TrainingController {

    private final TrainingService trainingService;
    private final AuthFacade authFacade;

    @PostMapping
    public Result<TrainingResponse> create(HttpServletRequest request, @RequestBody TrainingCreateRequest body) {
        AuthFacade.AuthContext ctx = authFacade.requireUserTypesContext(request,
                AuthFacade.USER_TYPE_TEACHER, AuthFacade.USER_TYPE_ACADEMIC_ADMIN);
        return Result.ok(trainingService.createCourse(ctx.userId(), ctx.userType(), body));
    }

    @PutMapping("/{id}")
    public Result<TrainingResponse> update(HttpServletRequest request, @PathVariable Long id,
                                           @RequestBody TrainingUpdateRequest body) {
        AuthFacade.AuthContext ctx = authFacade.requireUserTypesContext(request,
                AuthFacade.USER_TYPE_TEACHER, AuthFacade.USER_TYPE_ACADEMIC_ADMIN);
        return Result.ok(trainingService.updateCourse(id, body, ctx.userId(), ctx.userType()));
    }

    @PutMapping("/{id}/status")
    public Result<Void> changeStatus(HttpServletRequest request, @PathVariable Long id,
                                     @RequestParam TrainingStatusEnum status) {
        AuthFacade.AuthContext ctx = authFacade.requireUserTypesContext(request,
                AuthFacade.USER_TYPE_TEACHER, AuthFacade.USER_TYPE_ACADEMIC_ADMIN);
        trainingService.changeCourseStatus(id, status, ctx.userId(), ctx.userType());
        return Result.ok();
    }

    @GetMapping
    public Result<PageResult<TrainingResponse>> list(HttpServletRequest request,
                                                     @RequestParam(required = false) Long teacherId,
                                                     @RequestParam(required = false) TrainingStatusEnum status,
                                                     @RequestParam(required = false) Integer page,
                                                     @RequestParam(required = false) Integer pageSize) {
        AuthFacade.AuthContext ctx = authFacade.requireUserTypesContext(request,
                AuthFacade.USER_TYPE_TEACHER, AuthFacade.USER_TYPE_ACADEMIC_ADMIN);
        return Result.ok(trainingService.listCourses(ctx.userId(), ctx.userType(),
                teacherId, status, new PageQuery(page, pageSize)));
    }

    @GetMapping("/{id}")
    public Result<TrainingResponse> get(@PathVariable Long id) {
        return Result.ok(trainingService.getCourse(id));
    }

    @GetMapping("/available")
    public Result<List<TrainingResponse>> listAvailable(HttpServletRequest request) {
        Long studentUserId = authFacade.requireStudentUserId(request);
        return Result.ok(trainingService.listAvailableCourses(studentUserId));
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(HttpServletRequest request, @PathVariable Long id) {
        AuthFacade.AuthContext ctx = authFacade.requireUserTypesContext(request,
                AuthFacade.USER_TYPE_TEACHER, AuthFacade.USER_TYPE_ACADEMIC_ADMIN);
        trainingService.deleteCourse(id, ctx.userId(), ctx.userType());
        return Result.ok();
    }

    @PostMapping("/{courseId}/enrollments")
    public Result<TrainingEnrollmentResponse> enroll(HttpServletRequest request, @PathVariable Long courseId) {
        Long studentUserId = authFacade.requireStudentUserId(request);
        return Result.ok(trainingService.enroll(studentUserId, courseId));
    }

    @DeleteMapping("/enrollments/{id}")
    public Result<Void> cancelEnroll(HttpServletRequest request, @PathVariable Long id) {
        Long studentUserId = authFacade.requireStudentUserId(request);
        trainingService.cancelEnroll(studentUserId, id);
        return Result.ok();
    }

    @GetMapping("/enrollments/my")
    public Result<List<TrainingEnrollmentResponse>> myEnrollments(HttpServletRequest request) {
        Long studentUserId = authFacade.requireStudentUserId(request);
        return Result.ok(trainingService.listMyEnrollments(studentUserId));
    }

    @GetMapping("/{courseId}/enrollments")
    public Result<PageResult<TrainingEnrollmentResponse>> enrollmentsByCourse(HttpServletRequest request,
                                                                              @PathVariable Long courseId,
                                                                              @RequestParam(required = false) Integer page,
                                                                              @RequestParam(required = false) Integer pageSize) {
        AuthFacade.AuthContext ctx = authFacade.requireUserTypesContext(request,
                AuthFacade.USER_TYPE_TEACHER, AuthFacade.USER_TYPE_ACADEMIC_ADMIN);
        return Result.ok(trainingService.listEnrollmentsByCourse(courseId, ctx.userId(), ctx.userType(),
                new PageQuery(page, pageSize)));
    }
}
