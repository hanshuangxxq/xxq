package com.xrq.xxq.module.practice.graduation.controller;

import java.util.List;

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.xrq.xxq.common.Result;
import com.xrq.xxq.module.practice.common.FileView;
import com.xrq.xxq.module.practice.graduation.dto.DefenseArrangeRequest;
import com.xrq.xxq.module.practice.graduation.dto.DefenseResponse;
import com.xrq.xxq.module.practice.graduation.dto.ScoreConfirmRequest;
import com.xrq.xxq.module.practice.graduation.dto.ScoreResponse;
import com.xrq.xxq.module.practice.graduation.dto.ScoreSubmitRequest;
import com.xrq.xxq.module.practice.graduation.service.GraduationDefenseService;
import com.xrq.xxq.util.auth.AuthFacade;
import com.xrq.xxq.util.auth.RequireAcademicAdmin;
import com.xrq.xxq.util.auth.RequireAuth;
import com.xrq.xxq.util.auth.RequireDepartment;
import com.xrq.xxq.util.auth.RequireManagement;
import com.xrq.xxq.util.auth.RequireStudent;
import com.xrq.xxq.util.auth.RequireTeacher;
import com.xrq.xxq.util.auth.UserType;
import com.xrq.xxq.util.file.ResumableFileResponse;

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
    @RequireDepartment
    @PostMapping("/arrange")
    public Result<DefenseResponse> arrange(HttpServletRequest request, @RequestBody DefenseArrangeRequest body) {
        Long deptUserId = authFacade.currentUserId(request);
        return Result.ok(defenseService.arrangeDefense(deptUserId, body));
    }

    /** 答辩安排列表（教务全部/院系本院系/学生本人） */
    @RequireAuth({UserType.ACADEMIC_ADMIN, UserType.DEPARTMENT, UserType.STUDENT})
    @GetMapping("/list")
    public Result<List<DefenseResponse>> list(HttpServletRequest request, @RequestParam Long campaignId) {
        Long userId = authFacade.currentUserId(request);
        String userType = authFacade.currentUserType(request);
        return Result.ok(defenseService.listDefenses(campaignId, userType, userId));
    }

    /** 院系/教务上传答辩材料（重复上传为替换；file 整传 与 filePath 分片产物 二选一，必填其一） */
    @RequireManagement
    @PostMapping(value = "/{id:\\d+}/material", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Result<DefenseResponse> uploadMaterial(HttpServletRequest request, @PathVariable Long id,
                                                  @RequestParam(required = false) String filePath,
                                                  @RequestParam(required = false) String fileOriginal,
                                                  @RequestPart(value = "file", required = false) MultipartFile file) {
        Long userId = authFacade.currentUserId(request);
        String userType = authFacade.currentUserType(request);
        return Result.ok(defenseService.uploadMaterial(userId, userType, id, filePath, fileOriginal, file));
    }

    /** 答辩材料下载（学生本人/院系/教务） */
    @RequireAuth({UserType.STUDENT, UserType.DEPARTMENT, UserType.ACADEMIC_ADMIN})
    @GetMapping("/{id:\\d+}/material/download")
    public ResponseEntity<Resource> downloadMaterial(HttpServletRequest request, @PathVariable Long id) {
        Long userId = authFacade.currentUserId(request);
        String userType = authFacade.currentUserType(request);
        FileView view = defenseService.resolveMaterialFile(userType, userId, id);
        return ResumableFileResponse.buildDownload(view.path(), view.originalName(),
                ResumableFileResponse.sha256FromFileName(view.path().getFileName().toString()));
    }

    /** 指导教师录入指导分（R-9.2/R-9.3） */
    @RequireTeacher
    @PostMapping("/scores/advisor")
    public Result<ScoreResponse> advisorScore(HttpServletRequest request, @RequestBody ScoreSubmitRequest body) {
        Long teacherUserId = authFacade.currentUserId(request);
        return Result.ok(defenseService.submitAdvisorScore(teacherUserId, body));
    }

    /** 指导评分录入列表：教师名下学生 */
    @RequireTeacher
    @GetMapping("/scores/advisor")
    public Result<List<ScoreResponse>> advisorScoreEntries(HttpServletRequest request, @RequestParam Long campaignId) {
        Long teacherUserId = authFacade.currentUserId(request);
        return Result.ok(defenseService.listAdvisorScoreEntries(teacherUserId, campaignId));
    }

    /** 评阅教师录入评阅分 */
    @RequireTeacher
    @PostMapping("/scores/reviewer")
    public Result<ScoreResponse> reviewerScore(HttpServletRequest request, @RequestBody ScoreSubmitRequest body) {
        Long reviewerUserId = authFacade.currentUserId(request);
        return Result.ok(defenseService.submitReviewerScore(reviewerUserId, body));
    }

    /** 评阅评分录入列表：本人为评阅人的学生 */
    @RequireTeacher
    @GetMapping("/scores/reviewer")
    public Result<List<ScoreResponse>> reviewerScoreEntries(HttpServletRequest request, @RequestParam Long campaignId) {
        Long reviewerUserId = authFacade.currentUserId(request);
        return Result.ok(defenseService.listReviewerScoreEntries(reviewerUserId, campaignId));
    }

    /** 院系/教务录入答辩分 */
    @RequireManagement
    @PostMapping("/scores/defense")
    public Result<ScoreResponse> defenseScore(HttpServletRequest request, @RequestBody ScoreSubmitRequest body) {
        Long userId = authFacade.currentUserId(request);
        String userType = authFacade.currentUserType(request);
        return Result.ok(defenseService.submitDefenseScore(userId, userType, body));
    }

    /** 院系确认并发布总评成绩（R-9.3） */
    @RequireDepartment
    @PostMapping("/scores/confirm")
    public Result<ScoreResponse> confirm(HttpServletRequest request, @RequestBody ScoreConfirmRequest body) {
        Long deptUserId = authFacade.currentUserId(request);
        return Result.ok(defenseService.confirmScore(deptUserId, body));
    }

    /** 成绩列表（教务全部/院系本院系/学生本人/教师名下） */
    @RequireAuth({UserType.ACADEMIC_ADMIN, UserType.DEPARTMENT, UserType.STUDENT, UserType.TEACHER})
    @GetMapping("/scores")
    public Result<List<ScoreResponse>> scores(HttpServletRequest request, @RequestParam Long campaignId) {
        Long userId = authFacade.currentUserId(request);
        String userType = authFacade.currentUserType(request);
        return Result.ok(defenseService.listScores(campaignId, userType, userId));
    }

    /** 学生查看本人成绩 */
    @RequireStudent
    @GetMapping("/scores/my")
    public Result<ScoreResponse> myScore(HttpServletRequest request, @RequestParam Long campaignId) {
        Long studentUserId = authFacade.currentUserId(request);
        return Result.ok(defenseService.getMyScore(studentUserId, campaignId));
    }

    /** 教务导出成绩总表（R-9.4，复用导出能力） */
    @RequireAcademicAdmin
    @GetMapping("/scores/export")
    public ResponseEntity<Resource> exportScores(HttpServletRequest request, @RequestParam Long campaignId) {
        Long academicUserId = authFacade.currentUserId(request);
        var file = defenseService.exportScores(academicUserId, campaignId);
        return ResumableFileResponse.buildDownload(file.data(), file.fileName());
    }
}
