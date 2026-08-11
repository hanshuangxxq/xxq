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
import com.xrq.xxq.module.practice.graduation.dto.DuplicateCheckRegisterRequest;
import com.xrq.xxq.module.practice.graduation.dto.DuplicateCheckResponse;
import com.xrq.xxq.module.practice.graduation.dto.ThesisResponse;
import com.xrq.xxq.module.practice.graduation.dto.ThesisReviewRequest;
import com.xrq.xxq.module.practice.graduation.dto.ThesisSubmitRequest;
import com.xrq.xxq.module.practice.graduation.entity.ThesisStatusEnum;
import com.xrq.xxq.module.practice.graduation.service.GraduationThesisService;
import com.xrq.xxq.module.practice.graduation.service.GraduationThesisService.FileView;
import com.xrq.xxq.util.auth.AuthFacade;
import com.xrq.xxq.util.auth.AuthFacade.AuthContext;

import lombok.RequiredArgsConstructor;

/**
 * 论文与查重（阶段三）。
 */
@RestController
@RequestMapping("/api/practice/graduation/theses")
@RequiredArgsConstructor
public class GraduationThesisController {

    private final GraduationThesisService thesisService;
    private final PracticeFileService fileService;
    private final AuthFacade authFacade;

    /** 学生提交/重提论文（R-8.1/R-8.2，版本管理） */
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Result<ThesisResponse> submit(HttpServletRequest request,
                                         @RequestPart("data") ThesisSubmitRequest body,
                                         @RequestPart("file") MultipartFile file) {
        Long studentUserId = authFacade.requireStudentUserId(request);
        return Result.ok(thesisService.submitThesis(studentUserId, body, file));
    }

    /** 指导教师形式审查（R-8.3） */
    @PutMapping("/{id:\\d+}/review")
    public Result<ThesisResponse> review(HttpServletRequest request, @PathVariable Long id,
                                         @RequestBody ThesisReviewRequest body) {
        Long teacherUserId = authFacade.requireUserTypesUserId(request, AuthFacade.USER_TYPE_TEACHER);
        return Result.ok(thesisService.reviewThesis(teacherUserId, id, body));
    }

    /** 学生查看我的论文（含版本与查重记录） */
    @GetMapping("/my")
    public Result<List<ThesisResponse>> my(HttpServletRequest request,
                                           @RequestParam(required = false) Long campaignId) {
        Long studentUserId = authFacade.requireStudentUserId(request);
        return Result.ok(thesisService.listMyThesis(studentUserId, campaignId));
    }

    /** 教师查看名下学生的论文 */
    @GetMapping("/teacher")
    public Result<List<ThesisResponse>> teacher(HttpServletRequest request, @RequestParam Long campaignId) {
        Long teacherUserId = authFacade.requireUserTypesUserId(request, AuthFacade.USER_TYPE_TEACHER);
        return Result.ok(thesisService.listTeacherThesis(teacherUserId, campaignId));
    }

    /** 教务查看活动内论文（按状态筛选） */
    @GetMapping("/campaign")
    public Result<List<ThesisResponse>> campaign(HttpServletRequest request, @RequestParam Long campaignId,
                                                 @RequestParam(required = false) ThesisStatusEnum status) {
        authFacade.requireAcademicAdmin(request);
        return Result.ok(thesisService.listCampaignThesis(campaignId, status));
    }

    /** 教务导出查重数据包（R-8.4：zip 内含 xlsx 名单 + 论文文件） */
    @GetMapping("/export-package")
    public ResponseEntity<byte[]> exportPackage(HttpServletRequest request, @RequestParam Long campaignId,
                                                @RequestParam(required = false) ThesisStatusEnum status)
            throws java.io.IOException {
        Long academicUserId = authFacade.requireAcademicAdminUserId(request);
        var file = thesisService.exportPackage(academicUserId, campaignId, status);
        String encoded = URLEncoder.encode(file.fileName(), StandardCharsets.UTF_8).replace("+", "%20");
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename*=UTF-8''" + encoded)
                .contentType(MediaType.parseMediaType("application/zip"))
                .body(file.data());
    }

    /** 教务登记查重结果（R-8.5/R-8.6） */
    @PostMapping("/duplicate-checks")
    public Result<DuplicateCheckResponse> registerDuplicateCheck(HttpServletRequest request,
                                                                 @RequestBody DuplicateCheckRegisterRequest body) {
        Long academicUserId = authFacade.requireAcademicAdminUserId(request);
        return Result.ok(thesisService.registerDuplicateCheck(academicUserId, body));
    }

    /** 论文的查重记录 */
    @GetMapping("/{id:\\d+}/duplicate-checks")
    public Result<List<DuplicateCheckResponse>> duplicateChecks(HttpServletRequest request, @PathVariable Long id) {
        AuthContext ctx = authFacade.requireUserTypesContext(request,
                AuthFacade.USER_TYPE_STUDENT, AuthFacade.USER_TYPE_TEACHER,
                AuthFacade.USER_TYPE_DEPARTMENT, AuthFacade.USER_TYPE_ACADEMIC_ADMIN);
        return Result.ok(thesisService.listDuplicateChecks(id));
    }

    /** 论文文件下载（学生本人/指导教师/院系/教务） */
    @GetMapping("/{id:\\d+}/download")
    public ResponseEntity<Resource> download(HttpServletRequest request, @PathVariable Long id) {
        AuthContext ctx = authFacade.requireUserTypesContext(request,
                AuthFacade.USER_TYPE_STUDENT, AuthFacade.USER_TYPE_TEACHER,
                AuthFacade.USER_TYPE_DEPARTMENT, AuthFacade.USER_TYPE_ACADEMIC_ADMIN);
        FileView view = thesisService.resolveThesisFile(ctx.userType(), ctx.userId(), id);
        String filename = view.originalName() != null ? view.originalName() : view.path().getFileName().toString();
        String encoded = URLEncoder.encode(filename, StandardCharsets.UTF_8).replace("+", "%20");
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename*=UTF-8''" + encoded)
                .contentType(MediaType.parseMediaType(fileService.contentType(view.path().getFileName().toString())))
                .body(new FileSystemResource(view.path()));
    }
}
