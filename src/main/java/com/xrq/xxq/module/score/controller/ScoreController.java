package com.xrq.xxq.module.score.controller;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.xrq.xxq.common.Result;
import com.xrq.xxq.module.score.dto.ScoreBatchRequest;
import com.xrq.xxq.module.score.dto.ScoreConfigRequest;
import com.xrq.xxq.module.score.dto.ScoreEntryRequest;
import com.xrq.xxq.module.score.dto.ScoreRosterDto;
import com.xrq.xxq.module.score.dto.ScoreStatisticsDto;
import com.xrq.xxq.module.score.dto.ScoreView;
import com.xrq.xxq.module.score.entity.ScoreConfig;
import com.xrq.xxq.module.score.service.ScoreConfigService;
import com.xrq.xxq.module.score.service.ScoreExportService;
import com.xrq.xxq.module.score.service.ScoreService;
import com.xrq.xxq.module.semester.entity.Semester;
import com.xrq.xxq.util.auth.AuthFacade;

import lombok.RequiredArgsConstructor;

/**
 * 成绩管理接口。
 * <p>
 * 录入/占比配置：教师（本人课程）或教务管理员；查询：教师/院系/教务按角色 scope；学生查自己成绩。
 */
@RestController
@RequestMapping("/api/scores")
@RequiredArgsConstructor
public class ScoreController {

    private final ScoreService scoreService;
    private final ScoreConfigService scoreConfigService;
    private final ScoreExportService scoreExportService;
    private final AuthFacade authFacade;

    // ──────────────────────── 占比配置 ────────────────────────

    /** 设置平时分占比（教师本人课程或教务）。 */
    @PutMapping("/config/{teachInfoId}")
    public Result<ScoreConfig> setConfig(HttpServletRequest request,
                                         @PathVariable Long teachInfoId,
                                         @RequestBody ScoreConfigRequest body) {
        Long userId = authFacade.currentUserId(request);
        String userType = authFacade.currentUserType(request);
        authFacade.requireUserTypes(request,
                AuthFacade.USER_TYPE_TEACHER, AuthFacade.USER_TYPE_ACADEMIC_ADMIN);
        scoreService.assertCanEnterTeachInfo(teachInfoId, userId, userType);
        return Result.ok(scoreConfigService.upsert(teachInfoId, body.getRegularRatio(), userId));
    }

    /** 查询占比配置。 */
    @GetMapping("/config/{teachInfoId}")
    public Result<ScoreConfig> getConfig(HttpServletRequest request,
                                         @PathVariable Long teachInfoId) {
        Long userId = authFacade.currentUserId(request);
        String userType = authFacade.currentUserType(request);
        authFacade.requireUserTypes(request,
                AuthFacade.USER_TYPE_TEACHER, AuthFacade.USER_TYPE_ACADEMIC_ADMIN);
        scoreService.assertCanEnterTeachInfo(teachInfoId, userId, userType);
        return Result.ok(scoreConfigService.getByTeachInfo(teachInfoId));
    }

    // ──────────────────────── 名单 ────────────────────────

    /** 录入前取学生名单（教师本人课程或教务）；传 examId 时按考试排考班级过滤合班名单。 */
    @GetMapping("/roster/{teachInfoId}")
    public Result<List<ScoreRosterDto>> roster(HttpServletRequest request,
                                               @PathVariable Long teachInfoId,
                                               @RequestParam(required = false) Long examId) {
        Long userId = authFacade.currentUserId(request);
        String userType = authFacade.currentUserType(request);
        authFacade.requireUserTypes(request,
                AuthFacade.USER_TYPE_TEACHER, AuthFacade.USER_TYPE_ACADEMIC_ADMIN);
        scoreService.assertCanEnterTeachInfo(teachInfoId, userId, userType);
        return Result.ok(scoreService.roster(teachInfoId, examId));
    }

    // ──────────────────────── 录入 ────────────────────────

    /** 批量录入成绩（录入即生效；新建且不及格者自动通知）。 */
    @PostMapping
    public Result<List<ScoreView>> saveScores(HttpServletRequest request,
                                              @RequestBody ScoreBatchRequest body) {
        Long userId = authFacade.currentUserId(request);
        String userType = authFacade.currentUserType(request);
        authFacade.requireUserTypes(request,
                AuthFacade.USER_TYPE_TEACHER, AuthFacade.USER_TYPE_ACADEMIC_ADMIN);
        return Result.ok(scoreService.saveScores(body, userId, userType));
    }

