package com.xrq.xxq.module.exam.controller;

import java.util.List;

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.xrq.xxq.common.Result;
import com.xrq.xxq.module.exam.dto.ClassCourseOptionDto;
import com.xrq.xxq.module.exam.dto.ExamCreateRequest;
import com.xrq.xxq.module.exam.dto.ExamView;
import com.xrq.xxq.module.exam.dto.MakeupCandidateDto;
import com.xrq.xxq.module.exam.dto.MakeupExamCreateRequest;
import com.xrq.xxq.module.exam.dto.MakeupScoreEntryRequest;
import com.xrq.xxq.module.exam.entity.ExamTypeEnum;
import com.xrq.xxq.module.exam.service.ExamService;
import com.xrq.xxq.module.score.dto.ScoreView;
import com.xrq.xxq.module.score.service.ScoreService;
import com.xrq.xxq.util.auth.AuthFacade;

import lombok.RequiredArgsConstructor;

/**
 * 考试管理接口。
 * <p>
 * 安排/改/删/列表：仅教务管理员；教师查询自己课程考试；学生查询自己考试（含公选课）。
 */
@RestController
@RequestMapping("/api/exams")
@RequiredArgsConstructor
public class ExamController {

    private final ExamService examService;
    private final ScoreService scoreService;
    private final AuthFacade authFacade;

    /** 教务安排考试（期末/期中，需绑授课安排）。 */
    @PostMapping
    public Result<ExamView> create(HttpServletRequest request,
                                   @RequestBody ExamCreateRequest body) {
        Long userId = authFacade.requireAcademicAdminUserId(request);
        return Result.ok(examService.create(body, userId));
    }

    /** 教务修改考试。 */
    @PutMapping("/{id}")
    public Result<ExamView> update(HttpServletRequest request,
                                   @PathVariable Long id,
                                   @RequestBody ExamCreateRequest body) {
        Long userId = authFacade.requireAcademicAdminUserId(request);
        return Result.ok(examService.update(id, body, userId));
    }

    /** 教务删除考试。 */
    @DeleteMapping("/{id}")
    public Result<Void> delete(HttpServletRequest request,
                               @PathVariable Long id) {
        Long userId = authFacade.requireAcademicAdminUserId(request);
        examService.delete(id, userId);
        return Result.ok();
    }

    /** 教务查询考试列表（可按学期/课程/类型过滤；source=SELECTION_CAMPAIGN 时按公选课过滤）。 */
    @GetMapping
    public Result<List<ExamView>> list(HttpServletRequest request,
                                       @RequestParam(required = false) Long semesterId,
                                       @RequestParam(required = false) Long courseId,
                                       @RequestParam(required = false) String source,
                                       @RequestParam(required = false) ExamTypeEnum examType) {
        authFacade.requireAcademicAdmin(request);
        return Result.ok(examService.list(semesterId, courseId, source, examType));
    }

    /** 教务按班级查询可排考的课程（建考用，合班自动命中）。 */
    @GetMapping("/class-courses")
    public Result<List<ClassCourseOptionDto>> listClassCourses(
            HttpServletRequest request,
            @RequestParam Long classId) {
        authFacade.requireAcademicAdmin(request);
        return Result.ok(examService.listClassCourseOptions(classId));
    }

    /** 教师查询自己课程相关的考试。 */
    @GetMapping("/teacher")
    public Result<List<ExamView>> listForTeacher(HttpServletRequest request) {
        Long userId = authFacade.currentUserId(request);
        authFacade.requireTeacher(request);
        return Result.ok(examService.listForTeacher(userId));
    }

    /** 学生查询自己的考试（常规考试含公选课 ∪ 补考/重修）。 */
    @GetMapping("/my")
    public Result<List<ExamView>> myExams(HttpServletRequest request) {
        Long studentUserId = authFacade.requireStudentUserId(request);
        return Result.ok(examService.listMyExams(studentUserId));
    }

    // ──────────────────────── 补考/重修 ────────────────────────

    /** 教务查询不及格学生名单（补考候选，自动生成；source=SELECTION_CAMPAIGN 时按公选课过滤）。 */
    @GetMapping("/makeup/candidates")
    public Result<List<MakeupCandidateDto>> makeupCandidates(HttpServletRequest request,
                                                             @RequestParam Long courseId,
                                                             @RequestParam(required = false) String source,
                                                             @RequestParam(required = false) Long semesterId) {
        authFacade.requireAcademicAdmin(request);
        return Result.ok(examService.listMakeupCandidates(courseId, source, semesterId));
    }

    /** 教务建补考/重修考试（按不及格名单自动生成考生）。 */
    @PostMapping("/makeup")
    public Result<ExamView> createMakeupExam(HttpServletRequest request,
                                             @RequestBody MakeupExamCreateRequest body) {
        Long userId = authFacade.requireAcademicAdminUserId(request);
        return Result.ok(examService.createMakeupExam(body, userId));
    }

    /** 教务查询补考/重修考试列表。 */
    @GetMapping("/makeup")
    public Result<List<ExamView>> listMakeupExams(HttpServletRequest request,
                                                  @RequestParam(required = false) Long semesterId) {
        authFacade.requireAcademicAdmin(request);
        return Result.ok(examService.listMakeupExams(semesterId));
    }

    /** 录入补考/重修成绩（教师本人或教务）。 */
    @PostMapping("/{examId}/grades")
    public Result<List<ScoreView>> enterMakeupScores(HttpServletRequest request,
                                                     @PathVariable Long examId,
                                                     @RequestBody List<MakeupScoreEntryRequest> body) {
        Long userId = authFacade.currentUserId(request);
        String userType = authFacade.currentUserType(request);
        authFacade.requireUserTypes(request,
                AuthFacade.USER_TYPE_TEACHER, AuthFacade.USER_TYPE_ACADEMIC_ADMIN);
        return Result.ok(scoreService.enterMakeupScore(examId, body, userId, userType));
    }
}
