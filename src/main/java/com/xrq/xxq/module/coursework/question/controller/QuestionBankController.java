package com.xrq.xxq.module.coursework.question.controller;

import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.xrq.xxq.common.PageQuery;
import com.xrq.xxq.common.PageResult;
import com.xrq.xxq.common.Result;
import com.xrq.xxq.module.coursework.common.QuestionTypeEnum;
import com.xrq.xxq.module.coursework.question.dto.QuestionSaveRequest;
import com.xrq.xxq.module.coursework.question.dto.QuestionView;
import com.xrq.xxq.module.coursework.question.service.QuestionBankService;
import com.xrq.xxq.util.auth.AuthFacade;
import com.xrq.xxq.util.auth.RequireTeacher;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;

/** 教师个人题库端点。 */
@RestController
@RequestMapping("/api/coursework/question-bank")
@RequiredArgsConstructor
@RequireTeacher
public class QuestionBankController {

    private final QuestionBankService questionBankService;
    private final AuthFacade authFacade;

    @GetMapping
    public Result<PageResult<QuestionView>> list(HttpServletRequest request,
                                                 @RequestParam(required = false) Long courseId,
                                                 @RequestParam(required = false) Long campaignId,
                                                 @RequestParam(required = false) QuestionTypeEnum type,
                                                 @RequestParam(required = false) String keyword,
                                                 @RequestParam(required = false) Integer page,
                                                 @RequestParam(required = false) Integer pageSize) {
        PageQuery pageQuery = new PageQuery(page, pageSize);
        return Result.ok(questionBankService.list(authFacade.currentUserId(request),
                courseId, campaignId, type, keyword, pageQuery));
    }

    @PostMapping
    public Result<QuestionView> create(HttpServletRequest request,
                                       @RequestBody QuestionSaveRequest body) {
        return Result.ok(questionBankService.create(authFacade.currentUserId(request), body));
    }

    @PutMapping("/{id}")
    public Result<QuestionView> update(HttpServletRequest request, @PathVariable Long id,
                                       @RequestBody QuestionSaveRequest body) {
        return Result.ok(questionBankService.update(authFacade.currentUserId(request), id, body));
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(HttpServletRequest request, @PathVariable Long id) {
        questionBankService.delete(authFacade.currentUserId(request), id);
        return Result.ok();
    }
}
