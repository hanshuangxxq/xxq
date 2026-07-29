package com.xrq.xxq.module.selection.controller;

import java.util.List;

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.xrq.xxq.common.Result;
import com.xrq.xxq.module.selection.dto.AssignTeacherRequest;
import com.xrq.xxq.module.selection.dto.SelectionClassResponse;
import com.xrq.xxq.module.selection.service.SelectionClassService;
import com.xrq.xxq.util.auth.AuthFacade;

import lombok.RequiredArgsConstructor;

/**
 * 选课分班结果管理接口。
 * <p>
 * 权限：仅教务管理员（academic_admin）可操作。
 */
@RestController
@RequestMapping("/api/selection/campaigns/{campaignId}/classes")
@RequiredArgsConstructor
public class SelectionClassController {

    private final SelectionClassService selectionClassService;
    private final AuthFacade authFacade;

    @GetMapping
    public Result<List<SelectionClassResponse>> list(HttpServletRequest request,
                                                     @PathVariable Long campaignId) {
        authFacade.requireAcademicAdmin(request);
        return Result.ok(selectionClassService.listByCampaign(campaignId));
    }

    /**
     * 为指定选课班分配（或取消分配）任课教师。
     * <p>
     * 请求体 {@code teacherId} 为 null 表示取消已分配的教师。
     */
    @PutMapping("/{classId}/teacher")
    public Result<SelectionClassResponse> assignTeacher(HttpServletRequest request,
                                                       @PathVariable Long campaignId,
                                                       @PathVariable Long classId,
                                                       @RequestBody AssignTeacherRequest body) {
        authFacade.requireAcademicAdmin(request);
        return Result.ok(selectionClassService.assignTeacher(campaignId, classId, body.getTeacherId()));
    }
}
