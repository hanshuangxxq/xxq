package com.xrq.xxq.module.selection.controller;

import java.util.List;

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.xrq.xxq.common.Result;
import com.xrq.xxq.module.selection.dto.CampaignGroupBindingRequest;
import com.xrq.xxq.module.selection.dto.SelectionGroupResponse;
import com.xrq.xxq.module.selection.service.SelectionGroupService;
import com.xrq.xxq.util.auth.AuthFacade;

import lombok.RequiredArgsConstructor;

/**
 * 选课活动-选课组绑定接口。
 * <p>
 * 将独立的选课组绑定到某个选课活动。一个选课活动只能绑定一个选课组（服务层强制），
 * 一个选课组可被多个活动复用。仅草稿状态的活动可配置绑定；解绑时组内不得仍有可选课程。
 * <p>
 * 权限：仅教务管理员（academic_admin）可操作。
 */
@RestController
@RequestMapping("/api/selection/campaigns/{campaignId}/groups")
@RequiredArgsConstructor
public class CampaignGroupBindingController {

    private final SelectionGroupService groupService;
    private final AuthFacade authFacade;

    @GetMapping
    public Result<List<SelectionGroupResponse>> list(HttpServletRequest httpRequest,
                                                     @PathVariable Long campaignId) {
        authFacade.requireAcademicAdmin(httpRequest);
        return Result.ok(groupService.listByCampaign(campaignId));
    }

    @PostMapping
    public Result<Void> bind(HttpServletRequest httpRequest,
                             @PathVariable Long campaignId,
                             @RequestBody CampaignGroupBindingRequest body) {
        authFacade.requireAcademicAdmin(httpRequest);
        groupService.bindToCampaign(campaignId, body);
        return Result.ok();
    }

    @DeleteMapping("/{groupId}")
    public Result<Void> unbind(HttpServletRequest httpRequest,
                               @PathVariable Long campaignId,
                               @PathVariable Long groupId) {
        authFacade.requireAcademicAdmin(httpRequest);
        groupService.unbindFromCampaign(campaignId, groupId);
        return Result.ok();
    }
}
