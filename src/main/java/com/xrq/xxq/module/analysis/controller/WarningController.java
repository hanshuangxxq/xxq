package com.xrq.xxq.module.analysis.controller;

import java.util.List;

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.xrq.xxq.common.Result;
import com.xrq.xxq.module.analysis.dto.WarningConfigDto;
import com.xrq.xxq.module.analysis.dto.WarningConfigRequest;
import com.xrq.xxq.module.analysis.dto.WarningItemDto;
import com.xrq.xxq.module.analysis.dto.WarningScanResultDto;
import com.xrq.xxq.module.analysis.entity.WarningLevelEnum;
import com.xrq.xxq.module.analysis.service.WarningService;
import com.xrq.xxq.util.auth.AuthFacade;

import lombok.RequiredArgsConstructor;

/**
 * 学业预警接口：阈值配置、扫描、看板、自查。
 * <p>扫描/配置仅教务；看板教务全校、院系本院；自查仅学生。
 */
@RestController
@RequestMapping("/api/analysis/warnings")
@RequiredArgsConstructor
public class WarningController {

    private final WarningService warningService;
    private final AuthFacade authFacade;

    /** 预警阈值配置查询（教务）。 */
    @GetMapping("/config")
    public Result<List<WarningConfigDto>> listConfig(HttpServletRequest request) {
        authFacade.requireAcademicAdmin(request);
        return Result.ok(warningService.listConfig());
    }

    /** 预警阈值配置更新（教务）。 */
    @PutMapping("/config")
    public Result<Void> updateConfig(HttpServletRequest request, @RequestBody WarningConfigRequest body) {
        authFacade.requireAcademicAdmin(request);
        warningService.updateConfig(body.getConfigs());
        return Result.ok();
    }

    /** 触发扫描：评估全体学生，upsert 预警记录并推送通知（教务）。 */
    @PostMapping("/scan")
    public Result<WarningScanResultDto> scan(HttpServletRequest request) {
        Long userId = authFacade.requireAcademicAdminUserId(request);
        return Result.ok(warningService.scan(userId));
    }

    /** 预警看板：教务全校、院系本院；按学期/级别过滤，默认当前学期生效中预警。 */
    @GetMapping
    public Result<List<WarningItemDto>> list(HttpServletRequest request,
                                             @RequestParam(required = false) Long semesterId,
                                             @RequestParam(required = false) String level) {
        AuthFacade.AuthContext ctx = authFacade.requireUserTypesContext(request,
                AuthFacade.USER_TYPE_ACADEMIC_ADMIN, AuthFacade.USER_TYPE_DEPARTMENT);
        WarningLevelEnum levelEnum = (level == null || level.isBlank())
                ? null : WarningLevelEnum.fromValue(level);
        return Result.ok(warningService.list(semesterId, levelEnum, ctx.userId(), ctx.userType()));
    }

    /** 学生查询本人生效中的预警。 */
    @GetMapping("/me")
    public Result<List<WarningItemDto>> myWarnings(HttpServletRequest request) {
        Long studentUserId = authFacade.requireStudentUserId(request);
        return Result.ok(warningService.myWarnings(studentUserId));
    }
}
