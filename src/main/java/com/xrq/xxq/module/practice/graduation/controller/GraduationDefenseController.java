package com.xrq.xxq.module.practice.graduation.controller;

import java.util.List;

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.xrq.xxq.common.Result;
import com.xrq.xxq.module.practice.graduation.dto.DefenseArrangeRequest;
import com.xrq.xxq.module.practice.graduation.dto.DefenseResponse;
import com.xrq.xxq.module.practice.graduation.dto.ScoreConfirmRequest;
import com.xrq.xxq.module.practice.graduation.dto.ScoreResponse;
import com.xrq.xxq.module.practice.graduation.dto.ScoreSubmitRequest;
import com.xrq.xxq.module.practice.graduation.service.GraduationDefenseService;
import com.xrq.xxq.util.auth.AuthFacade;
import com.xrq.xxq.util.auth.AuthFacade.AuthContext;

import lombok.RequiredArgsConstructor;

/**
 * 答辩与成绩（阶段四）。
 */
@RestController
@RequestMapping("/api/practice/graduation/defense")
@RequiredArgsConstructor
public class GraduationDefenseController {

    private final GraduationDefenseService defenseService;
    private final AuthFacade authFacade;

    /** 院系安排/更新答辩（R-9.1，门禁：查重通过） */
    @PostMapping("/arrange")
    public Result<DefenseResponse> arrange(HttpServletRequest request, @RequestBody DefenseArrangeRequest body) {
        Long deptUserId = authFacade.requireDepartmentUserId(request);
        return Result.ok(defenseService.arrangeDefense(deptUserId, body));
    }

    /** 答辩安排列表（教务全部/院系本院系/学生本人） */
    @GetMapping("/list")
    public Result<List<DefenseResponse>> list(HttpServletRequest request, @RequestParam Long campaignId) {
        AuthContext ctx = authFacade.requireUserTypesContext(request,
                AuthFacade.USER_TYPE_ACADEMIC_ADMIN, AuthFacade.USER_TYPE_DEPARTMENT,
                AuthFacade.USER_TYPE_STUDENT);
        return Result.ok(defenseService.listDefenses(campaignId, ctx.userType(), ctx.userId()));
    }

    /** 指导教师录入指导分（R-9.2/R-9.3） */
    @PostMapping("/scores/advisor")
    public Result<ScoreResponse> advisorScore(HttpServletRequest request, @RequestBody ScoreSubmitRequest body) {
        Long teacherUserId = authFacade.requireUserTypesUserId(request, AuthFacade.USER_TYPE_TEACHER);
        return Result.ok(defenseService.submitAdvisorScore(teacherUserId, body));
    }

    /** 评阅教师录入评阅分 */
    @PostMapping("/scores/reviewer")
    public Result<ScoreResponse> reviewerScore(HttpServletRequest request, @RequestBody ScoreSubmitRequest body) {
        Long reviewerUserId = authFacade.requireUserTypesUserId(request, AuthFacade.USER_TYPE_TEACHER);
        return Result.ok(defenseService.submitReviewerScore(reviewerUserId, body));
    }

    /** 院系/教务录入答辩分 */
    @PostMapping("/scores/defense")
    public Result<ScoreResponse> defenseScore(HttpServletRequest request, @RequestBody ScoreSubmitRequest body) {
        AuthContext ctx = authFacade.requireUserTypesContext(request,
                AuthFacade.USER_TYPE_DEPARTMENT, AuthFacade.USER_TYPE_ACADEMIC_ADMIN);
        return Result.ok(defenseService.submitDefenseScore(ctx.userId(), ctx.userType(), body));
    }

    /** 院系确认并发布总评成绩（R-9.3） */
    @PostMapping("/scores/confirm")
    public Result<ScoreResponse> confirm(HttpServletRequest request, @RequestBody ScoreConfirmRequest body) {
        Long deptUserId = authFacade.requireDepartmentUserId(request);
        return Result.ok(defenseService.confirmScore(deptUserId, body));
    }

    /** 成绩列表（教务全部/院系本院系/学生本人/教师名下） */
    @GetMapping("/scores")
    public Result<List<ScoreResponse>> scores(HttpServletRequest request, @RequestParam Long campaignId) {
        AuthContext ctx = authFacade.requireUserTypesContext(request,
                AuthFacade.USER_TYPE_ACADEMIC_ADMIN, AuthFacade.USER_TYPE_DEPARTMENT,
                AuthFacade.USER_TYPE_STUDENT, AuthFacade.USER_TYPE_TEACHER);
        return Result.ok(defenseService.listScores(campaignId, ctx.userType(), ctx.userId()));
    }

    /** 学生查看本人成绩 */
    @GetMapping("/scores/my")
    public Result<ScoreResponse> myScore(HttpServletRequest request, @RequestParam Long campaignId) {
        Long studentUserId = authFacade.requireStudentUserId(request);
        return Result.ok(defenseService.getMyScore(studentUserId, campaignId));
    }

    /** 教务导出成绩总表（R-9.4，复用导出能力） */
    @GetMapping("/scores/export")
    public ResponseEntity<byte[]> exportScores(HttpServletRequest request, @RequestParam Long campaignId)
            throws java.io.IOException {
        Long academicUserId = authFacade.requireAcademicAdminUserId(request);
        var file = defenseService.exportScores(academicUserId, campaignId);
        String encoded = java.net.URLEncoder.encode(file.fileName(), java.nio.charset.StandardCharsets.UTF_8)
                .replace("+", "%20");
        return ResponseEntity.ok()
                .header(org.springframework.http.HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename*=UTF-8''" + encoded)
                .contentType(MediaType.parseMediaType(
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(file.data());
    }
}
