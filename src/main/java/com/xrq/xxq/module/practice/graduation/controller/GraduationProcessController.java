package com.xrq.xxq.module.practice.graduation.controller;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.xrq.xxq.common.Result;
import com.xrq.xxq.module.practice.common.PracticeFileService;
import com.xrq.xxq.module.practice.graduation.dto.GuidanceLogCreateRequest;
import com.xrq.xxq.module.practice.graduation.dto.GuidanceLogResponse;
import com.xrq.xxq.module.practice.graduation.dto.MidtermResponse;
import com.xrq.xxq.module.practice.graduation.dto.MidtermReviewRequest;
import com.xrq.xxq.module.practice.graduation.dto.MidtermSubmitRequest;
import com.xrq.xxq.module.practice.graduation.dto.OpeningReportResponse;
import com.xrq.xxq.module.practice.graduation.dto.OpeningReportReviewRequest;
import com.xrq.xxq.module.practice.graduation.dto.OpeningReportSubmitRequest;
import com.xrq.xxq.module.practice.graduation.service.GraduationProcessService;
import com.xrq.xxq.module.practice.graduation.service.GraduationProcessService.FileView;
import com.xrq.xxq.util.auth.AuthFacade;
import com.xrq.xxq.util.auth.RequireAuth;
import com.xrq.xxq.util.auth.UserType;

import lombok.RequiredArgsConstructor;

/**
 * 过程管理（阶段二：开题报告 / 中期检查 / 过程指导记录）。
 */
@RestController
@RequestMapping("/api/practice/graduation/process")
@RequiredArgsConstructor
public class GraduationProcessController {

    private final GraduationProcessService processService;
    private final PracticeFileService fileService;
    private final AuthFacade authFacade;

    // ==================== 开题报告 ====================

