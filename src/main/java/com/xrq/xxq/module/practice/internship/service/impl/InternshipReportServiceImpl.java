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
import org.springframework.web.multipart.MultipartFile;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.xrq.xxq.common.BusinessException;
import com.xrq.xxq.common.PageQuery;
import com.xrq.xxq.common.PageResult;
import com.xrq.xxq.common.event.PracticeNoticeEvent;
import com.xrq.xxq.module.practice.common.PracticeFileService;
import com.xrq.xxq.module.practice.common.entity.AuditStatusEnum;
import com.xrq.xxq.module.practice.common.entity.ReportStatusEnum;
import com.xrq.xxq.module.practice.internship.dto.InternshipReportResponse;
import com.xrq.xxq.module.practice.internship.dto.InternshipReportReviewRequest;
import com.xrq.xxq.module.practice.internship.dto.InternshipReportSubmitRequest;
import com.xrq.xxq.module.practice.internship.entity.Internship;
import com.xrq.xxq.module.practice.internship.entity.InternshipApplication;
import com.xrq.xxq.module.practice.internship.entity.InternshipReport;
import com.xrq.xxq.module.practice.internship.mapper.InternshipApplicationMapper;
import com.xrq.xxq.module.practice.internship.mapper.InternshipMapper;
import com.xrq.xxq.module.practice.internship.mapper.InternshipReportMapper;
import com.xrq.xxq.module.practice.internship.service.InternshipReportService;
import com.xrq.xxq.module.user.mapper.UserMapper;
import com.xrq.xxq.util.ParamValidator;
import com.xrq.xxq.util.auth.AuthFacade;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class InternshipReportServiceImpl
        extends ServiceImpl<InternshipReportMapper, InternshipReport>
        implements InternshipReportService {

    private final InternshipMapper internshipMapper;
    private final InternshipApplicationMapper applicationMapper;
    private final UserMapper userMapper;
    private final PracticeFileService fileService;
    private final ApplicationEventPublisher eventPublisher;

    @Override
    @Transactional
    public InternshipReportResponse submit(Long studentUserId, InternshipReportSubmitRequest request, MultipartFile file) {
        ParamValidator.requireNonNull(request.getInternshipId(), "实习项目");
        ParamValidator.requireNonBlank(request.getTitle(), "报告标题");
        Internship internship = internshipMapper.selectById(request.getInternshipId());
        if (internship == null) {
            throw new BusinessException(404, "实习项目不存在");
        }
        InternshipApplication app = applicationMapper.selectOne(new LambdaQueryWrapper<InternshipApplication>()
                .eq(InternshipApplication::getInternshipId, request.getInternshipId())
                .eq(InternshipApplication::getStudentId, studentUserId)
                .eq(InternshipApplication::getStatus, AuditStatusEnum.APPROVED)
                .last("LIMIT 1"));
        if (app == null) {
            throw new BusinessException(409, "实习未通过审核，不可提交报告");
        }
        // 一人一份；未评审可重传，已评审不可改
        InternshipReport existing = baseMapper.selectOne(new LambdaQueryWrapper<InternshipReport>()
                .eq(InternshipReport::getInternshipId, request.getInternshipId())
                .eq(InternshipReport::getStudentId, studentUserId)
                .last("LIMIT 1"));
        if (existing != null) {
            if (existing.getStatus() != ReportStatusEnum.SUBMITTED) {
                throw new BusinessException(409, "报告已评审，不可修改");
            }
            PracticeFileService.StoredFile stored = fileService.store(file);
            existing.setTitle(request.getTitle());
            existing.setSummary(request.getSummary());
            existing.setFileName(stored.storedName());
            existing.setFileOriginal(stored.originalName());
            existing.setSubmitTime(LocalDateTime.now());
            updateById(existing);
            return toResponse(existing, internship.getTitle(), nameOf(studentUserId));
        }
        PracticeFileService.StoredFile stored = fileService.store(file);
        InternshipReport report = new InternshipReport();
        report.setInternshipId(request.getInternshipId());
        report.setStudentId(studentUserId);
        report.setTitle(request.getTitle());
        report.setSummary(request.getSummary());
        report.setFileName(stored.storedName());
        report.setFileOriginal(stored.originalName());
        report.setSubmitTime(LocalDateTime.now());
        report.setStatus(ReportStatusEnum.SUBMITTED);
        save(report);
        return toResponse(report, internship.getTitle(), nameOf(studentUserId));
    }

    @Override
    @Transactional
    public InternshipReportResponse review(Long reportId, InternshipReportReviewRequest request,
                                           Long reviewerUserId, String userType) {
        InternshipReport report = baseMapper.selectById(reportId);
        if (report == null) {
            throw new BusinessException(404, "报告不存在");
        }
        Internship internship = internshipMapper.selectById(report.getInternshipId());
        if (internship == null) {
            throw new BusinessException(404, "实习项目不存在");
        }
        if (!AuthFacade.USER_TYPE_ACADEMIC_ADMIN.equals(userType)
                && !internship.getSupervisorId().equals(reviewerUserId)) {
            throw new BusinessException(403, "权限不足");
        }
        if (report.getStatus() != ReportStatusEnum.SUBMITTED) {
            throw new BusinessException(409, "该报告已评审");
        }
        report.setScore(request.getScore());
        report.setFeedback(request.getFeedback());
        report.setReviewTime(LocalDateTime.now());
        report.setStatus(ReportStatusEnum.REVIEWED);
        updateById(report);
        String title = "实习报告评审结果";
        String content = "您的实习《" + internship.getTitle() + "》报告已评审完成。";
        eventPublisher.publishEvent(new PracticeNoticeEvent(report.getStudentId(), title, content));
        return toResponse(report, internship.getTitle(), nameOf(report.getStudentId()));
    }

    @Override
    public List<InternshipReportResponse> listMyReports(Long studentUserId) {
        List<InternshipReport> reports = baseMapper.selectList(new LambdaQueryWrapper<InternshipReport>()
                .eq(InternshipReport::getStudentId, studentUserId)
                .orderByDesc(InternshipReport::getId));
        return toResponses(reports);
    }

    @Override
    public PageResult<InternshipReportResponse> listForHandler(Long handlerUserId, String userType,
                                                               ReportStatusEnum status, PageQuery pageQuery) {
        LambdaQueryWrapper<InternshipReport> wrapper = new LambdaQueryWrapper<InternshipReport>()
                .orderByDesc(InternshipReport::getId);
        if (!AuthFacade.USER_TYPE_ACADEMIC_ADMIN.equals(userType)) {
            List<Long> ids = internshipMapper.selectList(new LambdaQueryWrapper<Internship>()
                    .eq(Internship::getSupervisorId, handlerUserId))
                    .stream().map(Internship::getId).toList();
            if (ids.isEmpty()) {
                return PageResult.slice(List.of(), pageQuery);
            }
            wrapper.in(InternshipReport::getInternshipId, ids);
        }
        if (status != null) {
            wrapper.eq(InternshipReport::getStatus, status);
        }
        Page<InternshipReport> page = baseMapper.selectPage(pageQuery.toPage(), wrapper);
        return PageResult.of(page, toResponses(page.getRecords()));
    }

    @Override
    public InternshipReport loadForDownload(Long reportId, Long operatorUserId, String userType) {
        InternshipReport report = baseMapper.selectById(reportId);
        if (report == null) {
            throw new BusinessException(404, "报告不存在");
        }
        if (!AuthFacade.USER_TYPE_ACADEMIC_ADMIN.equals(userType)) {
            if (report.getStudentId().equals(operatorUserId)) {
                return report;
            }
            Internship internship = internshipMapper.selectById(report.getInternshipId());
            if (internship != null && internship.getSupervisorId().equals(operatorUserId)) {
                return report;
            }
            throw new BusinessException(403, "权限不足");
        }
        return report;
    }

    @Override
    @Transactional
    public void deleteReport(Long reportId, Long operatorUserId, String userType) {
        InternshipReport report = baseMapper.selectById(reportId);
        if (report == null) {
            throw new BusinessException(404, "报告不存在");
        }
        if (!AuthFacade.USER_TYPE_ACADEMIC_ADMIN.equals(userType)) {
            Boolean allowed = false;
            if (report.getStudentId() != null && report.getStudentId().equals(operatorUserId)
                    && report.getStatus() == ReportStatusEnum.SUBMITTED) {
                allowed = true;
            } else {
                Internship internship = internshipMapper.selectById(report.getInternshipId());
                allowed = internship != null && internship.getSupervisorId() != null
                        && internship.getSupervisorId().equals(operatorUserId);
            }
            if (!allowed) {
                throw new BusinessException(403, "权限不足");
            }
        }
        String fileName = report.getFileName();
        removeById(reportId);
        fileService.delete(fileName);
    }

    // ---- helpers ----

    private List<InternshipReportResponse> toResponses(List<InternshipReport> reports) {
        if (reports.isEmpty()) {
            return List.of();
        }
        List<Long> internshipIds = reports.stream().map(InternshipReport::getInternshipId).distinct().toList();
        Map<Long, String> titleMap = internshipMapper.selectByIds(internshipIds).stream()
                .collect(Collectors.toMap(Internship::getId, Internship::getTitle, (a, b) -> a));
        List<Long> personIds = new ArrayList<>();
        reports.forEach(r -> personIds.add(r.getStudentId()));
        personIds.removeIf(Objects::isNull);
        Map<Long, String> nameMap = userMapper.toNameMap(personIds);
        return reports.stream()
                .map(r -> toResponse(r, titleMap.get(r.getInternshipId()), nameMap.get(r.getStudentId())))
                .toList();
    }

    private InternshipReportResponse toResponse(InternshipReport report, String internshipTitle, String studentName) {
        InternshipReportResponse resp = new InternshipReportResponse();
        resp.setId(report.getId());
        resp.setInternshipId(report.getInternshipId());
        resp.setInternshipTitle(internshipTitle);
        resp.setStudentId(report.getStudentId());
        resp.setStudentName(studentName);
        resp.setTitle(report.getTitle());
        resp.setSummary(report.getSummary());
        resp.setFileOriginal(report.getFileOriginal());
        resp.setSubmitTime(report.getSubmitTime());
        resp.setScore(report.getScore());
        resp.setFeedback(report.getFeedback());
        resp.setReviewTime(report.getReviewTime());
        resp.setStatus(report.getStatus());
        return resp;
    }

    private String nameOf(Long userId) {
        if (userId == null) {
            return null;
        }
        return userMapper.toNameMap(List.of(userId)).get(userId);
    }
}
