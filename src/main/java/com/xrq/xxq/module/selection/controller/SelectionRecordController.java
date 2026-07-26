package com.xrq.xxq.module.selection.controller;

import java.util.List;

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.xrq.xxq.common.Result;
import com.xrq.xxq.module.selection.dto.SelectionRecordRequest;
import com.xrq.xxq.module.selection.dto.SelectionRecordResponse;
import com.xrq.xxq.module.selection.dto.StudentCampaignResponse;
import com.xrq.xxq.module.selection.service.SelectionRecordService;
import com.xrq.xxq.util.auth.AuthFacade;

import lombok.RequiredArgsConstructor;

/**
 * 学生选课接口。
 * <p>
 * 权限：仅学生（student）可操作。
 */
@RestController
@RequestMapping("/api/selection/student")
@RequiredArgsConstructor
public class SelectionRecordController {

    private final SelectionRecordService selectionRecordService;
    private final AuthFacade authFacade;

    @GetMapping("/campaigns")
    public Result<List<StudentCampaignResponse>> listOpenCampaigns(HttpServletRequest request) {
        Long studentUserId = authFacade.requireStudentUserId(request);
        return Result.ok(selectionRecordService.listOpenCampaignsForStudent(studentUserId));
    }

    @GetMapping("/campaigns/{campaignId}")
    public Result<StudentCampaignResponse> getCampaign(HttpServletRequest request,
                                                       @PathVariable Long campaignId) {
        Long studentUserId = authFacade.requireStudentUserId(request);
        return Result.ok(selectionRecordService.getCampaignForStudent(campaignId, studentUserId));
    }

    @PostMapping("/records")
    public Result<SelectionRecordResponse> select(HttpServletRequest request,
                                                  @RequestBody SelectionRecordRequest body) {
        Long studentUserId = authFacade.requireStudentUserId(request);
        return Result.ok(selectionRecordService.select(studentUserId, body));
    }

    @DeleteMapping("/records/{recordId}")
    public Result<Void> drop(HttpServletRequest request,
                             @PathVariable Long recordId) {
        Long studentUserId = authFacade.requireStudentUserId(request);
        selectionRecordService.drop(studentUserId, recordId);
        return Result.ok();
    }

    @GetMapping("/records")
    public Result<List<SelectionRecordResponse>> listMy(HttpServletRequest request,
                                                        @RequestParam Long campaignId) {
        Long studentUserId = authFacade.requireStudentUserId(request);
        return Result.ok(selectionRecordService.listMy(studentUserId, campaignId));
    }
}
