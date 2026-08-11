package com.xrq.xxq.module.practice.graduation.controller;

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
import com.xrq.xxq.module.practice.graduation.dto.CampaignCreateRequest;
import com.xrq.xxq.module.practice.graduation.dto.CampaignResponse;
import com.xrq.xxq.module.practice.graduation.dto.CampaignUpdateRequest;
import com.xrq.xxq.module.practice.graduation.entity.CampaignStatusEnum;
import com.xrq.xxq.module.practice.graduation.service.GraduationCampaignService;
import com.xrq.xxq.util.auth.AuthFacade;
import com.xrq.xxq.util.auth.AuthFacade.AuthContext;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;

/**
 * 毕设活动管理（§4，教务创建/配置，四角色查看）。
 */
@RestController
@RequestMapping("/api/practice/graduation/campaigns")
@RequiredArgsConstructor
public class GraduationCampaignController {

    private final GraduationCampaignService campaignService;
    private final AuthFacade authFacade;

    /** 教务创建毕设活动 */
    @PostMapping
    public Result<CampaignResponse> create(HttpServletRequest request, @RequestBody CampaignCreateRequest body) {
        Long operatorId = authFacade.requireAcademicAdminUserId(request);
        return Result.ok(campaignService.createCampaign(operatorId, AuthFacade.USER_TYPE_ACADEMIC_ADMIN, body));
    }

    /** 教务更新毕设活动（R-4.1 开始后仅允许延长截止/上调名额） */
    @PutMapping("/{id:\\d+}")
    public Result<CampaignResponse> update(HttpServletRequest request, @PathVariable Long id,
                                           @RequestBody CampaignUpdateRequest body) {
        Long operatorId = authFacade.requireAcademicAdminUserId(request);
        return Result.ok(campaignService.updateCampaign(operatorId, AuthFacade.USER_TYPE_ACADEMIC_ADMIN, id, body));
    }

    /** 教务切换活动状态（DRAFT/OPEN/CLOSED） */
    @PutMapping("/{id:\\d+}/status")
    public Result<Void> changeStatus(HttpServletRequest request, @PathVariable Long id,
                                     @RequestParam CampaignStatusEnum status) {
        Long operatorId = authFacade.requireAcademicAdminUserId(request);
        campaignService.changeCampaignStatus(operatorId, AuthFacade.USER_TYPE_ACADEMIC_ADMIN, id, status);
        return Result.ok();
    }

    /** 教务分页查看活动 */
    @GetMapping
    public Result<PageResult<CampaignResponse>> list(HttpServletRequest request,
                                                     @RequestParam(required = false) CampaignStatusEnum status,
                                                     PageQuery pageQuery) {
        authFacade.requireAcademicAdmin(request);
        return Result.ok(campaignService.listCampaigns(status, pageQuery));
    }

    /** 活动详情（四角色可见） */
    @GetMapping("/{id:\\d+}")
    public Result<CampaignResponse> get(HttpServletRequest request, @PathVariable Long id) {
        authFacade.requireUserTypes(request, AuthFacade.USER_TYPE_ACADEMIC_ADMIN, AuthFacade.USER_TYPE_DEPARTMENT,
                AuthFacade.USER_TYPE_TEACHER, AuthFacade.USER_TYPE_STUDENT);
        return Result.ok(campaignService.getCampaign(id));
    }

    /** 学生可见的进行中活动（参与年级匹配） */
    @GetMapping("/available")
    public Result<java.util.List<CampaignResponse>> available(HttpServletRequest request) {
        Long studentUserId = authFacade.requireStudentUserId(request);
        return Result.ok(campaignService.listAvailableCampaignsForStudent(studentUserId));
    }

    /** 教师/院系活动选择器（返回所有非草稿活动，供下拉选择用） */
    @GetMapping("/selector")
    public Result<java.util.List<CampaignResponse>> selector(HttpServletRequest request) {
        authFacade.requireUserTypes(request,
                AuthFacade.USER_TYPE_TEACHER, AuthFacade.USER_TYPE_DEPARTMENT);
        return Result.ok(campaignService.listCampaignsForSelector());
    }

    /** 教务查看活动详情（含分配总览的详情页入口，复用 get） */
    @GetMapping("/{id:\\d+}/detail")
    public Result<CampaignResponse> detail(HttpServletRequest request, @PathVariable Long id) {
        authFacade.requireAcademicAdmin(request);
        return Result.ok(campaignService.getCampaign(id));
    }
}
