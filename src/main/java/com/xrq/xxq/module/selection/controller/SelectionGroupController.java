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
import com.xrq.xxq.module.selection.dto.SelectionGroupCreateRequest;
import com.xrq.xxq.module.selection.dto.SelectionGroupResponse;
import com.xrq.xxq.module.selection.dto.SelectionGroupUpdateRequest;
import com.xrq.xxq.module.selection.service.SelectionGroupService;

import lombok.RequiredArgsConstructor;

/**
 * 选课组管理接口。
 * <p>
 * 权限：仅教务管理员（academic_admin）可操作。
 */
@RestController
@RequestMapping("/api/selection/campaigns/{campaignId}/groups")
@RequiredArgsConstructor
public class SelectionGroupController {

    private final SelectionGroupService groupService;

    @PostMapping
    public Result<SelectionGroupResponse> create(HttpServletRequest httpRequest,
                                                 @PathVariable Long campaignId,
                                                 @RequestBody SelectionGroupCreateRequest body) {
        checkAcademicAdmin(httpRequest);
        return Result.ok(groupService.create(campaignId, body));
    }

    @GetMapping
    public Result<List<SelectionGroupResponse>> list(HttpServletRequest httpRequest,
                                                     @PathVariable Long campaignId) {
        checkAcademicAdmin(httpRequest);
        return Result.ok(groupService.listByCampaign(campaignId));
    }

    @PutMapping("/{groupId}")
    public Result<SelectionGroupResponse> update(HttpServletRequest httpRequest,
                                                 @PathVariable Long campaignId,
                                                 @PathVariable Long groupId,
                                                 @RequestBody SelectionGroupUpdateRequest body) {
        checkAcademicAdmin(httpRequest);
        return Result.ok(groupService.update(campaignId, groupId, body));
    }

    @DeleteMapping("/{groupId}")
    public Result<Void> delete(HttpServletRequest httpRequest,
                               @PathVariable Long campaignId,
                               @PathVariable Long groupId) {
        checkAcademicAdmin(httpRequest);
        groupService.delete(campaignId, groupId);
        return Result.ok();
    }

    private void checkAcademicAdmin(HttpServletRequest request) {
        String userType = (String) request.getAttribute("userType");
        if (!"academic_admin".equals(userType)) {
            throw new BusinessException(403, "仅教务管理员可配置选课组");
        }
    }
}
