package com.xrq.xxq.module.selection.controller;

import java.util.List;

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.xrq.xxq.common.BusinessException;
import com.xrq.xxq.common.Result;
import com.xrq.xxq.module.selection.dto.SelectionClassResponse;
import com.xrq.xxq.module.selection.service.SelectionClassService;

import lombok.RequiredArgsConstructor;

/**
 * 选课分班结果查询接口。
 * <p>
 * 权限：仅教务管理员（academic_admin）可查看。
 */
@RestController
@RequestMapping("/api/selection/campaigns/{campaignId}/classes")
@RequiredArgsConstructor
public class SelectionClassController {

    private final SelectionClassService selectionClassService;

    @GetMapping
    public Result<List<SelectionClassResponse>> list(HttpServletRequest request,
                                                     @PathVariable Long campaignId) {
        checkAcademicAdmin(request);
        return Result.ok(selectionClassService.listByCampaign(campaignId));
    }

    private void checkAcademicAdmin(HttpServletRequest request) {
        String userType = (String) request.getAttribute("userType");
        if (!"academic_admin".equals(userType)) {
            throw new BusinessException(403, "仅教务管理员可查看分班结果");
        }
    }
}
