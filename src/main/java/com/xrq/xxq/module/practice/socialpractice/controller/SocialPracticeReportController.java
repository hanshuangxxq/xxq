package com.xrq.xxq.module.practice.socialpractice.controller;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.xrq.xxq.common.PageQuery;
import com.xrq.xxq.common.PageResult;
import com.xrq.xxq.common.Result;
import com.xrq.xxq.module.practice.common.PracticeFileSupport;
import com.xrq.xxq.util.file.ResumableFileResponse;
import com.xrq.xxq.module.practice.common.entity.ReportStatusEnum;
import com.xrq.xxq.module.practice.socialpractice.dto.SocialPracticeReportResponse;
import com.xrq.xxq.module.practice.socialpractice.dto.SocialPracticeReportReviewRequest;
import com.xrq.xxq.module.practice.socialpractice.dto.SocialPracticeReportSubmitRequest;
import com.xrq.xxq.module.practice.socialpractice.entity.SocialPracticeReport;
import com.xrq.xxq.module.practice.socialpractice.service.SocialPracticeReportService;
import com.xrq.xxq.util.auth.AuthFacade;
import com.xrq.xxq.util.auth.RequireAcademicAdmin;
import com.xrq.xxq.util.auth.RequireAuth;
import com.xrq.xxq.util.auth.RequireStudent;
import com.xrq.xxq.util.auth.UserType;

import lombok.RequiredArgsConstructor;

/**
 * 社会实践报告接口。
 * <p>
 * 提交：学生；评审/列表：教务；下载：学生本人/教务。
 */
@RestController
@RequestMapping("/api/practice/social-practice-reports")
@RequiredArgsConstructor
public class SocialPracticeReportController {

    private final SocialPracticeReportService reportService;
    private final PracticeFileSupport fileSupport;
    private final AuthFacade authFacade;

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @RequireStudent
    public Result<SocialPracticeReportResponse> submit(HttpServletRequest request,
                                                       @RequestPart("data") SocialPracticeReportSubmitRequest body,
                                                       // 可选：与 body.filePath（分片产物）二选一
                                                       @RequestPart(value = "file", required = false) MultipartFile file) {
        Long studentUserId = authFacade.currentUserId(request);
        return Result.ok(reportService.submit(studentUserId, body, file));
    }

    @GetMapping("/my")
    @RequireStudent
    public Result<List<SocialPracticeReportResponse>> my(HttpServletRequest request) {
        Long studentUserId = authFacade.currentUserId(request);
        return Result.ok(reportService.listMyReports(studentUserId));
    }

    @GetMapping
    @RequireAcademicAdmin
    public Result<PageResult<SocialPracticeReportResponse>> list(HttpServletRequest request,
                                                                 @RequestParam(required = false) ReportStatusEnum status,
                                                                 @RequestParam(required = false) Integer page,
                                                                 @RequestParam(required = false) Integer pageSize) {
        return Result.ok(reportService.listForHandler(status, new PageQuery(page, pageSize)));
    }

    @PostMapping("/{id}/review")
    @RequireAcademicAdmin
    public Result<SocialPracticeReportResponse> review(HttpServletRequest request, @PathVariable Long id,
                                                       @RequestBody SocialPracticeReportReviewRequest body) {
        return Result.ok(reportService.review(id, body));
    }

    /** 删除报告（教务全权；学生仅本人且未评审）。 */
    @DeleteMapping("/{id}")
    @RequireAuth({UserType.ACADEMIC_ADMIN, UserType.STUDENT})
    public Result<Void> delete(HttpServletRequest request, @PathVariable Long id) {
        Long userId = authFacade.currentUserId(request);
        String userType = authFacade.currentUserType(request);
        reportService.deleteReport(id, userId, userType);
        return Result.ok();
    }

    @GetMapping("/{id}/download")
    @RequireAuth({UserType.ACADEMIC_ADMIN, UserType.STUDENT})
    public ResponseEntity<Resource> download(HttpServletRequest request, @PathVariable Long id) {
        Long userId = authFacade.currentUserId(request);
        String userType = authFacade.currentUserType(request);
        SocialPracticeReport report = reportService.loadForDownload(id, userId, userType);
        Path file = fileSupport.resolveForDownload(report.getFileName());
        return ResumableFileResponse.buildDownload(file,
                report.getFileOriginal() != null ? report.getFileOriginal() : report.getFileName(),
                ResumableFileResponse.sha256FromFileName(file.getFileName().toString()));
    }
}
