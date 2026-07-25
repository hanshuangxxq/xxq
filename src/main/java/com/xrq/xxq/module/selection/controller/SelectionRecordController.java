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

import com.xrq.xxq.common.BusinessException;
import com.xrq.xxq.common.Result;
import com.xrq.xxq.module.selection.dto.CampaignResponse;
import com.xrq.xxq.module.selection.dto.SelectionRecordRequest;
import com.xrq.xxq.module.selection.dto.SelectionRecordResponse;
import com.xrq.xxq.module.selection.dto.StudentCourseGroupResponse;
import com.xrq.xxq.module.selection.entity.CampaignStatusEnum;
import com.xrq.xxq.module.selection.service.SelectionCampaignService;
import com.xrq.xxq.module.selection.service.SelectionRecordService;

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
    private final SelectionCampaignService campaignService;

    @GetMapping("/campaigns")
    public Result<List<CampaignResponse>> listOpenCampaigns(HttpServletRequest request) {
        checkStudent(request);
        List<CampaignResponse> open = campaignService.listAll().stream()
                .filter(c -> c.getStatus() == CampaignStatusEnum.OPEN)
                .toList();
        return Result.ok(open);
    }

    @GetMapping("/campaigns/{campaignId}/courses")
    public Result<List<StudentCourseGroupResponse>> listCourses(HttpServletRequest request,
                                                                @PathVariable Long campaignId) {
        Long studentUserId = currentStudentUserId(request);
        return Result.ok(selectionRecordService.listCampaignCoursesForStudent(campaignId, studentUserId));
    }

    @PostMapping("/records")
    public Result<SelectionRecordResponse> select(HttpServletRequest request,
                                                  @RequestBody SelectionRecordRequest body) {
        Long studentUserId = currentStudentUserId(request);
        return Result.ok(selectionRecordService.select(studentUserId, body));
    }

    @DeleteMapping("/records/{recordId}")
    public Result<Void> drop(HttpServletRequest request,
                             @PathVariable Long recordId) {
        Long studentUserId = currentStudentUserId(request);
        selectionRecordService.drop(studentUserId, recordId);
        return Result.ok();
    }

    @GetMapping("/records")
    public Result<List<SelectionRecordResponse>> listMy(HttpServletRequest request,
                                                        @RequestParam Long campaignId) {
        Long studentUserId = currentStudentUserId(request);
        return Result.ok(selectionRecordService.listMy(studentUserId, campaignId));
    }

    private Long currentStudentUserId(HttpServletRequest request) {
        checkStudent(request);
        return (Long) request.getAttribute("userId");
    }

    private void checkStudent(HttpServletRequest request) {
        String userType = (String) request.getAttribute("userType");
        if (!"student".equals(userType)) {
            throw new BusinessException(403, "仅学生可操作选课记录");
        }
    }
}
