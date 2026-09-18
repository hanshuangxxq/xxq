package com.xrq.xxq.module.practice.graduation.controller;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import jakarta.servlet.http.HttpServletRequest;
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
import com.xrq.xxq.module.practice.common.FileView;
import com.xrq.xxq.module.practice.common.PracticeFileSupport;
import com.xrq.xxq.util.file.ResumableFileResponse;
import com.xrq.xxq.module.practice.graduation.dto.DuplicateCheckRegisterRequest;
import com.xrq.xxq.module.practice.graduation.dto.DuplicateCheckResponse;
import com.xrq.xxq.module.practice.graduation.dto.ThesisResponse;
import com.xrq.xxq.module.practice.graduation.dto.ThesisReviewRequest;
import com.xrq.xxq.module.practice.graduation.dto.ThesisSubmitRequest;
import com.xrq.xxq.module.practice.graduation.entity.ThesisStatusEnum;
import com.xrq.xxq.module.practice.graduation.service.GraduationThesisService;
import com.xrq.xxq.util.auth.AuthFacade;
import com.xrq.xxq.util.auth.RequireAcademicAdmin;
import com.xrq.xxq.util.auth.RequireAuth;
import com.xrq.xxq.util.auth.RequireStudent;
import com.xrq.xxq.util.auth.RequireTeacher;
import com.xrq.xxq.util.auth.UserType;
import lombok.RequiredArgsConstructor;

/**
 * 论文与查重（阶段三）。
 */
@RestController
@RequestMapping("/api/practice/graduation/theses")
@RequiredArgsConstructor
public class GraduationThesisController {

    private final GraduationThesisService thesisService;
    private final PracticeFileSupport fileSupport;
    private final AuthFacade authFacade;

    /** 学生提交/重提论文（R-8.1/R-8.2，版本管理） */
    @RequireStudent
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Result<ThesisResponse> submit(HttpServletRequest request,
                                         @RequestPart("data") ThesisSubmitRequest body,
                                         // 可选：与 body.filePath（分片产物）二选一
                                         @RequestPart(value = "file", required = false) MultipartFile file) {
        Long studentUserId = authFacade.currentUserId(request);
        return Result.ok(thesisService.submitThesis(studentUserId, body, file));
    }

    /** 指导教师形式审查（R-8.3） */
    @RequireTeacher
    @PutMapping("/{id:\\d+}/review")
    public Result<ThesisResponse> review(HttpServletRequest request, @PathVariable Long id,
                                         @RequestBody ThesisReviewRequest body) {
        Long teacherUserId = authFacade.currentUserId(request);
        return Result.ok(thesisService.reviewThesis(teacherUserId, id, body));
    }

    /** 学生查看我的论文（含版本与查重记录） */
    @RequireStudent
    @GetMapping("/my")
    public Result<List<ThesisResponse>> my(HttpServletRequest request,
                                           @RequestParam(required = false) Long campaignId) {
        Long studentUserId = authFacade.currentUserId(request);
        return Result.ok(thesisService.listMyThesis(studentUserId, campaignId));
    }

    /** 教师查看名下学生的论文 */
    @RequireTeacher
    @GetMapping("/teacher")
    public Result<List<ThesisResponse>> teacher(HttpServletRequest request, @RequestParam Long campaignId) {
        Long teacherUserId = authFacade.currentUserId(request);
        return Result.ok(thesisService.listTeacherThesis(teacherUserId, campaignId));
    }

    /** 教务查看活动内论文（按状态筛选） */
    @RequireAcademicAdmin
    @GetMapping("/campaign")
    public Result<List<ThesisResponse>> campaign(HttpServletRequest request, @RequestParam Long campaignId,
                                                 @RequestParam(required = false) ThesisStatusEnum status) {
        return Result.ok(thesisService.listCampaignThesis(campaignId, status));
    }

    /** 教务导出查重数据包（R-8.4：zip 内含 xlsx 名单 + 论文文件） */
    @RequireAcademicAdmin
    @GetMapping("/export-package")
    public ResponseEntity<byte[]> exportPackage(HttpServletRequest request, @RequestParam Long campaignId,
                                                @RequestParam(required = false) ThesisStatusEnum status)
            throws java.io.IOException {
        Long academicUserId = authFacade.currentUserId(request);
        var file = thesisService.exportPackage(academicUserId, campaignId, status);
        String encoded = URLEncoder.encode(file.fileName(), StandardCharsets.UTF_8).replace("+", "%20");
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename*=UTF-8''" + encoded)
                .contentType(MediaType.parseMediaType("application/zip"))
                .body(file.data());
    }

    /** 教务登记查重结果（R-8.5/R-8.6；可选附查重报告：file 整传 与 data.filePath 分片产物 二选一） */
    @RequireAcademicAdmin
    @PostMapping(value = "/duplicate-checks", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Result<DuplicateCheckResponse> registerDuplicateCheck(HttpServletRequest request,
                                                                 @RequestPart("data") DuplicateCheckRegisterRequest body,
                                                                 @RequestPart(value = "file", required = false) MultipartFile file) {
        Long academicUserId = authFacade.currentUserId(request);
        return Result.ok(thesisService.registerDuplicateCheck(academicUserId, body, file));
    }

    /** 论文的查重记录 */
    @RequireAuth({UserType.STUDENT, UserType.TEACHER, UserType.DEPARTMENT, UserType.ACADEMIC_ADMIN})
    @GetMapping("/{id:\\d+}/duplicate-checks")
    public Result<List<DuplicateCheckResponse>> duplicateChecks(HttpServletRequest request, @PathVariable Long id) {
        return Result.ok(thesisService.listDuplicateChecks(id));
    }

    /** 查重报告下载（学生本人/指导教师/院系/教务） */
    @RequireAuth({UserType.STUDENT, UserType.TEACHER, UserType.DEPARTMENT, UserType.ACADEMIC_ADMIN})
    @GetMapping("/duplicate-checks/{checkId:\\d+}/download")
    public ResponseEntity<Resource> downloadDuplicateCheck(HttpServletRequest request,
                                                           @PathVariable Long checkId) {
        Long userId = authFacade.currentUserId(request);
        String userType = authFacade.currentUserType(request);
        FileView view = thesisService.resolveDuplicateCheckFile(userType, userId, checkId);
        return ResumableFileResponse.buildDownload(view.path(), view.originalName(),
                ResumableFileResponse.sha256FromFileName(view.path().getFileName().toString()));
    }

    /** 论文文件下载（学生本人/指导教师/院系/教务） */
    @RequireAuth({UserType.STUDENT, UserType.TEACHER, UserType.DEPARTMENT, UserType.ACADEMIC_ADMIN})
    @GetMapping("/{id:\\d+}/download")
    public ResponseEntity<Resource> download(HttpServletRequest request, @PathVariable Long id) {
        Long userId = authFacade.currentUserId(request);
        String userType = authFacade.currentUserType(request);
        FileView view = thesisService.resolveThesisFile(userType, userId, id);
        // 统一响应构建：Content-Disposition(RFC 5987) + Content-Type 推断 + Accept-Ranges + 强 ETag，
        // Range 头由 Spring 原生处理返回 206（旧代码 5 处逐字重复且都没有 Accept-Ranges/ETag）
        return ResumableFileResponse.buildDownload(view.path(), view.originalName(),
                ResumableFileResponse.sha256FromFileName(view.path().getFileName().toString()));
    }
}
