package com.xrq.xxq.module.practice.graduation.controller;

import java.util.List;

import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.xrq.xxq.common.Result;
import com.xrq.xxq.module.practice.graduation.dto.AllocationRequest;
import com.xrq.xxq.module.practice.graduation.dto.AssignmentOverviewRow;
import com.xrq.xxq.module.practice.graduation.dto.AssignmentResponse;
import com.xrq.xxq.module.practice.graduation.dto.PickRequest;
import com.xrq.xxq.module.practice.graduation.dto.ReassignRequest;
import com.xrq.xxq.module.practice.graduation.dto.TeacherPickPoolRow;
import com.xrq.xxq.module.practice.graduation.service.GraduationAssignmentService;
import com.xrq.xxq.util.auth.AuthFacade;
import com.xrq.xxq.util.auth.RequireAuth;
import com.xrq.xxq.util.auth.UserType;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;

/**
 * 师生双选与分配（§6）。
 */
@RestController
@RequestMapping("/api/practice/graduation/assignments")
@RequiredArgsConstructor
public class GraduationAssignmentController {

    private final GraduationAssignmentService assignmentService;
    private final AuthFacade authFacade;

    /** 教师自由选择学生（R-6.1~R-6.5，先占先得） */
    @RequireAuth(UserType.TEACHER)
    @PostMapping("/picks")
    public Result<AssignmentResponse> pick(HttpServletRequest request, @RequestBody PickRequest body) {
        Long teacherUserId = authFacade.currentUserId(request);
        return Result.ok(assignmentService.pickStudent(teacherUserId, body));
    }

    /** 教师放弃自选（R-6.6，选题截止前） */
    @RequireAuth(UserType.TEACHER)
    @DeleteMapping("/picks/{id:\\d+}")
    public Result<Void> cancelPick(HttpServletRequest request, @PathVariable Long id) {
        Long teacherUserId = authFacade.currentUserId(request);
        assignmentService.cancelPick(teacherUserId, id);
        return Result.ok();
    }

    /** 院系指定分配（R-6.7~R-6.11，选题截止后开放） */
    @RequireAuth(UserType.DEPARTMENT)
    @PostMapping("/allocations")
    public Result<AssignmentResponse> allocate(HttpServletRequest request, @RequestBody AllocationRequest body) {
        Long deptUserId = authFacade.currentUserId(request);
        return Result.ok(assignmentService.allocateStudent(deptUserId, body));
    }

    /** 院系改派（R-6.13） */
    @RequireAuth(UserType.DEPARTMENT)
    @PostMapping("/reassigns")
    public Result<AssignmentResponse> reassign(HttpServletRequest request, @RequestBody ReassignRequest body) {
        Long deptUserId = authFacade.currentUserId(request);
        return Result.ok(assignmentService.reassignStudent(deptUserId, body));
    }

    /** 教师自选池（本活动参与年级 ∩ 本院系学生） */
    @RequireAuth(UserType.TEACHER)
    @GetMapping("/teacher/pool")
    public Result<List<TeacherPickPoolRow>> pickPool(HttpServletRequest request, @RequestParam Long campaignId) {
        Long teacherUserId = authFacade.currentUserId(request);
        return Result.ok(assignmentService.listTeacherPickPool(teacherUserId, campaignId));
    }

    /** 我的匹配（学生本人 / 教师名下学生） */
    @RequireAuth({UserType.STUDENT, UserType.TEACHER})
    @GetMapping("/my")
    public Result<List<AssignmentResponse>> my(HttpServletRequest request,
                                               @RequestParam(required = false) Long campaignId) {
        Long userId = authFacade.currentUserId(request);
        String userType = authFacade.currentUserType(request);
        return Result.ok(assignmentService.listMyAssignments(userId, userType, campaignId));
    }

    /** 教务分配总览（R-6.14：每教师已选/已指定/空缺） */
    @RequireAuth(UserType.ACADEMIC_ADMIN)
    @GetMapping("/overview")
    public Result<List<AssignmentOverviewRow>> overview(HttpServletRequest request,
                                                        @RequestParam Long campaignId) {
        Long academicUserId = authFacade.currentUserId(request);
        return Result.ok(assignmentService.listAssignmentOverview(academicUserId, campaignId));
    }

    /** 未分配学生清单（教务可传 collegeId 过滤，院系强制本院系，R-6.14） */
    @RequireAuth({UserType.ACADEMIC_ADMIN, UserType.DEPARTMENT})
    @GetMapping("/unassigned")
    public Result<List<Long>> unassigned(HttpServletRequest request, @RequestParam Long campaignId,
                                         @RequestParam(required = false) Long collegeId) {
        Long userId = authFacade.currentUserId(request);
        String userType = authFacade.currentUserType(request);
        return Result.ok(assignmentService.listUnassignedStudentIds(campaignId,
                userType, userId, collegeId));
    }
}
