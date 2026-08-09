package com.xrq.xxq.module.practice.competition.service.impl;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.xrq.xxq.common.BusinessException;
import com.xrq.xxq.common.PageQuery;
import com.xrq.xxq.common.PageResult;
import com.xrq.xxq.common.event.PracticeNoticeEvent;
import com.xrq.xxq.module.practice.common.entity.AuditStatusEnum;
import com.xrq.xxq.module.practice.competition.dto.CompetitionCreateRequest;
import com.xrq.xxq.module.practice.competition.dto.CompetitionResponse;
import com.xrq.xxq.module.practice.competition.dto.CompetitionResultRequest;
import com.xrq.xxq.module.practice.competition.dto.CompetitionResultResponse;
import com.xrq.xxq.module.practice.competition.dto.CompetitionUpdateRequest;
import com.xrq.xxq.module.practice.competition.dto.RegistrationRequest;
import com.xrq.xxq.module.practice.competition.dto.RegistrationResponse;
import com.xrq.xxq.module.practice.competition.dto.RegistrationReviewRequest;
import com.xrq.xxq.module.practice.competition.entity.Competition;
import com.xrq.xxq.module.practice.competition.entity.CompetitionRegistration;
import com.xrq.xxq.module.practice.competition.entity.CompetitionResult;
import com.xrq.xxq.module.practice.competition.entity.CompetitionStatusEnum;
import com.xrq.xxq.module.practice.competition.mapper.CompetitionMapper;
import com.xrq.xxq.module.practice.competition.mapper.CompetitionRegistrationMapper;
import com.xrq.xxq.module.practice.competition.mapper.CompetitionResultMapper;
import com.xrq.xxq.module.practice.competition.service.CompetitionService;
import com.xrq.xxq.module.semester.service.SemesterService;
import com.xrq.xxq.module.user.mapper.UserMapper;
import com.xrq.xxq.util.ParamValidator;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class CompetitionServiceImpl
        extends ServiceImpl<CompetitionMapper, Competition>
        implements CompetitionService {

    private final CompetitionRegistrationMapper registrationMapper;
    private final CompetitionResultMapper resultMapper;
    private final UserMapper userMapper;
    private final SemesterService semesterService;
    private final ApplicationEventPublisher eventPublisher;

    @Override
    @Transactional
    public CompetitionResponse createCompetition(CompetitionCreateRequest request) {
        ParamValidator.requireNonBlank(request.getName(), "竞赛名称");
        Competition competition = new Competition();
        competition.setSemesterId(semesterService.resolveOrDefault(request.getSemesterId()));
        competition.setName(request.getName());
        competition.setDescription(request.getDescription());
        competition.setOrganizer(request.getOrganizer());
        competition.setLevel(request.getLevel());
        competition.setRegStartTime(request.getRegStartTime());
        competition.setRegEndTime(request.getRegEndTime());
        competition.setContestTime(request.getContestTime());
        competition.setStatus(CompetitionStatusEnum.DRAFT);
        save(competition);
        return toResponse(competition);
    }

    @Override
    @Transactional
    public CompetitionResponse updateCompetition(Long id, CompetitionUpdateRequest request) {
        Competition competition = requireCompetition(id);
        if (request.getName() != null) {
            ParamValidator.requireNonBlank(request.getName(), "竞赛名称");
            competition.setName(request.getName());
        }
        if (request.getDescription() != null) {
            competition.setDescription(request.getDescription());
        }
        if (request.getOrganizer() != null) {
            competition.setOrganizer(request.getOrganizer());
        }
        if (request.getLevel() != null) {
            competition.setLevel(request.getLevel());
        }
        if (request.getRegStartTime() != null) {
            competition.setRegStartTime(request.getRegStartTime());
        }
        if (request.getRegEndTime() != null) {
            competition.setRegEndTime(request.getRegEndTime());
        }
        if (request.getContestTime() != null) {
            competition.setContestTime(request.getContestTime());
        }
        updateById(competition);
        return toResponse(competition);
    }

    @Override
    @Transactional
    public void changeCompetitionStatus(Long id, CompetitionStatusEnum status) {
        if (status == null) {
            throw new BusinessException(400, "状态不能为空");
        }
        Competition competition = requireCompetition(id);
        competition.setStatus(status);
        updateById(competition);
    }

    @Override
    public PageResult<CompetitionResponse> listCompetitions(CompetitionStatusEnum status, PageQuery pageQuery) {
        LambdaQueryWrapper<Competition> wrapper = new LambdaQueryWrapper<Competition>()
                .orderByDesc(Competition::getId);
        if (status != null) {
            wrapper.eq(Competition::getStatus, status);
        }
        Page<Competition> page = baseMapper.selectPage(pageQuery.toPage(), wrapper);
        List<CompetitionResponse> records = page.getRecords().stream().map(this::toResponse).toList();
        return PageResult.of(page, records);
    }

    @Override
    public CompetitionResponse getCompetition(Long id) {
        return toResponse(requireCompetition(id));
    }

    @Override
    public List<CompetitionResponse> listAvailableCompetitions(Long studentUserId) {
        List<Competition> list = baseMapper.selectList(new LambdaQueryWrapper<Competition>()
                .eq(Competition::getStatus, CompetitionStatusEnum.OPEN)
                .orderByDesc(Competition::getId));
        return list.stream().map(this::toResponse).toList();
    }

    @Override
    @Transactional
    public RegistrationResponse register(Long studentUserId, RegistrationRequest request) {
        ParamValidator.requireNonNull(request.getCompetitionId(), "竞赛");
        Competition competition = baseMapper.selectById(request.getCompetitionId());
        if (competition == null) {
            throw new BusinessException(404, "竞赛不存在");
        }
        if (competition.getStatus() != CompetitionStatusEnum.OPEN) {
            throw new BusinessException(409, "竞赛未开放报名");
        }
        LocalDateTime now = LocalDateTime.now();
        if (competition.getRegStartTime() != null && now.isBefore(competition.getRegStartTime())) {
            throw new BusinessException(409, "报名尚未开始");
        }
        if (competition.getRegEndTime() != null && now.isAfter(competition.getRegEndTime())) {
            throw new BusinessException(409, "报名已截止");
        }
        Long dup = registrationMapper.selectCount(new LambdaQueryWrapper<CompetitionRegistration>()
                .eq(CompetitionRegistration::getCompetitionId, request.getCompetitionId())
                .eq(CompetitionRegistration::getStudentId, studentUserId)
                .in(CompetitionRegistration::getStatus, AuditStatusEnum.PENDING, AuditStatusEnum.APPROVED));
        if (dup > 0) {
            throw new BusinessException(409, "已报名该竞赛");
        }
        CompetitionRegistration reg = new CompetitionRegistration();
        reg.setCompetitionId(request.getCompetitionId());
        reg.setStudentId(studentUserId);
        reg.setTeamName(request.getTeamName());
        reg.setMembers(request.getMembers());
        reg.setStatus(AuditStatusEnum.PENDING);
        reg.setRegisterTime(now);
        registrationMapper.insert(reg);
        return toRegResponse(reg, competition.getName(), nameOf(studentUserId));
    }

    @Override
    @Transactional
    public void cancelRegistration(Long studentUserId, Long registrationId) {
        CompetitionRegistration reg = registrationMapper.selectById(registrationId);
        if (reg == null) {
            throw new BusinessException(404, "报名不存在");
        }
        if (!reg.getStudentId().equals(studentUserId)) {
            throw new BusinessException(403, "权限不足");
        }
        if (reg.getStatus() != AuditStatusEnum.PENDING) {
            throw new BusinessException(409, "已处理的报名不可撤销");
        }
        registrationMapper.deleteById(registrationId);
    }

    @Override
    @Transactional
    public RegistrationResponse reviewRegistration(Long registrationId, RegistrationReviewRequest request) {
        if (request.getApproved() == null) {
            throw new BusinessException(400, "审核结果不能为空");
        }
        CompetitionRegistration reg = registrationMapper.selectById(registrationId);
        if (reg == null) {
            throw new BusinessException(404, "报名不存在");
        }
        if (reg.getStatus() != AuditStatusEnum.PENDING) {
            throw new BusinessException(409, "该报名已处理");
        }
        Competition competition = baseMapper.selectById(reg.getCompetitionId());
        if (competition == null) {
            throw new BusinessException(404, "竞赛不存在");
        }
        reg.setStatus(request.getApproved() ? AuditStatusEnum.APPROVED : AuditStatusEnum.REJECTED);
        reg.setReviewComment(request.getReviewComment());
        reg.setReviewTime(LocalDateTime.now());
        registrationMapper.updateById(reg);
        String title = "竞赛报名审核结果";
        String content = "您的竞赛《" + competition.getName() + "》报名"
                + (request.getApproved() ? "已通过" : "已被驳回") + "。";
        eventPublisher.publishEvent(new PracticeNoticeEvent(reg.getStudentId(), title, content));
        return toRegResponse(reg, competition.getName(), nameOf(reg.getStudentId()));
    }

    @Override
    public List<RegistrationResponse> listMyRegistrations(Long studentUserId) {
        List<CompetitionRegistration> regs = registrationMapper.selectList(
                new LambdaQueryWrapper<CompetitionRegistration>()
                        .eq(CompetitionRegistration::getStudentId, studentUserId)
                        .orderByDesc(CompetitionRegistration::getId));
        return toRegResponses(regs);
    }

    @Override
    public PageResult<RegistrationResponse> listRegistrationsByCompetition(Long competitionId, PageQuery pageQuery) {
        requireCompetition(competitionId);
        Page<CompetitionRegistration> page = registrationMapper.selectPage(pageQuery.toPage(),
                new LambdaQueryWrapper<CompetitionRegistration>()
                        .eq(CompetitionRegistration::getCompetitionId, competitionId)
                        .orderByDesc(CompetitionRegistration::getId));
        return PageResult.of(page, toRegResponses(page.getRecords()));
    }

    @Override
    @Transactional
    public void deleteCompetition(Long id) {
        requireCompetition(id);
        Long active = registrationMapper.selectCount(new LambdaQueryWrapper<CompetitionRegistration>()
                .eq(CompetitionRegistration::getCompetitionId, id)
                .in(CompetitionRegistration::getStatus, AuditStatusEnum.PENDING, AuditStatusEnum.APPROVED));
        if (active > 0) {
            throw new BusinessException(409, "存在未完结的报名，不可删除");
        }
        removeById(id);
    }

    @Override
    @Transactional
    public CompetitionResultResponse recordResult(CompetitionResultRequest request) {
        ParamValidator.requireNonNull(request.getCompetitionId(), "竞赛");
        ParamValidator.requireNonNull(request.getRegistrationId(), "报名记录");
        ParamValidator.requireNonNull(request.getAward(), "获奖等级");
        Competition competition = requireCompetition(request.getCompetitionId());
        CompetitionRegistration reg = registrationMapper.selectById(request.getRegistrationId());
        if (reg == null) {
            throw new BusinessException(404, "报名记录不存在");
        }
        if (!reg.getCompetitionId().equals(request.getCompetitionId())) {
            throw new BusinessException(400, "报名记录不属于该竞赛");
        }
        if (reg.getStatus() != AuditStatusEnum.APPROVED) {
            throw new BusinessException(409, "报名未通过审核，不可录入结果");
        }
        // upsert：按 registrationId 查现有结果，有则更新、无则新建
        CompetitionResult result = resultMapper.selectOne(new LambdaQueryWrapper<CompetitionResult>()
                .eq(CompetitionResult::getRegistrationId, request.getRegistrationId())
                .last("LIMIT 1"));
        boolean isNew = result == null;
        if (isNew) {
            result = new CompetitionResult();
            result.setCompetitionId(request.getCompetitionId());
            result.setRegistrationId(request.getRegistrationId());
            result.setStudentId(reg.getStudentId());
            result.setAwardTime(LocalDateTime.now());
        }
        result.setAward(request.getAward());
        result.setScore(request.getScore());
        result.setComment(request.getComment());
        if (isNew) {
            resultMapper.insert(result);
        } else {
            resultMapper.updateById(result);
        }
        String title = "竞赛结果通知";
        String content = "您在竞赛《" + competition.getName() + "》中获奖：" + request.getAward().getDescription() + "。";
        eventPublisher.publishEvent(new PracticeNoticeEvent(reg.getStudentId(), title, content));
        return toResultResponse(result, competition.getName(), nameOf(reg.getStudentId()));
    }

    @Override
    public List<CompetitionResultResponse> listResults(Long competitionId) {
        requireCompetition(competitionId);
        List<CompetitionResult> results = resultMapper.selectList(new LambdaQueryWrapper<CompetitionResult>()
                .eq(CompetitionResult::getCompetitionId, competitionId)
                .orderByDesc(CompetitionResult::getAwardTime));
        return toResultResponses(results);
    }

    @Override
    public CompetitionResultResponse getMyResult(Long studentUserId, Long competitionId) {
        CompetitionResult result = resultMapper.selectOne(new LambdaQueryWrapper<CompetitionResult>()
                .eq(CompetitionResult::getCompetitionId, competitionId)
                .eq(CompetitionResult::getStudentId, studentUserId)
                .last("LIMIT 1"));
        if (result == null) {
            return null;
        }
        Competition competition = baseMapper.selectById(competitionId);
        return toResultResponse(result, competition != null ? competition.getName() : null, nameOf(studentUserId));
    }

    @Override
    @Transactional
    public void deleteResult(Long resultId) {
        CompetitionResult result = resultMapper.selectById(resultId);
        if (result == null) {
            throw new BusinessException(404, "结果不存在");
        }
        resultMapper.deleteById(resultId);
    }

    // ---- helpers ----

    private Competition requireCompetition(Long id) {
        Competition competition = baseMapper.selectById(id);
        if (competition == null) {
            throw new BusinessException(404, "竞赛不存在");
        }
        return competition;
    }

    private List<RegistrationResponse> toRegResponses(List<CompetitionRegistration> regs) {
        if (regs.isEmpty()) {
            return List.of();
        }
        List<Long> compIds = regs.stream().map(CompetitionRegistration::getCompetitionId).distinct().toList();
        Map<Long, String> nameMap = baseMapper.selectByIds(compIds).stream()
                .collect(Collectors.toMap(Competition::getId, Competition::getName, (a, b) -> a));
        List<Long> personIds = new ArrayList<>();
        regs.forEach(r -> personIds.add(r.getStudentId()));
        personIds.removeIf(Objects::isNull);
        Map<Long, String> userMap = userMapper.toNameMap(personIds);
        return regs.stream()
                .map(r -> toRegResponse(r, nameMap.get(r.getCompetitionId()), userMap.get(r.getStudentId())))
                .toList();
    }

    private List<CompetitionResultResponse> toResultResponses(List<CompetitionResult> results) {
        if (results.isEmpty()) {
            return List.of();
        }
        List<Long> compIds = results.stream().map(CompetitionResult::getCompetitionId).distinct().toList();
        Map<Long, String> compNameMap = baseMapper.selectByIds(compIds).stream()
                .collect(Collectors.toMap(Competition::getId, Competition::getName, (a, b) -> a));
        List<Long> personIds = new ArrayList<>();
        results.forEach(r -> personIds.add(r.getStudentId()));
        personIds.removeIf(Objects::isNull);
        Map<Long, String> userMap = userMapper.toNameMap(personIds);
        return results.stream()
                .map(r -> toResultResponse(r, compNameMap.get(r.getCompetitionId()), userMap.get(r.getStudentId())))
                .toList();
    }

    private CompetitionResponse toResponse(Competition competition) {
        CompetitionResponse resp = new CompetitionResponse();
        resp.setId(competition.getId());
        resp.setSemesterId(competition.getSemesterId());
        resp.setName(competition.getName());
        resp.setDescription(competition.getDescription());
        resp.setOrganizer(competition.getOrganizer());
        resp.setLevel(competition.getLevel());
        resp.setRegStartTime(competition.getRegStartTime());
        resp.setRegEndTime(competition.getRegEndTime());
        resp.setContestTime(competition.getContestTime());
        resp.setStatus(competition.getStatus());
        resp.setCreateTime(competition.getCreateTime());
        return resp;
    }

    private RegistrationResponse toRegResponse(CompetitionRegistration reg, String competitionName, String studentName) {
        RegistrationResponse resp = new RegistrationResponse();
        resp.setId(reg.getId());
        resp.setCompetitionId(reg.getCompetitionId());
        resp.setCompetitionName(competitionName);
        resp.setStudentId(reg.getStudentId());
        resp.setStudentName(studentName);
        resp.setTeamName(reg.getTeamName());
        resp.setMembers(reg.getMembers());
        resp.setStatus(reg.getStatus());
        resp.setRegisterTime(reg.getRegisterTime());
        resp.setReviewTime(reg.getReviewTime());
        resp.setReviewComment(reg.getReviewComment());
        return resp;
    }

    private CompetitionResultResponse toResultResponse(CompetitionResult result, String competitionName, String studentName) {
        CompetitionResultResponse resp = new CompetitionResultResponse();
        resp.setId(result.getId());
        resp.setCompetitionId(result.getCompetitionId());
        resp.setCompetitionName(competitionName);
        resp.setRegistrationId(result.getRegistrationId());
        resp.setStudentId(result.getStudentId());
        resp.setStudentName(studentName);
        resp.setAward(result.getAward());
        resp.setScore(result.getScore());
        resp.setComment(result.getComment());
        resp.setAwardTime(result.getAwardTime());
        return resp;
    }

    private String nameOf(Long userId) {
        if (userId == null) {
            return null;
        }
        return userMapper.toNameMap(List.of(userId)).get(userId);
    }
}
