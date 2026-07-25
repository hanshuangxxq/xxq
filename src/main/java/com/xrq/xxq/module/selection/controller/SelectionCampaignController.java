package com.xrq.xxq.module.selection.controller;

import java.util.List;

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.xrq.xxq.common.BusinessException;
import com.xrq.xxq.common.Result;
import com.xrq.xxq.module.selection.dto.CampaignCreateRequest;
import com.xrq.xxq.module.selection.dto.CampaignResponse;
import com.xrq.xxq.module.selection.dto.CampaignUpdateRequest;
import com.xrq.xxq.module.selection.service.SelectionCampaignService;

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

    @PostMapping
    public Result<CampaignResponse> create(HttpServletRequest request,
                                           @RequestBody CampaignCreateRequest body) {
        checkAcademicAdmin(request);
        return Result.ok(campaignService.create(body));
    }

    @GetMapping
    public Result<List<CampaignResponse>> list(HttpServletRequest request) {
        checkAcademicAdmin(request);
        return Result.ok(campaignService.listAll());
    }

    @GetMapping("/{id}")
    public Result<CampaignResponse> detail(HttpServletRequest request,
                                           @PathVariable Long id) {
        checkAcademicAdmin(request);
        return Result.ok(campaignService.getDetail(id));
    }

    @PutMapping("/{id}")
    public Result<CampaignResponse> update(HttpServletRequest request,
                                           @PathVariable Long id,
                                           @RequestBody CampaignUpdateRequest body) {
        checkAcademicAdmin(request);
        return Result.ok(campaignService.update(id, body));
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(HttpServletRequest request,
                               @PathVariable Long id) {
        checkAcademicAdmin(request);
        campaignService.delete(id);
        return Result.ok();
    }

    @PostMapping("/{id}/open")
    public Result<Void> open(HttpServletRequest request,
                             @PathVariable Long id) {
        checkAcademicAdmin(request);
        campaignService.open(id);
        return Result.ok();
    }

    @PostMapping("/{id}/close")
    public Result<Void> close(HttpServletRequest request,
                              @PathVariable Long id) {
        checkAcademicAdmin(request);
        campaignService.close(id);
        return Result.ok();
    }

    @PostMapping("/{id}/finalize")
    public Result<Void> finalize(HttpServletRequest request,
                                 @PathVariable Long id) {
        checkAcademicAdmin(request);
        campaignService.finalizeCampaign(id);
        return Result.ok();
    }

    private void checkAcademicAdmin(HttpServletRequest request) {
        String userType = (String) request.getAttribute("userType");
        if (!"academic_admin".equals(userType)) {
            throw new BusinessException(403, "仅教务管理员可操作选课活动");
        }
    }
}
