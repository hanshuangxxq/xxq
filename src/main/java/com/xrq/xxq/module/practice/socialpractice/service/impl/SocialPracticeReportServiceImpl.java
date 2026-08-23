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
import com.xrq.xxq.module.practice.socialpractice.dto.SocialPracticeReportResponse;
import com.xrq.xxq.module.practice.socialpractice.dto.SocialPracticeReportReviewRequest;
import com.xrq.xxq.module.practice.socialpractice.dto.SocialPracticeReportSubmitRequest;
import com.xrq.xxq.module.practice.socialpractice.entity.SocialPractice;
import com.xrq.xxq.module.practice.socialpractice.entity.SocialPracticeApplication;
import com.xrq.xxq.module.practice.socialpractice.entity.SocialPracticeReport;
import com.xrq.xxq.module.practice.socialpractice.mapper.SocialPracticeApplicationMapper;
import com.xrq.xxq.module.practice.socialpractice.mapper.SocialPracticeMapper;
import com.xrq.xxq.module.practice.socialpractice.mapper.SocialPracticeReportMapper;
import com.xrq.xxq.module.practice.socialpractice.service.SocialPracticeReportService;
import com.xrq.xxq.module.user.mapper.UserMapper;
import com.xrq.xxq.util.ParamValidator;
import com.xrq.xxq.util.auth.AuthFacade;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class SocialPracticeReportServiceImpl
        extends ServiceImpl<SocialPracticeReportMapper, SocialPracticeReport>
        implements SocialPracticeReportService {

    private final SocialPracticeMapper practiceMapper;
    private final SocialPracticeApplicationMapper applicationMapper;
    private final UserMapper userMapper;
    private final PracticeFileService fileService;
    private final ApplicationEventPublisher eventPublisher;

    @Override
    @Transactional
    public SocialPracticeReportResponse submit(Long studentUserId, SocialPracticeReportSubmitRequest request, MultipartFile file) {
        ParamValidator.requireNonNull(request.getPracticeId(), "实践项目");
        ParamValidator.requireNonBlank(request.getTitle(), "报告标题");
        SocialPractice practice = practiceMapper.selectById(request.getPracticeId());
        if (practice == null) {
            throw new BusinessException(404, "实践项目不存在");
        }
        SocialPracticeApplication app = applicationMapper.selectOne(new LambdaQueryWrapper<SocialPracticeApplication>()
                .eq(SocialPracticeApplication::getPracticeId, request.getPracticeId())
                .eq(SocialPracticeApplication::getStudentId, studentUserId)
                .eq(SocialPracticeApplication::getStatus, AuditStatusEnum.APPROVED)
                .last("LIMIT 1"));
        if (app == null) {
            throw new BusinessException(409, "实践未通过审核，不可提交报告");
        }
        SocialPracticeReport existing = baseMapper.selectOne(new LambdaQueryWrapper<SocialPracticeReport>()
                .eq(SocialPracticeReport::getPracticeId, request.getPracticeId())
                .eq(SocialPracticeReport::getStudentId, studentUserId)
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
            return toResponse(existing, practice.getTitle(), nameOf(studentUserId));
        }
        PracticeFileService.StoredFile stored = fileService.store(file);
        SocialPracticeReport report = new SocialPracticeReport();
        report.setPracticeId(request.getPracticeId());
        report.setStudentId(studentUserId);
        report.setTitle(request.getTitle());
        report.setSummary(request.getSummary());
        report.setFileName(stored.storedName());
        report.setFileOriginal(stored.originalName());
        report.setSubmitTime(LocalDateTime.now());
        report.setStatus(ReportStatusEnum.SUBMITTED);
        save(report);
        return toResponse(report, practice.getTitle(), nameOf(studentUserId));
    }

    @Override
    @Transactional
    public SocialPracticeReportResponse review(Long reportId, SocialPracticeReportReviewRequest request) {
        SocialPracticeReport report = baseMapper.selectById(reportId);
        if (report == null) {
            throw new BusinessException(404, "报告不存在");
        }
        SocialPractice practice = practiceMapper.selectById(report.getPracticeId());
        if (practice == null) {
            throw new BusinessException(404, "实践项目不存在");
        }
        if (report.getStatus() != ReportStatusEnum.SUBMITTED) {
            throw new BusinessException(409, "该报告已评审");
        }
        report.setScore(request.getScore());
        report.setFeedback(request.getFeedback());
        report.setReviewTime(LocalDateTime.now());
        report.setStatus(ReportStatusEnum.REVIEWED);
        updateById(report);
        String title = "社会实践报告评审结果";
        String content = "您的实践《" + practice.getTitle() + "》报告已评审完成。";
        eventPublisher.publishEvent(new PracticeNoticeEvent(report.getStudentId(), title, content));
        return toResponse(report, practice.getTitle(), nameOf(report.getStudentId()));
    }

    @Override
    public List<SocialPracticeReportResponse> listMyReports(Long studentUserId) {
        List<SocialPracticeReport> reports = baseMapper.selectList(new LambdaQueryWrapper<SocialPracticeReport>()
                .eq(SocialPracticeReport::getStudentId, studentUserId)
                .orderByDesc(SocialPracticeReport::getId));
        return toResponses(reports);
    }

    @Override
    public PageResult<SocialPracticeReportResponse> listForHandler(ReportStatusEnum status, PageQuery pageQuery) {
        LambdaQueryWrapper<SocialPracticeReport> wrapper = new LambdaQueryWrapper<SocialPracticeReport>()
                .orderByDesc(SocialPracticeReport::getId);
        if (status != null) {
            wrapper.eq(SocialPracticeReport::getStatus, status);
        }
        Page<SocialPracticeReport> page = baseMapper.selectPage(pageQuery.toPage(), wrapper);
        return PageResult.of(page, toResponses(page.getRecords()));
    }

    @Override
    public SocialPracticeReport loadForDownload(Long reportId, Long operatorUserId, String userType) {
        SocialPracticeReport report = baseMapper.selectById(reportId);
        if (report == null) {
            throw new BusinessException(404, "报告不存在");
        }
        if (!AuthFacade.USER_TYPE_ACADEMIC_ADMIN.equals(userType)
                && !report.getStudentId().equals(operatorUserId)) {
            throw new BusinessException(403, "权限不足");
        }
        return report;
    }

    @Override
    @Transactional
    public void deleteReport(Long reportId, Long operatorUserId, String userType) {
        SocialPracticeReport report = baseMapper.selectById(reportId);
        if (report == null) {
            throw new BusinessException(404, "报告不存在");
        }
        if (!AuthFacade.USER_TYPE_ACADEMIC_ADMIN.equals(userType)) {
            Boolean isOwnerStudent = report.getStudentId() != null && report.getStudentId().equals(operatorUserId)
                    && report.getStatus() == ReportStatusEnum.SUBMITTED;
            if (!isOwnerStudent) {
                throw new BusinessException(403, "权限不足");
            }
        }
        String fileName = report.getFileName();
        removeById(reportId);
        fileService.delete(fileName);
    }

    // ---- helpers ----

    private List<SocialPracticeReportResponse> toResponses(List<SocialPracticeReport> reports) {
        if (reports.isEmpty()) {
            return List.of();
        }
        List<Long> practiceIds = reports.stream().map(SocialPracticeReport::getPracticeId).distinct().toList();
        Map<Long, String> titleMap = practiceMapper.selectByIds(practiceIds).stream()
                .collect(Collectors.toMap(SocialPractice::getId, SocialPractice::getTitle, (a, b) -> a));
        List<Long> personIds = new ArrayList<>();
        reports.forEach(r -> personIds.add(r.getStudentId()));
        personIds.removeIf(Objects::isNull);
        Map<Long, String> nameMap = userMapper.toNameMap(personIds);
        return reports.stream()
                .map(r -> toResponse(r, titleMap.get(r.getPracticeId()), nameMap.get(r.getStudentId())))
                .toList();
    }

    private SocialPracticeReportResponse toResponse(SocialPracticeReport report, String practiceTitle, String studentName) {
        SocialPracticeReportResponse resp = new SocialPracticeReportResponse();
        resp.setId(report.getId());
        resp.setPracticeId(report.getPracticeId());
        resp.setPracticeTitle(practiceTitle);
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
