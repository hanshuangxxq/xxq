package com.xrq.xxq.module.practice.graduation.controller;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;

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
import com.xrq.xxq.module.practice.graduation.dto.ThesisResponse;
import com.xrq.xxq.module.practice.graduation.dto.ThesisReviewRequest;
import com.xrq.xxq.module.practice.graduation.dto.ThesisSubmitRequest;
import com.xrq.xxq.module.practice.graduation.entity.Thesis;
import com.xrq.xxq.module.practice.graduation.entity.ThesisStatusEnum;
import com.xrq.xxq.module.practice.graduation.service.ThesisService;
import com.xrq.xxq.util.auth.AuthFacade;

import lombok.RequiredArgsConstructor;

/**
 * 毕业论文接口。
 * <p>
 * 提交：学生；评审：教师（本人指导）/教务；列表：教师/教务；下载：学生本人/指导教师/教务。
 */
@RestController
@RequestMapping("/api/practice/graduation/theses")
@RequiredArgsConstructor
public class ThesisController {

    private final ThesisService thesisService;
    private final PracticeFileService fileService;
    private final AuthFacade authFacade;

    /** 学生提交论文（multipart：data=JSON 表单，file=文件）。 */
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Result<ThesisResponse> submit(HttpServletRequest request,
                                         @RequestPart("data") ThesisSubmitRequest body,
                                         @RequestPart("file") MultipartFile file) {
        Long studentUserId = authFacade.requireStudentUserId(request);
        return Result.ok(thesisService.submit(studentUserId, body, file));
    }

    /** 学生查看我的论文。 */
    @GetMapping("/my")
    public Result<ThesisResponse> my(HttpServletRequest request) {
        Long studentUserId = authFacade.requireStudentUserId(request);
        return Result.ok(thesisService.getMyThesis(studentUserId));
    }

    /** 处理人待办列表（教师本人指导 / 教务全部）。 */
    @GetMapping
    public Result<PageResult<ThesisResponse>> list(HttpServletRequest request,
                                                   @RequestParam(required = false) ThesisStatusEnum status,
                                                   @RequestParam(required = false) Integer page,
                                                   @RequestParam(required = false) Integer pageSize) {
        AuthFacade.AuthContext ctx = authFacade.requireUserTypesContext(request,
                AuthFacade.USER_TYPE_TEACHER, AuthFacade.USER_TYPE_ACADEMIC_ADMIN);
        return Result.ok(thesisService.listForHandler(ctx.userId(), ctx.userType(), status, new PageQuery(page, pageSize)));
    }

    /** 评审论文（教师本人指导/教务）。 */
    @PostMapping("/{id}/review")
    public Result<ThesisResponse> review(HttpServletRequest request, @PathVariable Long id,
                                         @RequestBody ThesisReviewRequest body) {
        AuthFacade.AuthContext ctx = authFacade.requireUserTypesContext(request,
                AuthFacade.USER_TYPE_TEACHER, AuthFacade.USER_TYPE_ACADEMIC_ADMIN);
        return Result.ok(thesisService.review(id, body, ctx.userId(), ctx.userType()));
    }

    /** 删除论文（教务全权；教师仅本人指导；学生仅本人且未评审）。 */
    @DeleteMapping("/{id}")
    public Result<Void> delete(HttpServletRequest request, @PathVariable Long id) {
        AuthFacade.AuthContext ctx = authFacade.requireUserTypesContext(request,
                AuthFacade.USER_TYPE_TEACHER, AuthFacade.USER_TYPE_ACADEMIC_ADMIN, AuthFacade.USER_TYPE_STUDENT);
        thesisService.deleteThesis(id, ctx.userId(), ctx.userType());
        return Result.ok();
    }

    /** 下载论文文件（学生本人/指导教师/教务）。 */
    @GetMapping("/{id}/download")
    public ResponseEntity<Resource> download(HttpServletRequest request, @PathVariable Long id) {
        AuthFacade.AuthContext ctx = authFacade.requireUserTypesContext(request,
                AuthFacade.USER_TYPE_TEACHER, AuthFacade.USER_TYPE_ACADEMIC_ADMIN, AuthFacade.USER_TYPE_STUDENT);
        Thesis thesis = thesisService.loadForDownload(id, ctx.userId(), ctx.userType());
        Path file = fileService.resolve(thesis.getFileName());
        String filename = thesis.getFileOriginal() != null ? thesis.getFileOriginal() : thesis.getFileName();
        String encoded = URLEncoder.encode(filename, StandardCharsets.UTF_8).replace("+", "%20");
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename*=UTF-8''" + encoded)
                .contentType(MediaType.parseMediaType(fileService.contentType(thesis.getFileName())))
                .body(new FileSystemResource(file));
    }
}
