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
import com.xrq.xxq.util.auth.RequireAuth;
import com.xrq.xxq.util.auth.UserType;

import lombok.RequiredArgsConstructor;

/**
 * 培训课程接口。
 * <p>
 * 发布：院系管理者；更新/状态/删除：院系管理者（本人）或教务；报名/退课/我的报名：学生（即报即生效）。
 */
@RestController
@RequestMapping("/api/practice/trainings")
@RequiredArgsConstructor
public class TrainingController {

    private final TrainingService trainingService;
    private final AuthFacade authFacade;

    @PostMapping
    @RequireAuth(UserType.DEPARTMENT)
    public Result<TrainingResponse> create(HttpServletRequest request, @RequestBody TrainingCreateRequest body) {
        Long userId = authFacade.currentUserId(request);
        String userType = authFacade.currentUserType(request);
        return Result.ok(trainingService.createCourse(userId, userType, body));
    }

    @PutMapping("/{id}")
    @RequireAuth({UserType.DEPARTMENT, UserType.ACADEMIC_ADMIN})
    public Result<TrainingResponse> update(HttpServletRequest request, @PathVariable Long id,
                                           @RequestBody TrainingUpdateRequest body) {
        Long userId = authFacade.currentUserId(request);
        String userType = authFacade.currentUserType(request);
        return Result.ok(trainingService.updateCourse(id, body, userId, userType));
    }

    @PutMapping("/{id}/status")
    @RequireAuth({UserType.DEPARTMENT, UserType.ACADEMIC_ADMIN})
    public Result<Void> changeStatus(HttpServletRequest request, @PathVariable Long id,
                                     @RequestParam TrainingStatusEnum status) {
        Long userId = authFacade.currentUserId(request);
        String userType = authFacade.currentUserType(request);
        trainingService.changeCourseStatus(id, status, userId, userType);
        return Result.ok();
    }

    @GetMapping
    @RequireAuth({UserType.DEPARTMENT, UserType.ACADEMIC_ADMIN})
    public Result<PageResult<TrainingResponse>> list(HttpServletRequest request,
                                                     @RequestParam(required = false) Long teacherId,
                                                     @RequestParam(required = false) TrainingStatusEnum status,
                                                     @RequestParam(required = false) Integer page,
                                                     @RequestParam(required = false) Integer pageSize) {
        Long userId = authFacade.currentUserId(request);
        String userType = authFacade.currentUserType(request);
        return Result.ok(trainingService.listCourses(userId, userType,
                teacherId, status, new PageQuery(page, pageSize)));
    }

    @GetMapping("/{id}")
    @RequireAuth()
    public Result<TrainingResponse> get(@PathVariable Long id) {
        return Result.ok(trainingService.getCourse(id));
    }

    @GetMapping("/available")
    @RequireAuth(UserType.STUDENT)
    public Result<List<TrainingResponse>> listAvailable(HttpServletRequest request) {
        Long studentUserId = authFacade.currentUserId(request);
        return Result.ok(trainingService.listAvailableCourses(studentUserId));
    }

    @DeleteMapping("/{id}")
    @RequireAuth({UserType.DEPARTMENT, UserType.ACADEMIC_ADMIN})
    public Result<Void> delete(HttpServletRequest request, @PathVariable Long id) {
        Long userId = authFacade.currentUserId(request);
        String userType = authFacade.currentUserType(request);
        trainingService.deleteCourse(id, userId, userType);
        return Result.ok();
    }

    @PostMapping("/{courseId}/enrollments")
    @RequireAuth(UserType.STUDENT)
    public Result<TrainingEnrollmentResponse> enroll(HttpServletRequest request, @PathVariable Long courseId) {
        Long studentUserId = authFacade.currentUserId(request);
        return Result.ok(trainingService.enroll(studentUserId, courseId));
    }

    @DeleteMapping("/enrollments/{id}")
    @RequireAuth(UserType.STUDENT)
    public Result<Void> cancelEnroll(HttpServletRequest request, @PathVariable Long id) {
        Long studentUserId = authFacade.currentUserId(request);
        trainingService.cancelEnroll(studentUserId, id);
        return Result.ok();
    }

    @GetMapping("/enrollments/my")
    @RequireAuth(UserType.STUDENT)
    public Result<List<TrainingEnrollmentResponse>> myEnrollments(HttpServletRequest request) {
        Long studentUserId = authFacade.currentUserId(request);
        return Result.ok(trainingService.listMyEnrollments(studentUserId));
    }

    @GetMapping("/{courseId}/enrollments")
    @RequireAuth({UserType.DEPARTMENT, UserType.ACADEMIC_ADMIN})
    public Result<PageResult<TrainingEnrollmentResponse>> enrollmentsByCourse(HttpServletRequest request,
                                                                              @PathVariable Long courseId,
                                                                              @RequestParam(required = false) Integer page,
                                                                              @RequestParam(required = false) Integer pageSize) {
        Long userId = authFacade.currentUserId(request);
        String userType = authFacade.currentUserType(request);
        return Result.ok(trainingService.listEnrollmentsByCourse(courseId, userId, userType,
                new PageQuery(page, pageSize)));
    }
}
