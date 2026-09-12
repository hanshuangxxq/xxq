package com.xrq.xxq.module.analysis.controller;

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.xrq.xxq.common.Result;
import com.xrq.xxq.module.analysis.dto.StudentProfileDto;
import com.xrq.xxq.module.analysis.service.StudentProfileService;
import com.xrq.xxq.util.auth.AuthFacade;
import com.xrq.xxq.util.auth.RequireAuth;
import com.xrq.xxq.util.auth.UserType;

import lombok.RequiredArgsConstructor;

/**
 * 学生个人画像接口。
 * <p>学生查本人；教务查全校；院系查本院学生。
 */
@RestController
@RequestMapping("/api/analysis/profile")
@RequiredArgsConstructor
public class StudentProfileController {

    private final StudentProfileService studentProfileService;
    private final AuthFacade authFacade;

    /** 学生查询本人画像。 */
    @GetMapping("/me")
    @RequireAuth(UserType.STUDENT)
    public Result<StudentProfileDto> myProfile(HttpServletRequest request) {
        Long userId = authFacade.currentUserId(request);
        return Result.ok(studentProfileService.getProfile(userId, userId, AuthFacade.USER_TYPE_STUDENT));
    }

    /** 教务/院系查询指定学生画像。 */
    @GetMapping("/{studentUserId}")
    @RequireAuth({UserType.ACADEMIC_ADMIN, UserType.DEPARTMENT})
    public Result<StudentProfileDto> profile(HttpServletRequest request,
                                             @PathVariable Long studentUserId) {
        Long userId = authFacade.currentUserId(request);
        String userType = authFacade.currentUserType(request);
        return Result.ok(studentProfileService.getProfile(studentUserId, userId, userType));
    }
}
