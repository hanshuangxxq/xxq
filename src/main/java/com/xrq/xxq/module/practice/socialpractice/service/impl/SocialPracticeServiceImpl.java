package com.xrq.xxq.module.practice.socialpractice.service.impl;

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
import com.xrq.xxq.module.practice.socialpractice.dto.SocialPracticeApplicationResponse;
import com.xrq.xxq.module.practice.socialpractice.dto.SocialPracticeApplyRequest;
import com.xrq.xxq.module.practice.socialpractice.dto.SocialPracticeCreateRequest;
import com.xrq.xxq.module.practice.socialpractice.dto.SocialPracticeResponse;
import com.xrq.xxq.module.practice.socialpractice.dto.SocialPracticeReviewRequest;
import com.xrq.xxq.module.practice.socialpractice.dto.SocialPracticeUpdateRequest;
import com.xrq.xxq.module.practice.socialpractice.entity.SocialPractice;
import com.xrq.xxq.module.practice.socialpractice.entity.SocialPracticeApplication;
import com.xrq.xxq.module.practice.socialpractice.entity.SocialPracticeStatusEnum;
import com.xrq.xxq.module.practice.socialpractice.mapper.SocialPracticeApplicationMapper;
import com.xrq.xxq.module.practice.socialpractice.mapper.SocialPracticeMapper;
import com.xrq.xxq.module.practice.socialpractice.service.SocialPracticeService;
import com.xrq.xxq.module.semester.service.SemesterService;
import com.xrq.xxq.module.user.mapper.UserMapper;
import com.xrq.xxq.util.ParamValidator;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class SocialPracticeServiceImpl
        extends ServiceImpl<SocialPracticeMapper, SocialPractice>
        implements SocialPracticeService {

    private final SocialPracticeApplicationMapper applicationMapper;
    private final UserMapper userMapper;
    private final SemesterService semesterService;
    private final ApplicationEventPublisher eventPublisher;

    @Override
    @Transactional
    public SocialPracticeResponse createPractice(SocialPracticeCreateRequest request) {
        ParamValidator.requireNonBlank(request.getTitle(), "实践标题");
        ParamValidator.requirePositive(request.getCapacity(), "实践容量");
        SocialPractice practice = new SocialPractice();
        practice.setSemesterId(semesterService.resolveOrDefault(request.getSemesterId()));
        practice.setTitle(request.getTitle());
        practice.setDescription(request.getDescription());
        practice.setOrganizer(request.getOrganizer());
        practice.setStartTime(request.getStartTime());
        practice.setEndTime(request.getEndTime());
        practice.setCapacity(request.getCapacity());
        practice.setSelectedCount(0);
        practice.setStatus(SocialPracticeStatusEnum.DRAFT);
        save(practice);
        return toResponse(practice);
    }

    @Override
    @Transactional
    public SocialPracticeResponse updatePractice(Long id, SocialPracticeUpdateRequest request) {
        SocialPractice practice = requirePractice(id);
        if (request.getTitle() != null) {
            ParamValidator.requireNonBlank(request.getTitle(), "实践标题");
            practice.setTitle(request.getTitle());
        }
        if (request.getDescription() != null) {
            practice.setDescription(request.getDescription());
        }
        if (request.getOrganizer() != null) {
            practice.setOrganizer(request.getOrganizer());
        }
        if (request.getStartTime() != null) {
            practice.setStartTime(request.getStartTime());
        }
        if (request.getEndTime() != null) {
            practice.setEndTime(request.getEndTime());
        }
        if (request.getCapacity() != null) {
            ParamValidator.requirePositive(request.getCapacity(), "实践容量");
            int selected = practice.getSelectedCount() == null ? 0 : practice.getSelectedCount();
            if (request.getCapacity() < selected) {
                throw new BusinessException(409, "容量不能小于已申报人数");
            }
            practice.setCapacity(request.getCapacity());
        }
        updateById(practice);
        return toResponse(practice);
    }

    @Override
    @Transactional
    public void changePracticeStatus(Long id, SocialPracticeStatusEnum status) {
        if (status == null) {
            throw new BusinessException(400, "状态不能为空");
        }
        SocialPractice practice = requirePractice(id);
        practice.setStatus(status);
        updateById(practice);
    }

    @Override
    public PageResult<SocialPracticeResponse> listPractices(SocialPracticeStatusEnum status, PageQuery pageQuery) {
        LambdaQueryWrapper<SocialPractice> wrapper = new LambdaQueryWrapper<SocialPractice>()
                .orderByDesc(SocialPractice::getId);
        if (status != null) {
            wrapper.eq(SocialPractice::getStatus, status);
        }
        Page<SocialPractice> page = baseMapper.selectPage(pageQuery.toPage(), wrapper);
        List<SocialPracticeResponse> records = page.getRecords().stream().map(this::toResponse).toList();
        return PageResult.of(page, records);
    }

    @Override
    public SocialPracticeResponse getPractice(Long id) {
        return toResponse(requirePractice(id));
    }

    @Override
    public List<SocialPracticeResponse> listAvailablePractices(Long studentUserId) {
        List<SocialPractice> list = baseMapper.selectList(new LambdaQueryWrapper<SocialPractice>()
                .eq(SocialPractice::getStatus, SocialPracticeStatusEnum.OPEN)
                .orderByDesc(SocialPractice::getId));
        return list.stream().map(this::toResponse).toList();
    }

    @Override
    @Transactional
    public SocialPracticeApplicationResponse apply(Long studentUserId, SocialPracticeApplyRequest request) {
        ParamValidator.requireNonNull(request.getPracticeId(), "实践项目");
        SocialPractice practice = baseMapper.selectById(request.getPracticeId());
        if (practice == null) {
            throw new BusinessException(404, "实践项目不存在");
        }
        if (practice.getStatus() != SocialPracticeStatusEnum.OPEN) {
            throw new BusinessException(409, "实践项目未开放");
        }
        Long dup = applicationMapper.selectCount(new LambdaQueryWrapper<SocialPracticeApplication>()
                .eq(SocialPracticeApplication::getPracticeId, request.getPracticeId())
                .eq(SocialPracticeApplication::getStudentId, studentUserId)
                .in(SocialPracticeApplication::getStatus, AuditStatusEnum.PENDING, AuditStatusEnum.APPROVED));
        if (dup > 0) {
            throw new BusinessException(409, "已申报该实践");
        }
        int affected = baseMapper.update(null, new LambdaUpdateWrapper<SocialPractice>()
                .eq(SocialPractice::getId, request.getPracticeId())
                .lt(SocialPractice::getSelectedCount, practice.getCapacity())
                .setSql("selected_count = selected_count + 1"));
        if (affected == 0) {
            throw new BusinessException(409, "实践已满");
        }
        SocialPracticeApplication app = new SocialPracticeApplication();
        app.setPracticeId(request.getPracticeId());
        app.setStudentId(studentUserId);
        app.setTeamName(request.getTeamName());
        app.setMembers(request.getMembers());
        app.setApplyReason(request.getApplyReason());
        app.setStatus(AuditStatusEnum.PENDING);
        app.setApplyTime(LocalDateTime.now());
        applicationMapper.insert(app);
        return toAppResponse(app, practice.getTitle(), nameOf(studentUserId));
    }

    @Override
    @Transactional
    public void cancelApplication(Long studentUserId, Long applicationId) {
        SocialPracticeApplication app = applicationMapper.selectById(applicationId);
        if (app == null) {
            throw new BusinessException(404, "申报不存在");
        }
        if (!app.getStudentId().equals(studentUserId)) {
            throw new BusinessException(403, "权限不足");
        }
        if (app.getStatus() != AuditStatusEnum.PENDING) {
            throw new BusinessException(409, "已处理的申报不可撤销");
        }
        applicationMapper.deleteById(applicationId);
        baseMapper.update(null, new LambdaUpdateWrapper<SocialPractice>()
                .eq(SocialPractice::getId, app.getPracticeId())
                .setSql("selected_count = GREATEST(selected_count - 1, 0)"));
    }

    @Override
    @Transactional
    public SocialPracticeApplicationResponse reviewApplication(Long applicationId, SocialPracticeReviewRequest request) {
        if (request.getApproved() == null) {
            throw new BusinessException(400, "审核结果不能为空");
        }
        SocialPracticeApplication app = applicationMapper.selectById(applicationId);
        if (app == null) {
            throw new BusinessException(404, "申报不存在");
        }
        if (app.getStatus() != AuditStatusEnum.PENDING) {
            throw new BusinessException(409, "该申报已处理");
        }
        SocialPractice practice = baseMapper.selectById(app.getPracticeId());
        if (practice == null) {
            throw new BusinessException(404, "实践项目不存在");
        }
        app.setStatus(request.getApproved() ? AuditStatusEnum.APPROVED : AuditStatusEnum.REJECTED);
        app.setReviewComment(request.getReviewComment());
        app.setReviewTime(LocalDateTime.now());
        applicationMapper.updateById(app);
        if (!request.getApproved()) {
            baseMapper.update(null, new LambdaUpdateWrapper<SocialPractice>()
                    .eq(SocialPractice::getId, practice.getId())
                    .setSql("selected_count = GREATEST(selected_count - 1, 0)"));
        }
        String title = "社会实践申报审核结果";
        String content = "您的实践《" + practice.getTitle() + "》申报"
                + (request.getApproved() ? "已通过" : "已被驳回") + "。";
        eventPublisher.publishEvent(new PracticeNoticeEvent(app.getStudentId(), title, content));
        return toAppResponse(app, practice.getTitle(), nameOf(app.getStudentId()));
    }

    @Override
    public List<SocialPracticeApplicationResponse> listMyApplications(Long studentUserId) {
        List<SocialPracticeApplication> apps = applicationMapper.selectList(
                new LambdaQueryWrapper<SocialPracticeApplication>()
                        .eq(SocialPracticeApplication::getStudentId, studentUserId)
                        .orderByDesc(SocialPracticeApplication::getId));
        return toAppResponses(apps);
    }

    @Override
    public PageResult<SocialPracticeApplicationResponse> listApplicationsByPractice(Long practiceId, PageQuery pageQuery) {
        requirePractice(practiceId);
        Page<SocialPracticeApplication> page = applicationMapper.selectPage(pageQuery.toPage(),
                new LambdaQueryWrapper<SocialPracticeApplication>()
                        .eq(SocialPracticeApplication::getPracticeId, practiceId)
                        .orderByDesc(SocialPracticeApplication::getId));
        return PageResult.of(page, toAppResponses(page.getRecords()));
    }

    @Override
    @Transactional
    public void deletePractice(Long id) {
        requirePractice(id);
        Long active = applicationMapper.selectCount(new LambdaQueryWrapper<SocialPracticeApplication>()
                .eq(SocialPracticeApplication::getPracticeId, id)
                .in(SocialPracticeApplication::getStatus, AuditStatusEnum.PENDING, AuditStatusEnum.APPROVED));
        if (active > 0) {
            throw new BusinessException(409, "存在未完结的申报，不可删除");
        }
        removeById(id);
    }

    // ---- helpers ----

    private SocialPractice requirePractice(Long id) {
        SocialPractice practice = baseMapper.selectById(id);
        if (practice == null) {
            throw new BusinessException(404, "实践项目不存在");
        }
        return practice;
    }

    private List<SocialPracticeApplicationResponse> toAppResponses(List<SocialPracticeApplication> apps) {
        if (apps.isEmpty()) {
            return List.of();
        }
        List<Long> ids = apps.stream().map(SocialPracticeApplication::getPracticeId).distinct().toList();
        Map<Long, String> titleMap = baseMapper.selectByIds(ids).stream()
                .collect(Collectors.toMap(SocialPractice::getId, SocialPractice::getTitle, (a, b) -> a));
        List<Long> personIds = new ArrayList<>();
        apps.forEach(a -> personIds.add(a.getStudentId()));
        personIds.removeIf(Objects::isNull);
        Map<Long, String> nameMap = userMapper.toNameMap(personIds);
        return apps.stream()
                .map(a -> toAppResponse(a, titleMap.get(a.getPracticeId()), nameMap.get(a.getStudentId())))
                .toList();
    }

    private SocialPracticeResponse toResponse(SocialPractice practice) {
        SocialPracticeResponse resp = new SocialPracticeResponse();
        resp.setId(practice.getId());
        resp.setSemesterId(practice.getSemesterId());
        resp.setTitle(practice.getTitle());
        resp.setDescription(practice.getDescription());
        resp.setOrganizer(practice.getOrganizer());
        resp.setStartTime(practice.getStartTime());
        resp.setEndTime(practice.getEndTime());
        resp.setCapacity(practice.getCapacity());
        resp.setSelectedCount(practice.getSelectedCount());
        resp.setStatus(practice.getStatus());
        resp.setCreateTime(practice.getCreateTime());
        return resp;
    }

    private SocialPracticeApplicationResponse toAppResponse(SocialPracticeApplication app, String practiceTitle, String studentName) {
        SocialPracticeApplicationResponse resp = new SocialPracticeApplicationResponse();
        resp.setId(app.getId());
        resp.setPracticeId(app.getPracticeId());
        resp.setPracticeTitle(practiceTitle);
        resp.setStudentId(app.getStudentId());
        resp.setStudentName(studentName);
        resp.setTeamName(app.getTeamName());
        resp.setMembers(app.getMembers());
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
