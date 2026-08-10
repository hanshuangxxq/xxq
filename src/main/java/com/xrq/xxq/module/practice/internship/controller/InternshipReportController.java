package com.xrq.xxq.module.practice.internship.controller;

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
import com.xrq.xxq.module.practice.common.PracticeFileService;
import com.xrq.xxq.module.practice.common.entity.ReportStatusEnum;
import com.xrq.xxq.module.practice.internship.dto.InternshipReportResponse;
import com.xrq.xxq.module.practice.internship.dto.InternshipReportReviewRequest;
import com.xrq.xxq.module.practice.internship.dto.InternshipReportSubmitRequest;
import com.xrq.xxq.module.practice.internship.entity.InternshipReport;
import com.xrq.xxq.module.practice.internship.service.InternshipReportService;
import com.xrq.xxq.util.auth.AuthFacade;

import lombok.RequiredArgsConstructor;

/**
 * 实习成果报告接口。
 * <p>
 * 提交：学生；评审/列表：院系管理者（负责本人）/教务；下载：学生本人/负责院系管理者/教务。
 */
@RestController
@RequestMapping("/api/practice/internship-reports")
@RequiredArgsConstructor
public class InternshipReportController {

    private final InternshipReportService reportService;
    private final PracticeFileService fileService;
    private final AuthFacade authFacade;

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Result<InternshipReportResponse> submit(HttpServletRequest request,
                                                   @RequestPart("data") InternshipReportSubmitRequest body,
                                                   @RequestPart("file") MultipartFile file) {
        Long studentUserId = authFacade.requireStudentUserId(request);
        return Result.ok(reportService.submit(studentUserId, body, file));
    }

    @GetMapping("/my")
    public Result<List<InternshipReportResponse>> my(HttpServletRequest request) {
        Long studentUserId = authFacade.requireStudentUserId(request);
        return Result.ok(reportService.listMyReports(studentUserId));
    }

    @GetMapping
    public Result<PageResult<InternshipReportResponse>> list(HttpServletRequest request,
                                                             @RequestParam(required = false) ReportStatusEnum status,
                                                             @RequestParam(required = false) Integer page,
                                                             @RequestParam(required = false) Integer pageSize) {
        AuthFacade.AuthContext ctx = authFacade.requireUserTypesContext(request,
                AuthFacade.USER_TYPE_DEPARTMENT, AuthFacade.USER_TYPE_ACADEMIC_ADMIN);
        return Result.ok(reportService.listForHandler(ctx.userId(), ctx.userType(), status, new PageQuery(page, pageSize)));
    }

    @PostMapping("/{id}/review")
    public Result<InternshipReportResponse> review(HttpServletRequest request, @PathVariable Long id,
                                                   @RequestBody InternshipReportReviewRequest body) {
        AuthFacade.AuthContext ctx = authFacade.requireUserTypesContext(request,
                AuthFacade.USER_TYPE_DEPARTMENT, AuthFacade.USER_TYPE_ACADEMIC_ADMIN);
        return Result.ok(reportService.review(id, body, ctx.userId(), ctx.userType()));
    }

    /** 删除报告（教务全权；院系管理者负责实习；学生仅本人且未评审）。 */
    @DeleteMapping("/{id}")
    public Result<Void> delete(HttpServletRequest request, @PathVariable Long id) {
        AuthFacade.AuthContext ctx = authFacade.requireUserTypesContext(request,
                AuthFacade.USER_TYPE_DEPARTMENT, AuthFacade.USER_TYPE_ACADEMIC_ADMIN, AuthFacade.USER_TYPE_STUDENT);
        reportService.deleteReport(id, ctx.userId(), ctx.userType());
        return Result.ok();
    }

    @GetMapping("/{id}/download")
    public ResponseEntity<Resource> download(HttpServletRequest request, @PathVariable Long id) {
        AuthFacade.AuthContext ctx = authFacade.requireUserTypesContext(request,
                AuthFacade.USER_TYPE_DEPARTMENT, AuthFacade.USER_TYPE_ACADEMIC_ADMIN, AuthFacade.USER_TYPE_STUDENT);
        InternshipReport report = reportService.loadForDownload(id, ctx.userId(), ctx.userType());
        Path file = fileService.resolve(report.getFileName());
        String filename = report.getFileOriginal() != null ? report.getFileOriginal() : report.getFileName();
        String encoded = URLEncoder.encode(filename, StandardCharsets.UTF_8).replace("+", "%20");
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename*=UTF-8''" + encoded)
                .contentType(MediaType.parseMediaType(fileService.contentType(report.getFileName())))
                .body(new FileSystemResource(file));
    }
}
