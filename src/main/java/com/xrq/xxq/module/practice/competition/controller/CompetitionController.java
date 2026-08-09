package com.xrq.xxq.module.practice.competition.controller;

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

import com.xrq.xxq.common.PageQuery;
import com.xrq.xxq.common.PageResult;
import com.xrq.xxq.common.Result;
import com.xrq.xxq.module.practice.competition.dto.CompetitionCreateRequest;
import com.xrq.xxq.module.practice.competition.dto.CompetitionResponse;
import com.xrq.xxq.module.practice.competition.dto.CompetitionResultRequest;
import com.xrq.xxq.module.practice.competition.dto.CompetitionResultResponse;
import com.xrq.xxq.module.practice.competition.dto.CompetitionUpdateRequest;
import com.xrq.xxq.module.practice.competition.dto.RegistrationRequest;
import com.xrq.xxq.module.practice.competition.dto.RegistrationResponse;
import com.xrq.xxq.module.practice.competition.dto.RegistrationReviewRequest;
import com.xrq.xxq.module.practice.competition.entity.CompetitionStatusEnum;
import com.xrq.xxq.module.practice.competition.service.CompetitionService;
import com.xrq.xxq.util.auth.AuthFacade;

import lombok.RequiredArgsConstructor;

/**
 * 竞赛管理接口。
 * <p>
 * 发布/更新/状态/删除/审核/录结果/查看全部：教务；报名/撤销/我的报名/我的结果：学生。
 */
@RestController
@RequestMapping("/api/practice/competitions")
@RequiredArgsConstructor
public class CompetitionController {

    private final CompetitionService competitionService;
    private final AuthFacade authFacade;

    @PostMapping
    public Result<CompetitionResponse> create(HttpServletRequest request, @RequestBody CompetitionCreateRequest body) {
        authFacade.requireAcademicAdmin(request);
        return Result.ok(competitionService.createCompetition(body));
    }

    @PutMapping("/{id}")
    public Result<CompetitionResponse> update(HttpServletRequest request, @PathVariable Long id,
                                              @RequestBody CompetitionUpdateRequest body) {
        authFacade.requireAcademicAdmin(request);
        return Result.ok(competitionService.updateCompetition(id, body));
    }

    @PutMapping("/{id}/status")
    public Result<Void> changeStatus(HttpServletRequest request, @PathVariable Long id,
                                     @RequestParam CompetitionStatusEnum status) {
        authFacade.requireAcademicAdmin(request);
        competitionService.changeCompetitionStatus(id, status);
        return Result.ok();
    }

    @GetMapping
    public Result<PageResult<CompetitionResponse>> list(HttpServletRequest request,
                                                        @RequestParam(required = false) CompetitionStatusEnum status,
                                                        @RequestParam(required = false) Integer page,
                                                        @RequestParam(required = false) Integer pageSize) {
        authFacade.requireAcademicAdmin(request);
        return Result.ok(competitionService.listCompetitions(status, new PageQuery(page, pageSize)));
    }

    @GetMapping("/{id}")
    public Result<CompetitionResponse> get(@PathVariable Long id) {
        return Result.ok(competitionService.getCompetition(id));
    }

    @GetMapping("/available")
    public Result<List<CompetitionResponse>> listAvailable(HttpServletRequest request) {
        Long studentUserId = authFacade.requireStudentUserId(request);
        return Result.ok(competitionService.listAvailableCompetitions(studentUserId));
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(HttpServletRequest request, @PathVariable Long id) {
        authFacade.requireAcademicAdmin(request);
        competitionService.deleteCompetition(id);
        return Result.ok();
    }

    @PostMapping("/registrations")
    public Result<RegistrationResponse> register(HttpServletRequest request, @RequestBody RegistrationRequest body) {
        Long studentUserId = authFacade.requireStudentUserId(request);
        return Result.ok(competitionService.register(studentUserId, body));
    }

    @DeleteMapping("/registrations/{id}")
    public Result<Void> cancel(HttpServletRequest request, @PathVariable Long id) {
        Long studentUserId = authFacade.requireStudentUserId(request);
        competitionService.cancelRegistration(studentUserId, id);
        return Result.ok();
    }

    @PostMapping("/registrations/{id}/review")
    public Result<RegistrationResponse> review(HttpServletRequest request, @PathVariable Long id,
                                               @RequestBody RegistrationReviewRequest body) {
        authFacade.requireAcademicAdmin(request);
        return Result.ok(competitionService.reviewRegistration(id, body));
    }

    @GetMapping("/registrations/my")
    public Result<List<RegistrationResponse>> myRegistrations(HttpServletRequest request) {
        Long studentUserId = authFacade.requireStudentUserId(request);
        return Result.ok(competitionService.listMyRegistrations(studentUserId));
    }

    @GetMapping("/{competitionId}/registrations")
    public Result<PageResult<RegistrationResponse>> registrationsByCompetition(HttpServletRequest request,
                                                                              @PathVariable Long competitionId,
                                                                              @RequestParam(required = false) Integer page,
                                                                              @RequestParam(required = false) Integer pageSize) {
        authFacade.requireAcademicAdmin(request);
        return Result.ok(competitionService.listRegistrationsByCompetition(competitionId, new PageQuery(page, pageSize)));
    }

    @PostMapping("/results")
    public Result<CompetitionResultResponse> recordResult(HttpServletRequest request,
                                                          @RequestBody CompetitionResultRequest body) {
        authFacade.requireAcademicAdmin(request);
        return Result.ok(competitionService.recordResult(body));
    }

    @DeleteMapping("/results/{id}")
    public Result<Void> deleteResult(HttpServletRequest request, @PathVariable Long id) {
        authFacade.requireAcademicAdmin(request);
        competitionService.deleteResult(id);
        return Result.ok();
    }

    @GetMapping("/{competitionId}/results")
    public Result<List<CompetitionResultResponse>> results(@PathVariable Long competitionId) {
        return Result.ok(competitionService.listResults(competitionId));
    }

    @GetMapping("/{competitionId}/results/my")
    public Result<CompetitionResultResponse> myResult(HttpServletRequest request, @PathVariable Long competitionId) {
        Long studentUserId = authFacade.requireStudentUserId(request);
        return Result.ok(competitionService.getMyResult(studentUserId, competitionId));
    }
}
