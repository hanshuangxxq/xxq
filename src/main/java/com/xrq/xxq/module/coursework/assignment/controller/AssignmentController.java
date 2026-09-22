package com.xrq.xxq.module.coursework.assignment.controller;

import java.util.List;

import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.xrq.xxq.common.PageQuery;
import com.xrq.xxq.common.PageResult;
import com.xrq.xxq.common.Result;
import com.xrq.xxq.module.coursework.assignment.dto.AssignmentSaveRequest;
import com.xrq.xxq.module.coursework.assignment.dto.AssignmentView;
import com.xrq.xxq.module.coursework.assignment.dto.CloneAssignmentRequest;
import com.xrq.xxq.module.coursework.assignment.dto.SubmissionRowView;
import com.xrq.xxq.module.coursework.assignment.dto.SubmissionView;
import com.xrq.xxq.module.coursework.assignment.dto.SubmitRequest;
import com.xrq.xxq.module.coursework.assignment.service.AssignmentService;
import com.xrq.xxq.module.coursework.assignment.service.SubmissionService;
import com.xrq.xxq.util.auth.AuthFacade;
import com.xrq.xxq.util.auth.RequireAuth;
import com.xrq.xxq.util.auth.RequireStudent;
import com.xrq.xxq.util.auth.RequireTeacher;
import com.xrq.xxq.util.auth.UserType;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;

/**
 * 课程作业端点。带文件的写操作全部 POST multipart（Tomcat 默认不解析 PUT 的 multipart）。
 */
@RestController
@RequestMapping("/api/coursework/assignments")
@RequiredArgsConstructor
public class AssignmentController {

    private final AssignmentService assignmentService;
    private final SubmissionService submissionService;
    private final AuthFacade authFacade;

    @PostMapping
    @RequireTeacher
    public Result<AssignmentView> create(HttpServletRequest request,
                                         @RequestPart("data") AssignmentSaveRequest body,
                                         @RequestPart(value = "file", required = false) MultipartFile file) {
        return Result.ok(assignmentService.create(authFacade.currentUserId(request), body, file));
    }

    @PutMapping("/{id}")
    @RequireTeacher
    public Result<AssignmentView> update(HttpServletRequest request, @PathVariable Long id,
                                         @RequestBody AssignmentSaveRequest body) {
        return Result.ok(assignmentService.update(authFacade.currentUserId(request), id, body));
    }

    /** 替换附件（POST multipart；PUT 不解析 multipart，故独立端点）。 */
    @PostMapping("/{id}/attachment")
    @RequireTeacher
    public Result<AssignmentView> replaceAttachment(HttpServletRequest request, @PathVariable Long id,
                                                    @RequestPart("data") AssignmentSaveRequest body,
                                                    @RequestPart(value = "file", required = false)
                                                    MultipartFile file) {
        return Result.ok(assignmentService.replaceAttachment(authFacade.currentUserId(request), id,
                body.getFilePath(), body.getFileOriginal(), file));
    }

    @DeleteMapping("/{id}")
    @RequireTeacher
    public Result<Void> delete(HttpServletRequest request, @PathVariable Long id) {
        assignmentService.delete(authFacade.currentUserId(request), id);
        return Result.ok();
    }

    @PostMapping("/{id}/publish")
    @RequireTeacher
    public Result<AssignmentView> publish(HttpServletRequest request, @PathVariable Long id) {
        return Result.ok(assignmentService.publish(authFacade.currentUserId(request), id));
    }

    @PostMapping("/{id}/close")
    @RequireTeacher
    public Result<AssignmentView> close(HttpServletRequest request, @PathVariable Long id) {
        return Result.ok(assignmentService.close(authFacade.currentUserId(request), id));
    }

    /** 克隆作业到本人其它授课组（题目快照一并复制，落为 DRAFT）。 */
    @PostMapping("/{id}/clone")
    @RequireTeacher
    public Result<AssignmentView> clone(HttpServletRequest request, @PathVariable Long id,
                                        @RequestBody CloneAssignmentRequest body) {
        return Result.ok(assignmentService.clone(authFacade.currentUserId(request), id, body));
    }

    /** 列表：教师看全部状态 + 提交统计；学生看已发布/已关闭 + 我的提交摘要。 */
    @GetMapping
    @RequireAuth({UserType.TEACHER, UserType.STUDENT})
    public Result<PageResult<AssignmentView>> list(HttpServletRequest request, @RequestParam Long teachInfoId,
                                                   PageQuery pageQuery) {
        Long userId = authFacade.currentUserId(request);
        if (AuthFacade.USER_TYPE_TEACHER.equals(authFacade.currentUserType(request))) {
            return Result.ok(assignmentService.listForTeacher(userId, teachInfoId, pageQuery));
        }
        return Result.ok(assignmentService.listForStudent(userId, teachInfoId, pageQuery));
    }

    @GetMapping("/{id}")
    @RequireAuth({UserType.TEACHER, UserType.STUDENT})
    public Result<AssignmentView> detail(HttpServletRequest request, @PathVariable Long id) {
        Long userId = authFacade.currentUserId(request);
        if (AuthFacade.USER_TYPE_TEACHER.equals(authFacade.currentUserType(request))) {
            return Result.ok(assignmentService.detailForTeacher(userId, id));
        }
        return Result.ok(assignmentService.detailForStudent(userId, id));
    }

    /** 教师视角提交名单（含未交学生）。 */
    @GetMapping("/{id}/submissions")
    @RequireTeacher
    public Result<List<SubmissionRowView>> submissions(HttpServletRequest request, @PathVariable Long id) {
        return Result.ok(submissionService.roster(authFacade.currentUserId(request), id));
    }

    /** 学生提交/重交。 */
    @PostMapping("/{id}/submit")
    @RequireStudent
    public Result<SubmissionView> submit(HttpServletRequest request, @PathVariable Long id,
                                         @RequestPart("data") SubmitRequest body,
                                         @RequestPart(value = "file", required = false) MultipartFile file) {
        return Result.ok(submissionService.submit(authFacade.currentUserId(request), id, body, file));
    }

    /** 我的提交（未提交返回 null data）。 */
    @GetMapping("/{id}/my-submission")
    @RequireStudent
    public Result<SubmissionView> mySubmission(HttpServletRequest request, @PathVariable Long id) {
        return Result.ok(submissionService.mySubmission(authFacade.currentUserId(request), id));
    }
}
