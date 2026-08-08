package com.xrq.xxq.module.selection.controller;

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

import com.xrq.xxq.common.PageQuery;
import com.xrq.xxq.common.PageResult;
import com.xrq.xxq.common.Result;
import com.xrq.xxq.module.selection.dto.CampaignCreateRequest;
import com.xrq.xxq.module.selection.dto.CampaignResponse;
import com.xrq.xxq.module.selection.dto.CampaignUpdateRequest;
import com.xrq.xxq.module.selection.service.SelectionCampaignService;
import com.xrq.xxq.util.auth.AuthFacade;

import lombok.RequiredArgsConstructor;

/**
 * 选课活动管理接口。
 * <p>
 * 权限：仅教务管理员（academic_admin）可操作。
 */
@RestController
@RequestMapping("/api/selection/campaigns")
@RequiredArgsConstructor
public class SelectionCampaignController {

    private final SelectionCampaignService campaignService;
    private final AuthFacade authFacade;

    @PostMapping
    public Result<CampaignResponse> create(HttpServletRequest request,
                                           @RequestBody CampaignCreateRequest body) {
        authFacade.requireAcademicAdmin(request);
        return Result.ok(campaignService.create(body));
    }

    @GetMapping
    public Result<PageResult<CampaignResponse>> list(HttpServletRequest request,
                                                     @RequestParam(required = false) Integer page,
                                                     @RequestParam(required = false) Integer pageSize) {
        authFacade.requireAcademicAdmin(request);
        return Result.ok(campaignService.listAll(new PageQuery(page, pageSize)));
    }

    @GetMapping("/{id}")
    public Result<CampaignResponse> detail(HttpServletRequest request,
                                           @PathVariable Long id) {
        authFacade.requireAcademicAdmin(request);
        return Result.ok(campaignService.getDetail(id));
    }

    @PutMapping("/{id}")
    public Result<CampaignResponse> update(HttpServletRequest request,
                                           @PathVariable Long id,
                                           @RequestBody CampaignUpdateRequest body) {
        authFacade.requireAcademicAdmin(request);
        return Result.ok(campaignService.update(id, body));
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(HttpServletRequest request,
                               @PathVariable Long id) {
        authFacade.requireAcademicAdmin(request);
        campaignService.delete(id);
        return Result.ok();
    }

    @PostMapping("/{id}/open")
    public Result<Void> open(HttpServletRequest request,
                             @PathVariable Long id) {
        authFacade.requireAcademicAdmin(request);
        campaignService.open(id, authFacade.currentUserId(request));
        return Result.ok();
    }

    @PostMapping("/{id}/close")
    public Result<Void> close(HttpServletRequest request,
                              @PathVariable Long id) {
        authFacade.requireAcademicAdmin(request);
        campaignService.close(id);
        return Result.ok();
    }

    @PostMapping("/{id}/finalize")
    public Result<Void> finalize(HttpServletRequest request,
                                 @PathVariable Long id) {
        authFacade.requireAcademicAdmin(request);
        campaignService.finalizeCampaign(id);
        return Result.ok();
    }
}
