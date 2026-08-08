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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.xrq.xxq.common.PageQuery;
import com.xrq.xxq.common.PageResult;
import com.xrq.xxq.common.Result;
import com.xrq.xxq.module.selection.dto.CampaignResponse;
import com.xrq.xxq.module.selection.dto.SelectionGroupCreateRequest;
import com.xrq.xxq.module.selection.dto.SelectionGroupResponse;
import com.xrq.xxq.module.selection.dto.SelectionGroupUpdateRequest;
import com.xrq.xxq.module.selection.service.SelectionCampaignService;
import com.xrq.xxq.module.selection.service.SelectionGroupService;
import com.xrq.xxq.util.auth.AuthFacade;

import lombok.RequiredArgsConstructor;

/**
 * 选课组独立管理接口。
 * <p>
 * 选课组是独立实体，可被多个选课活动复用。绑定关系通过
 * {@code SelectionCampaignController} 的 create/update 接口携带 groupId 完成。
 * <p>
 * 权限：仅教务管理员（academic_admin）可操作。
 */
@RestController
@RequestMapping("/api/selection/groups")
@RequiredArgsConstructor
public class SelectionGroupController {

    private final SelectionGroupService groupService;
    private final SelectionCampaignService campaignService;
    private final AuthFacade authFacade;

    @PostMapping
    public Result<SelectionGroupResponse> create(HttpServletRequest httpRequest,
                                                 @RequestBody SelectionGroupCreateRequest body) {
        authFacade.requireAcademicAdmin(httpRequest);
        return Result.ok(groupService.create(body));
    }

    @GetMapping
    public Result<PageResult<SelectionGroupResponse>> list(HttpServletRequest httpRequest,
                                                           @RequestParam(required = false) Integer page,
                                                           @RequestParam(required = false) Integer pageSize) {
        authFacade.requireAcademicAdmin(httpRequest);
        return Result.ok(groupService.listAll(new PageQuery(page, pageSize)));
    }

    @GetMapping("/{groupId}")
    public Result<SelectionGroupResponse> detail(HttpServletRequest httpRequest,
                                                 @PathVariable Long groupId) {
        authFacade.requireAcademicAdmin(httpRequest);
        return Result.ok(groupService.getDetail(groupId));
    }

    /**
     * 列出可绑定到指定选课组的选课活动。
     * <p>
     * 由于一个活动只能绑定一个选课组，结果排除已绑定到其它选课组的活动，
     * 仅返回未绑定任何组的活动以及已绑定到本组的活动。
     */
    @GetMapping("/{groupId}/bindable-campaigns")
    public Result<List<CampaignResponse>> listBindableCampaigns(HttpServletRequest httpRequest,
                                                                @PathVariable Long groupId) {
        authFacade.requireAcademicAdmin(httpRequest);
        return Result.ok(campaignService.listBindableForGroup(groupId));
    }

    @PutMapping("/{groupId}")
    public Result<SelectionGroupResponse> update(HttpServletRequest httpRequest,
                                                 @PathVariable Long groupId,
                                                 @RequestBody SelectionGroupUpdateRequest body) {
        authFacade.requireAcademicAdmin(httpRequest);
        return Result.ok(groupService.update(groupId, body));
    }

    @DeleteMapping("/{groupId}")
    public Result<Void> delete(HttpServletRequest httpRequest,
                               @PathVariable Long groupId) {
        authFacade.requireAcademicAdmin(httpRequest);
        groupService.delete(groupId);
        return Result.ok();
    }
}
