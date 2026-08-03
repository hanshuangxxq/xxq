package com.xrq.xxq.module.analysis.controller;

import java.util.List;

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.xrq.xxq.common.Result;
import com.xrq.xxq.module.analysis.dto.TemplateCreateRequest;
import com.xrq.xxq.module.analysis.dto.TemplateOverrideRequest;
import com.xrq.xxq.module.analysis.dto.TemplateResponse;
import com.xrq.xxq.module.analysis.dto.TemplateUpdateRequest;
import com.xrq.xxq.module.analysis.entity.EvaluationTemplateStatusEnum;
import com.xrq.xxq.module.analysis.service.EvaluationTemplateService;
import com.xrq.xxq.util.auth.AuthFacade;

import lombok.RequiredArgsConstructor;

/**
 * 评教模板管理接口：模板 CRUD、设默认、启停、课程级覆盖。
 * <p>权限：仅教务管理员（academic_admin）可操作。
 */
@RestController
@RequestMapping("/api/analysis/evaluation-templates")
@RequiredArgsConstructor
public class EvaluationTemplateController {

    private final EvaluationTemplateService templateService;
    private final AuthFacade authFacade;

    @PostMapping
    public Result<TemplateResponse> create(HttpServletRequest request,
                                           @RequestBody TemplateCreateRequest body) {
        Long userId = authFacade.requireAcademicAdminUserId(request);
        return Result.ok(templateService.createTemplate(body, userId));
    }

    @GetMapping
    public Result<List<TemplateResponse>> list(HttpServletRequest request) {
        authFacade.requireAcademicAdmin(request);
        return Result.ok(templateService.listTemplates());
    }

    @GetMapping("/{id}")
    public Result<TemplateResponse> detail(HttpServletRequest request,
                                           @PathVariable Long id) {
        authFacade.requireAcademicAdmin(request);
        return Result.ok(templateService.getTemplate(id));
    }

    @PutMapping("/{id}")
    public Result<TemplateResponse> update(HttpServletRequest request,
                                           @PathVariable Long id,
                                           @RequestBody TemplateUpdateRequest body) {
        authFacade.requireAcademicAdmin(request);
        return Result.ok(templateService.updateTemplate(id, body));
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(HttpServletRequest request,
                               @PathVariable Long id) {
        authFacade.requireAcademicAdmin(request);
        templateService.deleteTemplate(id);
        return Result.ok();
    }

    /** 设为全局默认模板。 */
    @PutMapping("/{id}/default")
    public Result<Void> setDefault(HttpServletRequest request,
                                   @PathVariable Long id) {
        authFacade.requireAcademicAdmin(request);
        templateService.setDefault(id);
        return Result.ok();
    }

    /** 启用/停用模板（status=ENABLED/DISABLED）。 */
    @PutMapping("/{id}/status")
    public Result<Void> updateStatus(HttpServletRequest request,
                                     @PathVariable Long id,
                                     @RequestParam EvaluationTemplateStatusEnum status) {
        authFacade.requireAcademicAdmin(request);
        templateService.updateStatus(id, status);
        return Result.ok();
    }

    /** 设置/清除课程级模板覆盖（templateId 为空=清除，回退到全局默认模板）。 */
    @PutMapping("/override/{teachInfoId}")
    public Result<Void> setOverride(HttpServletRequest request,
                                    @PathVariable Long teachInfoId,
                                    @RequestBody(required = false) TemplateOverrideRequest body) {
        authFacade.requireAcademicAdmin(request);
        templateService.setOverride(teachInfoId, body);
        return Result.ok();
    }

    /** 查询课程级覆盖（未设置返回 null，表示用全局默认模板）。 */
    @GetMapping("/override/{teachInfoId}")
    public Result<TemplateResponse> getOverride(HttpServletRequest request,
                                                @PathVariable Long teachInfoId) {
        authFacade.requireAcademicAdmin(request);
        return Result.ok(templateService.getOverride(teachInfoId));
    }
}
