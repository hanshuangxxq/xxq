package com.xrq.xxq.module.practice.internship.service.impl;

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
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.xrq.xxq.common.BusinessException;
import com.xrq.xxq.common.PageQuery;
import com.xrq.xxq.common.PageResult;
import com.xrq.xxq.common.event.PracticeNoticeEvent;
import com.xrq.xxq.module.practice.common.entity.AuditStatusEnum;
import com.xrq.xxq.module.practice.internship.cache.InternshipPendingStore;
import com.xrq.xxq.module.practice.internship.dto.InternshipApplicationResponse;
import com.xrq.xxq.module.practice.internship.dto.InternshipApplyRequest;
import com.xrq.xxq.module.practice.internship.dto.InternshipCreateRequest;
import com.xrq.xxq.module.practice.internship.dto.InternshipResponse;
import com.xrq.xxq.module.practice.internship.dto.InternshipReviewRequest;
import com.xrq.xxq.module.practice.internship.dto.InternshipUpdateRequest;
import com.xrq.xxq.module.practice.internship.entity.Internship;
import com.xrq.xxq.module.practice.internship.entity.InternshipApplication;
import com.xrq.xxq.module.practice.internship.entity.InternshipStatusEnum;
import com.xrq.xxq.module.practice.internship.mapper.InternshipApplicationMapper;
import com.xrq.xxq.module.practice.internship.mapper.InternshipMapper;
import com.xrq.xxq.module.practice.internship.service.InternshipService;
import com.xrq.xxq.module.semester.service.SemesterService;
import com.xrq.xxq.module.user.mapper.UserMapper;
import com.xrq.xxq.util.ParamValidator;
import com.xrq.xxq.util.auth.AuthFacade;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class InternshipServiceImpl
        extends ServiceImpl<InternshipMapper, Internship>
        implements InternshipService {

    private final InternshipApplicationMapper applicationMapper;
    private final UserMapper userMapper;
    private final SemesterService semesterService;
    private final ApplicationEventPublisher eventPublisher;
    private final InternshipPendingStore pendingStore;

    @Override
    @Transactional
    public InternshipResponse createInternship(Long creatorUserId, String userType, InternshipCreateRequest request) {
        ParamValidator.requireNonBlank(request.getTitle(), "实习标题");
        ParamValidator.requirePositive(request.getCapacity(), "实习容量");
        Internship internship = new Internship();
        internship.setSemesterId(semesterService.resolveOrDefault(request.getSemesterId()));
        internship.setTitle(request.getTitle());
        internship.setCompany(request.getCompany());
        internship.setDescription(request.getDescription());
        internship.setSupervisorId(request.getSupervisorId() != null ? request.getSupervisorId() : creatorUserId);
        internship.setStartTime(request.getStartTime());
        internship.setEndTime(request.getEndTime());
        internship.setCapacity(request.getCapacity());
        internship.setSelectedCount(0);
        internship.setStatus(InternshipStatusEnum.DRAFT);
        save(internship);
        return toResponse(internship, nameOf(internship.getSupervisorId()));
    }

    @Override
    @Transactional
    public InternshipResponse updateInternship(Long id, InternshipUpdateRequest request, Long operatorUserId, String userType) {
        Internship internship = requireOwned(id, operatorUserId, userType);
        if (request.getTitle() != null) {
            ParamValidator.requireNonBlank(request.getTitle(), "实习标题");
            internship.setTitle(request.getTitle());
        }
        if (request.getCompany() != null) {
            internship.setCompany(request.getCompany());
        }
        if (request.getDescription() != null) {
            internship.setDescription(request.getDescription());
        }
        if (request.getSupervisorId() != null) {
            internship.setSupervisorId(request.getSupervisorId());
        }
        if (request.getStartTime() != null) {
            internship.setStartTime(request.getStartTime());
        }
        if (request.getEndTime() != null) {
            internship.setEndTime(request.getEndTime());
        }
        if (request.getCapacity() != null) {
            ParamValidator.requirePositive(request.getCapacity(), "实习容量");
            Integer selected = internship.getSelectedCount() == null ? 0 : internship.getSelectedCount();
            if (request.getCapacity() < selected) {
                throw new BusinessException(409, "容量不能小于已通过人数");
            }
            internship.setCapacity(request.getCapacity());
        }
        updateById(internship);
        return toResponse(internship, nameOf(internship.getSupervisorId()));
    }

    @Override
    @Transactional
    public void changeInternshipStatus(Long id, InternshipStatusEnum status, Long operatorUserId, String userType) {
        if (status == null) {
            throw new BusinessException(400, "状态不能为空");
        }
        Internship internship = requireOwned(id, operatorUserId, userType);
        internship.setStatus(status);
        updateById(internship);
    }

    @Override
    public PageResult<InternshipResponse> listInternships(Long operatorUserId, String userType,
                                                          Long supervisorId, InternshipStatusEnum status, PageQuery pageQuery) {
        LambdaQueryWrapper<Internship> wrapper = new LambdaQueryWrapper<Internship>()
                .orderByDesc(Internship::getId);
        if (!AuthFacade.USER_TYPE_ACADEMIC_ADMIN.equals(userType)) {
            wrapper.eq(Internship::getSupervisorId, operatorUserId);
        } else if (supervisorId != null) {
            wrapper.eq(Internship::getSupervisorId, supervisorId);
        }
        if (status != null) {
            wrapper.eq(Internship::getStatus, status);
        }
        Page<Internship> page = baseMapper.selectPage(pageQuery.toPage(), wrapper);
        List<Internship> list = page.getRecords();
        if (list.isEmpty()) {
            return PageResult.of(page, List.of());
        }
        Map<Long, String> nameMap = userMapper.toNameMap(
                list.stream().map(Internship::getSupervisorId).distinct().toList());
        List<InternshipResponse> records = list.stream()
                .map(i -> toResponse(i, nameMap.get(i.getSupervisorId()))).toList();
        return PageResult.of(page, records);
    }

    @Override
    public InternshipResponse getInternship(Long id) {
        Internship internship = baseMapper.selectById(id);
        if (internship == null) {
            throw new BusinessException(404, "实习项目不存在");
        }
        return toResponse(internship, nameOf(internship.getSupervisorId()));
    }

    @Override
    public List<InternshipResponse> listAvailableInternships(Long studentUserId) {
        List<Internship> list = baseMapper.selectList(new LambdaQueryWrapper<Internship>()
                .eq(Internship::getStatus, InternshipStatusEnum.OPEN)
                .orderByDesc(Internship::getId));
        Map<Long, String> nameMap = userMapper.toNameMap(
                list.stream().map(Internship::getSupervisorId).distinct().toList());
        return list.stream().map(i -> toResponse(i, nameMap.get(i.getSupervisorId()))).toList();
    }

    @Override
    @Transactional
    public InternshipApplicationResponse applyInternship(Long studentUserId, InternshipApplyRequest request) {
        ParamValidator.requireNonNull(request.getInternshipId(), "实习项目");
        Internship internship = baseMapper.selectById(request.getInternshipId());
        if (internship == null) {
            throw new BusinessException(404, "实习项目不存在");
        }
        if (internship.getStatus() != InternshipStatusEnum.OPEN) {
            throw new BusinessException(409, "实习项目未开放");
        }
        Long dup = applicationMapper.selectCount(new LambdaQueryWrapper<InternshipApplication>()
                .eq(InternshipApplication::getInternshipId, request.getInternshipId())
                .eq(InternshipApplication::getStudentId, studentUserId)
                .in(InternshipApplication::getStatus, AuditStatusEnum.PENDING, AuditStatusEnum.APPROVED));
        if (dup > 0) {
            throw new BusinessException(409, "已报名该实习");
        }
        // 待审核报名不占容量、不做数量限制：落库为 PENDING 后登记到 Redis 待审核集合，
        // 容量（selected_count）仅在审核通过时才消费。
        InternshipApplication app = new InternshipApplication();
        app.setInternshipId(request.getInternshipId());
        app.setStudentId(studentUserId);
        app.setStatus(AuditStatusEnum.PENDING);
        app.setApplyReason(request.getApplyReason());
        app.setApplyTime(LocalDateTime.now());
        applicationMapper.insert(app);
        pendingStore.markPending(app.getInternshipId(), studentUserId);
        return toAppResponse(app, internship.getTitle(), nameOf(studentUserId));
    }

    @Override
    @Transactional
    public void cancelApplication(Long studentUserId, Long applicationId) {
        InternshipApplication app = applicationMapper.selectById(applicationId);
        if (app == null) {
            throw new BusinessException(404, "报名不存在");
        }
        if (!app.getStudentId().equals(studentUserId)) {
            throw new BusinessException(403, "权限不足");
        }
        if (app.getStatus() != AuditStatusEnum.PENDING) {
            throw new BusinessException(409, "已处理的报名不可撤销");
        }
        applicationMapper.deleteById(applicationId);
        pendingStore.unmarkPending(app.getInternshipId(), studentUserId);
    }

    @Override
    @Transactional
    public InternshipApplicationResponse reviewApplication(Long applicationId, InternshipReviewRequest request,
                                                           Long operatorUserId, String userType) {
        if (request.getApproved() == null) {
            throw new BusinessException(400, "审核结果不能为空");
        }
        InternshipApplication app = applicationMapper.selectById(applicationId);
        if (app == null) {
            throw new BusinessException(404, "报名不存在");
        }
        if (app.getStatus() != AuditStatusEnum.PENDING) {
            throw new BusinessException(409, "该报名已处理");
        }
        Internship internship = baseMapper.selectById(app.getInternshipId());
        if (internship == null) {
            throw new BusinessException(404, "实习项目不存在");
        }
        ensureOwns(internship, operatorUserId, userType);
        if (request.getApproved()) {
            // 审核通过才纳入容量管理：条件更新防超售；满员时报名保持 PENDING，可扩容后再审
            Integer affected = baseMapper.update(null, new LambdaUpdateWrapper<Internship>()
                    .eq(Internship::getId, internship.getId())
                    .lt(Internship::getSelectedCount, internship.getCapacity())
                    .setSql("selected_count = selected_count + 1"));
            if (affected == 0) {
                throw new BusinessException(409, "实习容量已满，暂时无法通过该报名");
            }
        }
        app.setStatus(request.getApproved() ? AuditStatusEnum.APPROVED : AuditStatusEnum.REJECTED);
        app.setReviewComment(request.getReviewComment());
        app.setReviewTime(LocalDateTime.now());
        applicationMapper.updateById(app);
        pendingStore.unmarkPending(app.getInternshipId(), app.getStudentId());
        String title = "实习报名审核结果";
        String content = "您的实习《" + internship.getTitle() + "》报名"
                + (request.getApproved() ? "已通过" : "已被驳回") + "。";
        eventPublisher.publishEvent(new PracticeNoticeEvent(app.getStudentId(), title, content));
        return toAppResponse(app, internship.getTitle(), nameOf(app.getStudentId()));
    }

    @Override
    public List<InternshipApplicationResponse> listMyApplications(Long studentUserId) {
        List<InternshipApplication> apps = applicationMapper.selectList(
                new LambdaQueryWrapper<InternshipApplication>()
                        .eq(InternshipApplication::getStudentId, studentUserId)
                        .orderByDesc(InternshipApplication::getId));
        return toAppResponses(apps);
    }

    @Override
    public PageResult<InternshipApplicationResponse> listApplicationsByInternship(Long internshipId, Long operatorUserId,
                                                                                  String userType, PageQuery pageQuery) {
        Internship internship = requireOwned(internshipId, operatorUserId, userType);
        Page<InternshipApplication> page = applicationMapper.selectPage(pageQuery.toPage(),
                new LambdaQueryWrapper<InternshipApplication>()
                        .eq(InternshipApplication::getInternshipId, internshipId)
                        .orderByDesc(InternshipApplication::getId));
        return PageResult.of(page, toAppResponses(page.getRecords()));
    }

    @Override
    @Transactional
    public void deleteInternship(Long id, Long operatorUserId, String userType) {
        requireOwned(id, operatorUserId, userType);
        Long active = applicationMapper.selectCount(new LambdaQueryWrapper<InternshipApplication>()
                .eq(InternshipApplication::getInternshipId, id)
                .in(InternshipApplication::getStatus, AuditStatusEnum.PENDING, AuditStatusEnum.APPROVED));
        if (active > 0) {
            throw new BusinessException(409, "存在未完结的报名，不可删除");
        }
        removeById(id);
        pendingStore.clear(id);
    }

    // ---- helpers ----

    private Internship requireOwned(Long id, Long operatorUserId, String userType) {
        Internship internship = baseMapper.selectById(id);
        if (internship == null) {
            throw new BusinessException(404, "实习项目不存在");
        }
        ensureOwns(internship, operatorUserId, userType);
        return internship;
    }

    private void ensureOwns(Internship internship, Long operatorUserId, String userType) {
        if (!AuthFacade.USER_TYPE_ACADEMIC_ADMIN.equals(userType)
                && !internship.getSupervisorId().equals(operatorUserId)) {
            throw new BusinessException(403, "权限不足");
        }
    }

    private List<InternshipApplicationResponse> toAppResponses(List<InternshipApplication> apps) {
        if (apps.isEmpty()) {
            return List.of();
        }
        List<Long> ids = apps.stream().map(InternshipApplication::getInternshipId).distinct().toList();
        Map<Long, String> titleMap = baseMapper.selectByIds(ids).stream()
                .collect(Collectors.toMap(Internship::getId, Internship::getTitle, (a, b) -> a));
        List<Long> personIds = new ArrayList<>();
        apps.forEach(a -> personIds.add(a.getStudentId()));
        personIds.removeIf(Objects::isNull);
        Map<Long, String> nameMap = userMapper.toNameMap(personIds);
        return apps.stream()
                .map(a -> toAppResponse(a, titleMap.get(a.getInternshipId()), nameMap.get(a.getStudentId())))
                .toList();
    }

    private InternshipResponse toResponse(Internship internship, String supervisorName) {
        InternshipResponse resp = new InternshipResponse();
        resp.setId(internship.getId());
        resp.setSemesterId(internship.getSemesterId());
        resp.setTitle(internship.getTitle());
        resp.setCompany(internship.getCompany());
        resp.setDescription(internship.getDescription());
        resp.setSupervisorId(internship.getSupervisorId());
        resp.setSupervisorName(supervisorName);
        resp.setStartTime(internship.getStartTime());
        resp.setEndTime(internship.getEndTime());
        resp.setCapacity(internship.getCapacity());
        resp.setSelectedCount(internship.getSelectedCount());
        resp.setPendingCount(pendingStore.pendingCount(internship.getId()));
        resp.setStatus(internship.getStatus());
        resp.setCreateTime(internship.getCreateTime());
        return resp;
    }

    private InternshipApplicationResponse toAppResponse(InternshipApplication app, String internshipTitle, String studentName) {
        InternshipApplicationResponse resp = new InternshipApplicationResponse();
        resp.setId(app.getId());
        resp.setInternshipId(app.getInternshipId());
        resp.setInternshipTitle(internshipTitle);
        resp.setStudentId(app.getStudentId());
        resp.setStudentName(studentName);
        resp.setStatus(app.getStatus());
        resp.setApplyReason(app.getApplyReason());
        resp.setApplyTime(app.getApplyTime());
        resp.setReviewTime(app.getReviewTime());
        resp.setReviewComment(app.getReviewComment());
        return resp;
    }

    private String nameOf(Long userId) {
        if (userId == null) {
            return null;
        }
        return userMapper.toNameMap(List.of(userId)).get(userId);
    }
}