    /** 修改单条成绩（未锁定）。 */
    @PutMapping("/{id}")
    public Result<ScoreView> updateScore(HttpServletRequest request,
                                         @PathVariable Long id,
                                         @RequestBody ScoreEntryRequest body) {
        Long userId = authFacade.currentUserId(request);
        String userType = authFacade.currentUserType(request);
        authFacade.requireUserTypes(request,
                AuthFacade.USER_TYPE_TEACHER, AuthFacade.USER_TYPE_ACADEMIC_ADMIN);
        return Result.ok(scoreService.updateScore(id, body, userId, userType));
    }

    // ──────────────────────── 查询 ────────────────────────

    /** 按授课安排查询成绩（教师本人/院系本院/教务全部）。 */
    @GetMapping
    public Result<List<ScoreView>> listByTeachInfo(HttpServletRequest request,
                                                   @RequestParam Long teachInfoId) {
        Long userId = authFacade.currentUserId(request);
        String userType = authFacade.currentUserType(request);
        authFacade.requireUserTypes(request,
                AuthFacade.USER_TYPE_TEACHER, AuthFacade.USER_TYPE_DEPARTMENT, AuthFacade.USER_TYPE_ACADEMIC_ADMIN);
        return Result.ok(scoreService.listByTeachInfo(teachInfoId, userId, userType));
    }

    /** 学生查询自己的成绩：默认当前学期，传 semesterId 时查指定学期。 */
    @GetMapping("/my")
    public Result<List<ScoreView>> myScores(HttpServletRequest request,
                                            @RequestParam(required = false) Long semesterId) {
        Long studentUserId = authFacade.requireStudentUserId(request);
        return Result.ok(scoreService.listMyScores(studentUserId, semesterId));
    }

    /** 学生查询自己有成绩的学期列表（用于成绩页学期切换下拉）。 */
    @GetMapping("/my/semesters")
    public Result<List<Semester>> myScoreSemesters(HttpServletRequest request) {
        Long studentUserId = authFacade.requireStudentUserId(request);
        return Result.ok(scoreService.listMyScoreSemesters(studentUserId));
    }

    // ──────────────────────── 统计 ────────────────────────

    /**
     * 成绩统计：按课程聚合分布。院系仅本院学生、教务全校；
     * 可按课程/班级/学期过滤（仅院系与教务可查）。
     */
    @GetMapping("/statistics")
    public Result<List<ScoreStatisticsDto>> statistics(HttpServletRequest request,
                                                       @RequestParam(required = false) Long courseId,
                                                       @RequestParam(required = false) String className,
                                                       @RequestParam(required = false) Long semesterId) {
        Long userId = authFacade.currentUserId(request);
        String userType = authFacade.currentUserType(request);
        authFacade.requireUserTypes(request,
                AuthFacade.USER_TYPE_DEPARTMENT, AuthFacade.USER_TYPE_ACADEMIC_ADMIN);
        return Result.ok(scoreService.statistics(courseId, className, semesterId, userId, userType));
    }

    // ──────────────────────── 导出 ────────────────────────

    /**
     * 导出成绩（Excel/PDF）。按授课安排导出学生成绩表，
     * 权限同查询（教师本人/院系本院/教务全部），format=excel|pdf。
     */
    @GetMapping("/export")
    public void export(HttpServletRequest request,
                       HttpServletResponse response,
                       @RequestParam Long teachInfoId,
                       @RequestParam(defaultValue = "excel") String format) throws IOException {
        Long userId = authFacade.currentUserId(request);
        String userType = authFacade.currentUserType(request);
        authFacade.requireUserTypes(request,
                AuthFacade.USER_TYPE_TEACHER, AuthFacade.USER_TYPE_DEPARTMENT, AuthFacade.USER_TYPE_ACADEMIC_ADMIN);
        List<ScoreView> grades = scoreService.listByTeachInfo(teachInfoId, userId, userType);
        String courseName = (grades.isEmpty() || grades.getFirst().getCourseName() == null)
                ? "成绩" : grades.getFirst().getCourseName();

        byte[] data;
        String contentType;
        String fileName;
        if ("pdf".equalsIgnoreCase(format)) {
            data = scoreExportService.exportPdf(grades, courseName + " 成绩单");
            contentType = "application/pdf";
            fileName = courseName + "-成绩单.pdf";
        } else {
            data = scoreExportService.exportExcel(grades, courseName + " 成绩单");
            contentType = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
            fileName = courseName + "-成绩单.xlsx";
        }
        String encoded = URLEncoder.encode(fileName, StandardCharsets.UTF_8).replaceAll("\\+", "%20");
        response.setContentType(contentType);
        response.setCharacterEncoding("UTF-8");
        response.setHeader("Content-Disposition", "attachment; filename*=UTF-8''" + encoded);
        response.setContentLength(data.length);
        response.getOutputStream().write(data);
    }
}
