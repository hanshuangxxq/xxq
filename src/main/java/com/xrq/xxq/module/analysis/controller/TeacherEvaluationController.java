package com.xrq.xxq.module.analysis.controller;

import java.util.List;

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.xrq.xxq.common.Result;
import com.xrq.xxq.module.analysis.dto.EvaluationStatusDto;
import com.xrq.xxq.module.analysis.dto.EvaluationSubmitRequest;
import com.xrq.xxq.module.analysis.dto.EvaluationTemplateView;
import com.xrq.xxq.module.analysis.dto.TeacherQualityDto;
import com.xrq.xxq.module.analysis.dto.TeachingEvaluationView;
import com.xrq.xxq.module.analysis.service.EvaluationTemplateService;
import com.xrq.xxq.module.analysis.service.TeachingEvaluationService;
import com.xrq.xxq.util.auth.AuthFacade;

import lombok.RequiredArgsConstructor;

/**
 * 教师教学质量评估接口：评教提交/自查、教师质量查询与对比。
 * <p>评教仅学生；教师质量：教务全校、院系本院、教师本人。
 */
@RestController
@RequestMapping("/api/analysis")
@RequiredArgsConstructor
public class TeacherEvaluationController {

    private final TeachingEvaluationService evaluationService;
    private final EvaluationTemplateService evaluationTemplateService;
    private final AuthFacade authFacade;

    /** 学生取评教表单（解析课程所用模板：课程覆盖优先，否则全局默认）。任意登录用户可查看。 */
    @GetMapping("/evaluations/form")
    public Result<EvaluationTemplateView> evaluationForm(HttpServletRequest request,
                                                         @RequestParam Long teachInfoId) {
        authFacade.currentUserId(request);
        return Result.ok(evaluationTemplateService.getEvaluationForm(teachInfoId));
    }

    /** 学生提交评教（一人一授课安排一条，重复为更新）。 */
    @PostMapping("/evaluations")
    public Result<TeachingEvaluationView> submit(HttpServletRequest request,
                                                 @RequestBody EvaluationSubmitRequest body) {
        Long studentUserId = authFacade.requireStudentUserId(request);
        return Result.ok(evaluationService.submit(body, studentUserId));
    }

    /** 学生查询本人已提交的评教。 */
    @GetMapping("/evaluations/my")
    public Result<List<TeachingEvaluationView>> myEvaluations(HttpServletRequest request) {
        Long studentUserId = authFacade.requireStudentUserId(request);
        return Result.ok(evaluationService.myEvaluations(studentUserId));
    }

    /** 教务开启当前学期评教周期（统一触发）。 */
    @PostMapping("/evaluations/period/open")
    public Result<EvaluationStatusDto> openPeriod(HttpServletRequest request) {
        Long userId = authFacade.requireAcademicAdminUserId(request);
        return Result.ok(evaluationService.openPeriod(userId));
    }

    /** 教务关闭当前学期评教周期。 */
    @PostMapping("/evaluations/period/close")
    public Result<EvaluationStatusDto> closePeriod(HttpServletRequest request) {
        Long userId = authFacade.requireAcademicAdminUserId(request);
        return Result.ok(evaluationService.closePeriod(userId));
    }

    /** 查询当前学期评教周期状态（学生评教页用；未开放返回 message=暂无评教，开放时附带可评课程列表）。 */
    @GetMapping("/evaluations/period")
    public Result<EvaluationStatusDto> periodStatus(HttpServletRequest request) {
        Long userId = authFacade.currentUserId(request);
        String userType = authFacade.currentUserType(request);
        return Result.ok(evaluationService.getPeriodStatus(userId, userType));
    }

    /** 教师查询本人教学质量。 */
    @GetMapping("/teacher-quality/me")
    public Result<TeacherQualityDto> myQuality(HttpServletRequest request,
                                               @RequestParam(required = false) Long semesterId) {
        authFacade.requireTeacher(request);
        Long userId = authFacade.currentUserId(request);
        return Result.ok(evaluationService.myTeacherQuality(userId, semesterId));
    }

    /** 查询指定教师质量（教务/院系/教师本人）。 */
    @GetMapping("/teacher-quality/{teacherId}")
    public Result<TeacherQualityDto> quality(HttpServletRequest request,
                                             @PathVariable Long teacherId,
                                             @RequestParam(required = false) Long semesterId) {
        AuthFacade.AuthContext ctx = authFacade.requireUserTypesContext(request,
                AuthFacade.USER_TYPE_ACADEMIC_ADMIN, AuthFacade.USER_TYPE_DEPARTMENT, AuthFacade.USER_TYPE_TEACHER);
        return Result.ok(evaluationService.teacherQuality(teacherId, semesterId, ctx.userId(), ctx.userType()));
    }

    /** 教师质量列表/对比（教务全校、院系本院），可按学期过滤。 */
    @GetMapping("/teacher-quality")
    public Result<List<TeacherQualityDto>> list(HttpServletRequest request,
                                                @RequestParam(required = false) Long semesterId) {
        AuthFacade.AuthContext ctx = authFacade.requireUserTypesContext(request,
                AuthFacade.USER_TYPE_ACADEMIC_ADMIN, AuthFacade.USER_TYPE_DEPARTMENT);
        return Result.ok(evaluationService.listTeacherQuality(semesterId, ctx.userId(), ctx.userType()));
    }
}
