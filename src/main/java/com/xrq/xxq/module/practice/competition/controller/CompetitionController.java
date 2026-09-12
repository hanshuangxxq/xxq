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
import com.xrq.xxq.util.auth.RequireAuth;
import com.xrq.xxq.util.auth.UserType;

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
    @RequireAuth(UserType.ACADEMIC_ADMIN)
    public Result<CompetitionResponse> create(HttpServletRequest request, @RequestBody CompetitionCreateRequest body) {
        return Result.ok(competitionService.createCompetition(body));
    }

    @PutMapping("/{id}")
    @RequireAuth(UserType.ACADEMIC_ADMIN)
    public Result<CompetitionResponse> update(HttpServletRequest request, @PathVariable Long id,
                                              @RequestBody CompetitionUpdateRequest body) {
        return Result.ok(competitionService.updateCompetition(id, body));
    }

    @PutMapping("/{id}/status")
    @RequireAuth(UserType.ACADEMIC_ADMIN)
    public Result<Void> changeStatus(HttpServletRequest request, @PathVariable Long id,
                                     @RequestParam CompetitionStatusEnum status) {
        competitionService.changeCompetitionStatus(id, status);
        return Result.ok();
    }

    @GetMapping
    @RequireAuth(UserType.ACADEMIC_ADMIN)
    public Result<PageResult<CompetitionResponse>> list(HttpServletRequest request,
                                                        @RequestParam(required = false) CompetitionStatusEnum status,
                                                        @RequestParam(required = false) Integer page,
                                                        @RequestParam(required = false) Integer pageSize) {
        return Result.ok(competitionService.listCompetitions(status, new PageQuery(page, pageSize)));
    }

    @GetMapping("/{id}")
    @RequireAuth()
    public Result<CompetitionResponse> get(@PathVariable Long id) {
        return Result.ok(competitionService.getCompetition(id));
    }

    @GetMapping("/available")
    @RequireAuth(UserType.STUDENT)
    public Result<List<CompetitionResponse>> listAvailable(HttpServletRequest request) {
        Long studentUserId = authFacade.currentUserId(request);
        return Result.ok(competitionService.listAvailableCompetitions(studentUserId));
    }

    @DeleteMapping("/{id}")
    @RequireAuth(UserType.ACADEMIC_ADMIN)
    public Result<Void> delete(HttpServletRequest request, @PathVariable Long id) {
        competitionService.deleteCompetition(id);
        return Result.ok();
    }

    @PostMapping("/registrations")
    @RequireAuth(UserType.STUDENT)
    public Result<RegistrationResponse> register(HttpServletRequest request, @RequestBody RegistrationRequest body) {
        Long studentUserId = authFacade.currentUserId(request);
        return Result.ok(competitionService.register(studentUserId, body));
    }

    @DeleteMapping("/registrations/{id}")
    @RequireAuth(UserType.STUDENT)
    public Result<Void> cancel(HttpServletRequest request, @PathVariable Long id) {
        Long studentUserId = authFacade.currentUserId(request);
        competitionService.cancelRegistration(studentUserId, id);
        return Result.ok();
    }

    @PostMapping("/registrations/{id}/review")
    @RequireAuth(UserType.ACADEMIC_ADMIN)
    public Result<RegistrationResponse> review(HttpServletRequest request, @PathVariable Long id,
                                               @RequestBody RegistrationReviewRequest body) {
        return Result.ok(competitionService.reviewRegistration(id, body));
    }

    @GetMapping("/registrations/my")
    @RequireAuth(UserType.STUDENT)
    public Result<List<RegistrationResponse>> myRegistrations(HttpServletRequest request) {
        Long studentUserId = authFacade.currentUserId(request);
        return Result.ok(competitionService.listMyRegistrations(studentUserId));
    }

    @GetMapping("/{competitionId}/registrations")
    @RequireAuth(UserType.ACADEMIC_ADMIN)
    public Result<PageResult<RegistrationResponse>> registrationsByCompetition(HttpServletRequest request,
                                                                              @PathVariable Long competitionId,
                                                                              @RequestParam(required = false) Integer page,
                                                                              @RequestParam(required = false) Integer pageSize) {
        return Result.ok(competitionService.listRegistrationsByCompetition(competitionId, new PageQuery(page, pageSize)));
    }

    @PostMapping("/results")
    @RequireAuth(UserType.ACADEMIC_ADMIN)
    public Result<CompetitionResultResponse> recordResult(HttpServletRequest request,
                                                          @RequestBody CompetitionResultRequest body) {
        return Result.ok(competitionService.recordResult(body));
    }

    @DeleteMapping("/results/{id}")
    @RequireAuth(UserType.ACADEMIC_ADMIN)
    public Result<Void> deleteResult(HttpServletRequest request, @PathVariable Long id) {
        competitionService.deleteResult(id);
        return Result.ok();
    }

    @GetMapping("/{competitionId}/results")
    @RequireAuth()
    public Result<List<CompetitionResultResponse>> results(@PathVariable Long competitionId) {
        return Result.ok(competitionService.listResults(competitionId));
    }

    @GetMapping("/{competitionId}/results/my")
    @RequireAuth(UserType.STUDENT)
    public Result<CompetitionResultResponse> myResult(HttpServletRequest request, @PathVariable Long competitionId) {
        Long studentUserId = authFacade.currentUserId(request);
        return Result.ok(competitionService.getMyResult(studentUserId, competitionId));
    }
}
