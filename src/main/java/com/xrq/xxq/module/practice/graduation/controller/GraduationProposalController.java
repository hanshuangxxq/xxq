package com.xrq.xxq.module.practice.graduation.controller;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.xrq.xxq.common.Result;
import com.xrq.xxq.module.practice.graduation.dto.ProposalDeclareRequest;
import com.xrq.xxq.module.practice.graduation.dto.ProposalResponse;
import com.xrq.xxq.module.practice.graduation.dto.ProposalReviewRequest;
import com.xrq.xxq.module.practice.graduation.entity.ProposalReviewStageEnum;
import com.xrq.xxq.module.practice.graduation.service.GraduationProposalService;
import com.xrq.xxq.util.auth.AuthFacade;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;

/**
 * 选题申请与两级审批（§5：学生自拟 + 院系初审 + 教务终审）。
 */
@RestController
@RequestMapping("/api/practice/graduation/proposals")
@RequiredArgsConstructor
public class GraduationProposalController {

    private final GraduationProposalService proposalService;
    private final AuthFacade authFacade;

    /** 学生提交/重提选题申请（R-5.1~R-5.4） */
    @PostMapping
    public Result<ProposalResponse> declare(HttpServletRequest request, @RequestBody ProposalDeclareRequest body) {
        Long studentUserId = authFacade.requireStudentUserId(request);
        return Result.ok(proposalService.declareProposal(studentUserId, body));
    }

    /** 院系初审（R-5.5，仅本院系学生） */
    @PutMapping("/{id:\\d+}/review/dept")
    public Result<ProposalResponse> reviewDept(HttpServletRequest request, @PathVariable Long id,
                                               @RequestBody ProposalReviewRequest body) {
        Long deptUserId = authFacade.requireDepartmentUserId(request);
        return Result.ok(proposalService.reviewProposal(deptUserId, AuthFacade.USER_TYPE_DEPARTMENT,
                id, ProposalReviewStageEnum.DEPT, body));
    }

    /** 教务终审（R-5.6） */
    @PutMapping("/{id:\\d+}/review/academic")
    public Result<ProposalResponse> reviewAcademic(HttpServletRequest request, @PathVariable Long id,
                                                   @RequestBody ProposalReviewRequest body) {
        Long academicUserId = authFacade.requireAcademicAdminUserId(request);
        return Result.ok(proposalService.reviewProposal(academicUserId, AuthFacade.USER_TYPE_ACADEMIC_ADMIN,
                id, ProposalReviewStageEnum.ACADEMIC, body));
    }

    /** 学生查看我的申请列表 */
    @GetMapping("/my")
    public Result<List<ProposalResponse>> my(HttpServletRequest request) {
        Long studentUserId = authFacade.requireStudentUserId(request);
        return Result.ok(proposalService.listMyProposals(studentUserId));
    }

    /** 院系待初审队列（本院系） */
    @GetMapping("/pending/dept")
    public Result<List<ProposalResponse>> pendingDept(HttpServletRequest request,
                                                      @RequestParam Long campaignId) {
        Long deptUserId = authFacade.requireDepartmentUserId(request);
        return Result.ok(proposalService.listPendingDept(deptUserId, campaignId));
    }

    /** 教务待终审队列 */
    @GetMapping("/pending/academic")
    public Result<List<ProposalResponse>> pendingAcademic(HttpServletRequest request,
                                                          @RequestParam Long campaignId) {
        authFacade.requireAcademicAdmin(request);
        return Result.ok(proposalService.listPendingAcademic(campaignId));
    }
}