    /** 学生提交/重提开题报告（R-7.1，附件可选） */
    @RequireAuth(UserType.STUDENT)
    @PostMapping(value = "/opening-reports", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Result<OpeningReportResponse> submitOpening(HttpServletRequest request,
                                                       @RequestPart("data") OpeningReportSubmitRequest body,
                                                       @RequestPart(value = "file", required = false) MultipartFile file) {
        Long studentUserId = authFacade.currentUserId(request);
        return Result.ok(processService.submitOpeningReport(studentUserId, body, file));
    }

    /** 指导教师审核开题报告（R-7.2） */
    @RequireAuth(UserType.TEACHER)
    @PutMapping("/opening-reports/{id:\\d+}/review")
    public Result<OpeningReportResponse> reviewOpening(HttpServletRequest request, @PathVariable Long id,
                                                       @RequestBody OpeningReportReviewRequest body) {
        Long teacherUserId = authFacade.currentUserId(request);
        return Result.ok(processService.reviewOpeningReport(teacherUserId, id, body));
    }

    /** 学生查看我的开题报告 */
    @RequireAuth(UserType.STUDENT)
    @GetMapping("/opening-reports/my")
    public Result<OpeningReportResponse> myOpening(HttpServletRequest request, @RequestParam Long campaignId) {
        Long studentUserId = authFacade.currentUserId(request);
        return Result.ok(processService.getMyOpeningReport(studentUserId, campaignId));
    }

    /** 教师查看名下学生的开题报告 */
    @RequireAuth(UserType.TEACHER)
    @GetMapping("/opening-reports/teacher")
    public Result<List<OpeningReportResponse>> teacherOpenings(HttpServletRequest request,
                                                               @RequestParam Long campaignId) {
        Long teacherUserId = authFacade.currentUserId(request);
        return Result.ok(processService.listOpeningReportsByTeacher(teacherUserId, campaignId));
    }

    /** 开题报告附件下载（学生本人/指导教师/院系/教务） */
    @RequireAuth({UserType.STUDENT, UserType.TEACHER, UserType.DEPARTMENT, UserType.ACADEMIC_ADMIN})
    @GetMapping("/opening-reports/{id:\\d+}/download")
    public ResponseEntity<Resource> downloadOpening(HttpServletRequest request, @PathVariable Long id) {
        Long userId = authFacade.currentUserId(request);
        String userType = authFacade.currentUserType(request);
        FileView view = processService.resolveOpeningReportFile(userType, userId, id);
        String filename = view.originalName() != null ? view.originalName() : view.path().getFileName().toString();
        String encoded = URLEncoder.encode(filename, StandardCharsets.UTF_8).replace("+", "%20");
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename*=UTF-8''" + encoded)
                .contentType(MediaType.parseMediaType(fileService.contentType(view.path().getFileName().toString())))
                .body(new FileSystemResource(view.path()));
    }

    // ==================== 中期检查 ====================

    /** 学生提交中期检查（R-7.4，附件可选） */
    @RequireAuth(UserType.STUDENT)
    @PostMapping(value = "/midterms", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Result<MidtermResponse> submitMidterm(HttpServletRequest request,
                                                 @RequestPart("data") MidtermSubmitRequest body,
                                                 @RequestPart(value = "file", required = false) MultipartFile file) {
        Long studentUserId = authFacade.currentUserId(request);
        return Result.ok(processService.submitMidterm(studentUserId, body, file));
    }

    /** 指导教师审核中期并给出结论（R-7.5） */
    @RequireAuth(UserType.TEACHER)
    @PutMapping("/midterms/{id:\\d+}/review")
    public Result<MidtermResponse> reviewMidterm(HttpServletRequest request, @PathVariable Long id,
                                                 @RequestBody MidtermReviewRequest body) {
        Long teacherUserId = authFacade.currentUserId(request);
        return Result.ok(processService.reviewMidterm(teacherUserId, id, body));
    }

    /** 学生查看我的中期检查 */
    @RequireAuth(UserType.STUDENT)
    @GetMapping("/midterms/my")
    public Result<MidtermResponse> myMidterm(HttpServletRequest request, @RequestParam Long campaignId) {
        Long studentUserId = authFacade.currentUserId(request);
        return Result.ok(processService.getMyMidterm(studentUserId, campaignId));
    }

    /** 教师查看名下学生的中期检查 */
    @RequireAuth(UserType.TEACHER)
    @GetMapping("/midterms/teacher")
    public Result<List<MidtermResponse>> teacherMidterms(HttpServletRequest request, @RequestParam Long campaignId) {
        Long teacherUserId = authFacade.currentUserId(request);
        return Result.ok(processService.listMidtermsByTeacher(teacherUserId, campaignId));
    }

    /** 中期检查附件下载（学生本人/指导教师/院系/教务） */
    @RequireAuth({UserType.STUDENT, UserType.TEACHER, UserType.DEPARTMENT, UserType.ACADEMIC_ADMIN})
    @GetMapping("/midterms/{id:\\d+}/download")
    public ResponseEntity<Resource> downloadMidterm(HttpServletRequest request, @PathVariable Long id) {
        Long userId = authFacade.currentUserId(request);
        String userType = authFacade.currentUserType(request);
        FileView view = processService.resolveMidtermFile(userType, userId, id);
        String filename = view.originalName() != null ? view.originalName() : view.path().getFileName().toString();
        String encoded = URLEncoder.encode(filename, StandardCharsets.UTF_8).replace("+", "%20");
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename*=UTF-8''" + encoded)
                .contentType(MediaType.parseMediaType(fileService.contentType(view.path().getFileName().toString())))
                .body(new FileSystemResource(view.path()));
    }

    // ==================== 过程指导记录 ====================

    /** 教师记录过程指导日志（R-7.7） */
    @RequireAuth(UserType.TEACHER)
    @PostMapping("/guidance-logs")
    public Result<GuidanceLogResponse> createGuidanceLog(HttpServletRequest request,
                                                         @RequestBody GuidanceLogCreateRequest body) {
        Long teacherUserId = authFacade.currentUserId(request);
        return Result.ok(processService.createGuidanceLog(teacherUserId, body));
    }

    /** 查看指导记录（教师本人名下 / 院系本院系 / 教务全部） */
    @RequireAuth({UserType.TEACHER, UserType.DEPARTMENT, UserType.ACADEMIC_ADMIN})
    @GetMapping("/guidance-logs")
    public Result<List<GuidanceLogResponse>> guidanceLogs(HttpServletRequest request,
                                                          @RequestParam(required = false) Long campaignId,
                                                          @RequestParam(required = false) Long studentId) {
        Long userId = authFacade.currentUserId(request);
        String userType = authFacade.currentUserType(request);
        return Result.ok(processService.listGuidanceLogs(campaignId, studentId, userType, userId));
    }
}
