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
import com.xrq.xxq.module.selection.dto.SelectionCourseAddRequest;
import com.xrq.xxq.module.selection.dto.SelectionCourseResponse;
import com.xrq.xxq.module.selection.entity.SelectionCourse;
import com.xrq.xxq.module.selection.service.SelectionCourseService;
import com.xrq.xxq.util.auth.AuthFacade;

import lombok.RequiredArgsConstructor;

/**
 * 选课活动可选课程管理接口。
 * <p>
 * 每条可选课程记录本身就是一门独立的课程，并在 course 表生成衍生记录（source = SELECTION_COURSE），
 * 同时支持绑定多个 TimeRestriction（RESERVED 类型）作为排课备选时段。
 */
@RestController
@RequestMapping("/api/selection/campaigns/{campaignId}/courses")
@RequiredArgsConstructor
public class SelectionCourseController {

    private final SelectionCourseService selectionCourseService;
    private final AuthFacade authFacade;

    @PostMapping
    public Result<SelectionCourse> add(HttpServletRequest httpRequest,
                                       @PathVariable Long campaignId,
                                       @RequestBody SelectionCourseAddRequest request) {
        authFacade.requireAcademicAdmin(httpRequest);
        return Result.ok(selectionCourseService.add(campaignId, request));
    }

    @GetMapping
    public Result<List<SelectionCourseResponse>> list(@PathVariable Long campaignId,
                                                      HttpServletRequest request) {
        authFacade.requireAcademicAdmin(request);
        return Result.ok(selectionCourseService.listByCampaign(campaignId));
    }

    @DeleteMapping("/{selectionCourseId}")
    public Result<Void> remove(HttpServletRequest httpRequest,
                               @PathVariable Long campaignId,
                               @PathVariable Long selectionCourseId) {
        authFacade.requireAcademicAdmin(httpRequest);
        selectionCourseService.remove(campaignId, selectionCourseId);
        return Result.ok();
    }
}
