package com.xrq.xxq.module.analysis.controller;

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

import com.xrq.xxq.common.Result;
import com.xrq.xxq.module.analysis.dto.ItemCreateRequest;
import com.xrq.xxq.module.analysis.dto.ItemResponse;
import com.xrq.xxq.module.analysis.dto.ItemUpdateRequest;
import com.xrq.xxq.module.analysis.service.EvaluationItemService;
import com.xrq.xxq.util.auth.AuthFacade;

import lombok.RequiredArgsConstructor;

/**
 * 评教指标库管理接口：教务自定义评教内容。
 * <p>权限：仅教务管理员（academic_admin）可操作。
 * 更新指标时 body 带 {@code updateTemplates=true} 可同步刷新引用该指标的模板快照。
 */
@RestController
@RequestMapping("/api/analysis/evaluation-items")
@RequiredArgsConstructor
public class EvaluationItemController {

    private final EvaluationItemService itemService;
    private final AuthFacade authFacade;

    @PostMapping
    public Result<ItemResponse> create(HttpServletRequest request,
                                       @RequestBody ItemCreateRequest body) {
        Long userId = authFacade.requireAcademicAdminUserId(request);
        return Result.ok(itemService.createItem(body, userId));
    }

    @GetMapping
    public Result<List<ItemResponse>> list(HttpServletRequest request) {
        authFacade.requireAcademicAdmin(request);
        return Result.ok(itemService.listItems());
    }

    @PutMapping("/{id}")
    public Result<ItemResponse> update(HttpServletRequest request,
                                       @PathVariable Long id,
                                       @RequestBody ItemUpdateRequest body) {
        authFacade.requireAcademicAdmin(request);
        return Result.ok(itemService.updateItem(id, body));
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(HttpServletRequest request,
                               @PathVariable Long id) {
        authFacade.requireAcademicAdmin(request);
        itemService.deleteItem(id);
        return Result.ok();
    }
}
