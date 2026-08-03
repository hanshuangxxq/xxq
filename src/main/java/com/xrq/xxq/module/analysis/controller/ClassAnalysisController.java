package com.xrq.xxq.module.analysis.controller;

import java.util.List;

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.xrq.xxq.common.Result;
import com.xrq.xxq.module.analysis.dto.ClassAnalysisDto;
import com.xrq.xxq.module.analysis.dto.ClassTrendDto;
import com.xrq.xxq.module.analysis.service.ClassAnalysisService;
import com.xrq.xxq.util.auth.AuthFacade;

import lombok.RequiredArgsConstructor;

/**
 * 班级/专业成绩分析接口：分组聚合 + 跨学期趋势。
 * <p>教务全校、院系本院。
 */
@RestController
@RequestMapping("/api/analysis/class-analysis")
@RequiredArgsConstructor
public class ClassAnalysisController {

    private final ClassAnalysisService classAnalysisService;
    private final AuthFacade authFacade;

    /** 按班级或专业分组聚合（默认当前学期）：均分/GPA/及格率/挂科/等级分布。 */
    @GetMapping
    public Result<List<ClassAnalysisDto>> aggregate(HttpServletRequest request,
                                                    @RequestParam(defaultValue = "class") String groupBy,
                                                    @RequestParam(required = false) Long semesterId) {
        Long userId = authFacade.currentUserId(request);
        String userType = authFacade.currentUserType(request);
        authFacade.requireUserTypes(request,
                AuthFacade.USER_TYPE_ACADEMIC_ADMIN, AuthFacade.USER_TYPE_DEPARTMENT);
        return Result.ok(classAnalysisService.aggregate(groupBy, semesterId, userId, userType));
    }

    /** 单组（班级/专业）跨学期趋势。 */
    @GetMapping("/trend")
    public Result<ClassTrendDto> trend(HttpServletRequest request,
                                       @RequestParam(defaultValue = "class") String groupBy,
                                       @RequestParam String groupKey) {
        Long userId = authFacade.currentUserId(request);
        String userType = authFacade.currentUserType(request);
        authFacade.requireUserTypes(request,
                AuthFacade.USER_TYPE_ACADEMIC_ADMIN, AuthFacade.USER_TYPE_DEPARTMENT);
        return Result.ok(classAnalysisService.trend(groupBy, groupKey, userId, userType));
    }
}
