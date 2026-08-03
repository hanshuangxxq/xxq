package com.xrq.xxq.module.analysis.controller;

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.xrq.xxq.common.Result;
import com.xrq.xxq.module.analysis.dto.LearningProgressDto;
import com.xrq.xxq.module.analysis.service.ProgressService;
import com.xrq.xxq.util.auth.AuthFacade;

import lombok.RequiredArgsConstructor;

/**
 * 学习进度接口：当前学期各课程完成度（派生计算）。
 * <p>学生查本人；教务查全校；院系查本院学生。
 */
@RestController
@RequestMapping("/api/analysis/progress")
@RequiredArgsConstructor
public class ProgressController {

    private final ProgressService progressService;
    private final AuthFacade authFacade;

    /** 学生查询本人学习进度。 */
    @GetMapping("/me")
    public Result<LearningProgressDto> myProgress(HttpServletRequest request) {
        Long userId = authFacade.requireStudentUserId(request);
        return Result.ok(progressService.getProgress(userId, userId, AuthFacade.USER_TYPE_STUDENT));
    }

    /** 教务/院系查询指定学生学习进度。 */
    @GetMapping("/{studentUserId}")
    public Result<LearningProgressDto> progress(HttpServletRequest request,
                                                @PathVariable Long studentUserId) {
        Long userId = authFacade.currentUserId(request);
        String userType = authFacade.currentUserType(request);
        authFacade.requireUserTypes(request,
                AuthFacade.USER_TYPE_ACADEMIC_ADMIN, AuthFacade.USER_TYPE_DEPARTMENT);
        return Result.ok(progressService.getProgress(studentUserId, userId, userType));
    }
}
