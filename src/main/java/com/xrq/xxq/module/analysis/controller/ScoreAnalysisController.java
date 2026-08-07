package com.xrq.xxq.module.analysis.controller;

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.xrq.xxq.common.Result;
import com.xrq.xxq.module.analysis.dto.ScoreComparisonDto;
import com.xrq.xxq.module.analysis.dto.ScoreDistributionDto;
import com.xrq.xxq.module.analysis.dto.ScoreTrendDto;
import com.xrq.xxq.module.analysis.service.ScoreAnalysisService;
import com.xrq.xxq.util.auth.AuthFacade;

import lombok.RequiredArgsConstructor;

/**
 * 成绩分析接口：分数段分布、跨学期趋势、班级横向对比。
 * <p>教务全校；院系本院；教师本课程。
 */
@RestController
@RequestMapping("/api/analysis/scores")
@RequiredArgsConstructor
public class ScoreAnalysisController {

    private final ScoreAnalysisService scoreAnalysisService;
    private final AuthFacade authFacade;

    /** 分数段分布：[0-59][60-69][70-79][80-89][90-100] + 均值/及格率/标准差。source=SELECTION_CAMPAIGN 时按公选课过滤。 */
    @GetMapping("/distribution")
    public Result<ScoreDistributionDto> distribution(HttpServletRequest request,
                                                     @RequestParam Long courseId,
                                                     @RequestParam(required = false) String source,
                                                     @RequestParam(required = false) String className,
                                                     @RequestParam(required = false) Long semesterId) {
        Long userId = authFacade.currentUserId(request);
        String userType = authFacade.currentUserType(request);
        authFacade.requireUserTypes(request,
                AuthFacade.USER_TYPE_TEACHER, AuthFacade.USER_TYPE_DEPARTMENT, AuthFacade.USER_TYPE_ACADEMIC_ADMIN);
        return Result.ok(scoreAnalysisService.distribution(courseId, source, className, semesterId, userId, userType));
    }

    /** 课程成绩跨学期趋势。source=SELECTION_CAMPAIGN 时按公选课过滤。 */
    @GetMapping("/trend")
    public Result<ScoreTrendDto> trend(HttpServletRequest request,
                                       @RequestParam Long courseId,
                                       @RequestParam(required = false) String source,
                                       @RequestParam(required = false) String className) {
        Long userId = authFacade.currentUserId(request);
        String userType = authFacade.currentUserType(request);
        authFacade.requireUserTypes(request,
                AuthFacade.USER_TYPE_TEACHER, AuthFacade.USER_TYPE_DEPARTMENT, AuthFacade.USER_TYPE_ACADEMIC_ADMIN);
        return Result.ok(scoreAnalysisService.trend(courseId, source, className, userId, userType));
    }

    /** 同课程各班级成绩横向对比（默认当前学期）。source=SELECTION_CAMPAIGN 时按公选课过滤。 */
    @GetMapping("/comparison")
    public Result<ScoreComparisonDto> comparison(HttpServletRequest request,
                                                 @RequestParam Long courseId,
                                                 @RequestParam(required = false) String source,
                                                 @RequestParam(required = false) Long semesterId) {
        Long userId = authFacade.currentUserId(request);
        String userType = authFacade.currentUserType(request);
        authFacade.requireUserTypes(request,
                AuthFacade.USER_TYPE_TEACHER, AuthFacade.USER_TYPE_DEPARTMENT, AuthFacade.USER_TYPE_ACADEMIC_ADMIN);
        return Result.ok(scoreAnalysisService.comparison(courseId, source, semesterId, userId, userType));
    }
}
