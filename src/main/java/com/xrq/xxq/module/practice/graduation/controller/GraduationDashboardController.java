package com.xrq.xxq.module.practice.graduation.controller;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.net.URLEncoder;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.xrq.xxq.common.PageQuery;
import com.xrq.xxq.common.PageResult;
import com.xrq.xxq.common.Result;
import com.xrq.xxq.module.practice.graduation.dto.DashboardRow;
import com.xrq.xxq.module.practice.graduation.dto.OperationLogResponse;
import com.xrq.xxq.module.practice.graduation.service.GraduationDashboardService;
import com.xrq.xxq.module.practice.graduation.service.GraduationDashboardService.ExportFile;
import com.xrq.xxq.module.practice.graduation.service.GraduationLogService;
import com.xrq.xxq.util.auth.AuthFacade;
import com.xrq.xxq.util.auth.AuthFacade.AuthContext;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;

/**
 * 教务/院系看板与导出（R-5.8~R-5.11）。
 */
@RestController
@RequestMapping("/api/practice/graduation/dashboard")
@RequiredArgsConstructor
public class GraduationDashboardController {

    private final GraduationDashboardService dashboardService;
    private final GraduationLogService logService;
    private final AuthFacade authFacade;

    /** 看板分页（R-5.8/R-5.9，教务全校 / 院系本院系） */
    @GetMapping("/{campaignId:\\d+}")
    public Result<PageResult<DashboardRow>> list(HttpServletRequest request, @PathVariable Long campaignId,
                                                 @RequestParam(required = false) String status,
                                                 @RequestParam(required = false) String keyword,
                                                 @RequestParam(required = false) Long collegeId,
                                                 PageQuery pageQuery) {
        AuthContext ctx = authFacade.requireUserTypesContext(request,
                AuthFacade.USER_TYPE_ACADEMIC_ADMIN, AuthFacade.USER_TYPE_DEPARTMENT);
        return Result.ok(dashboardService.listDashboard(campaignId, status, keyword, collegeId,
                ctx.userType(), ctx.userId(), pageQuery));
    }

    /** 看板导出（R-5.10 xlsx/csv，导出动作记日志） */
    @GetMapping("/{campaignId:\\d+}/export")
    public ResponseEntity<byte[]> export(HttpServletRequest request, @PathVariable Long campaignId,
                                         @RequestParam(defaultValue = "xlsx") String format,
                                         @RequestParam(required = false) String status,
                                         @RequestParam(required = false) String keyword,
                                         @RequestParam(required = false) Long collegeId) throws IOException {
        AuthContext ctx = authFacade.requireUserTypesContext(request,
                AuthFacade.USER_TYPE_ACADEMIC_ADMIN, AuthFacade.USER_TYPE_DEPARTMENT);
        ExportFile file = dashboardService.exportDashboard(campaignId, format, status, keyword, collegeId,
                ctx.userType(), ctx.userId(), ctx.userId(), ctx.userType());
        String encoded = URLEncoder.encode(file.fileName(), StandardCharsets.UTF_8).replace("+", "%20");
        MediaType mediaType = "csv".equals(format)
                ? new MediaType("text", "csv", StandardCharsets.UTF_8)
                : new MediaType("application", "vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename*=UTF-8''" + encoded)
                .contentType(mediaType)
                .body(file.data());
    }

    /** 操作日志（R-10.4，教务） */
    @GetMapping("/{campaignId:\\d+}/logs")
    public Result<PageResult<OperationLogResponse>> logs(HttpServletRequest request, @PathVariable Long campaignId,
                                                         PageQuery pageQuery) {
        authFacade.requireAcademicAdmin(request);
        return Result.ok(logService.listLogs(campaignId, pageQuery));
    }
}
