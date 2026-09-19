package com.xrq.xxq.module.practice.graduation.controller;

import java.util.List;

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.xrq.xxq.common.Result;
import com.xrq.xxq.module.practice.common.FileView;
import com.xrq.xxq.module.practice.graduation.dto.CampaignMaterialResponse;
import com.xrq.xxq.module.practice.graduation.service.GraduationCampaignMaterialService;
import com.xrq.xxq.util.auth.AuthFacade;
import com.xrq.xxq.util.auth.RequireAcademicAdmin;
import com.xrq.xxq.util.auth.RequireAuth;
import com.xrq.xxq.util.auth.UserType;
import com.xrq.xxq.util.file.ResumableFileResponse;

import lombok.RequiredArgsConstructor;

/**
 * 毕设活动资料（教务下发模板/规范，四种角色可下载——可见性与活动详情一致）。
 */
@RestController
@RequestMapping("/api/practice/graduation/campaigns")
@RequiredArgsConstructor
public class GraduationCampaignMaterialController {

    private final GraduationCampaignMaterialService materialService;
    private final AuthFacade authFacade;

    /** 教务上传活动资料（file 整传 与 filePath 分片产物 二选一，必填其一） */
    @RequireAcademicAdmin
    @PostMapping(value = "/{id:\\d+}/materials", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Result<CampaignMaterialResponse> upload(HttpServletRequest request, @PathVariable Long id,
                                                   @RequestParam(required = false) String filePath,
                                                   @RequestParam(required = false) String fileOriginal,
                                                   @RequestPart(value = "file", required = false) MultipartFile file) {
        return Result.ok(materialService.upload(authFacade.currentUserId(request), id,
                filePath, fileOriginal, file));
    }

    /** 活动资料列表 */
    @RequireAuth({UserType.ACADEMIC_ADMIN, UserType.DEPARTMENT, UserType.TEACHER, UserType.STUDENT})
    @GetMapping("/{id:\\d+}/materials")
    public Result<List<CampaignMaterialResponse>> list(@PathVariable Long id) {
        return Result.ok(materialService.list(id));
    }

    /** 活动资料下载 */
    @RequireAuth({UserType.ACADEMIC_ADMIN, UserType.DEPARTMENT, UserType.TEACHER, UserType.STUDENT})
    @GetMapping("/{id:\\d+}/materials/{mid:\\d+}/download")
    public ResponseEntity<Resource> download(@PathVariable Long id, @PathVariable Long mid) {
        FileView view = materialService.resolveMaterialFile(id, mid);
        return ResumableFileResponse.buildDownload(view.path(), view.originalName(),
                ResumableFileResponse.sha256FromFileName(view.path().getFileName().toString()));
    }

    /** 教务删除活动资料 */
    @RequireAcademicAdmin
    @DeleteMapping("/{id:\\d+}/materials/{mid:\\d+}")
    public Result<Void> delete(HttpServletRequest request, @PathVariable Long id, @PathVariable Long mid) {
        materialService.delete(authFacade.currentUserId(request), id, mid);
        return Result.ok();
    }
}
