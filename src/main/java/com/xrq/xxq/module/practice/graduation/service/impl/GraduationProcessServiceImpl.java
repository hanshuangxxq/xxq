package com.xrq.xxq.module.practice.graduation.service.impl;

import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.xrq.xxq.common.BusinessException;
import com.xrq.xxq.common.event.PracticeNoticeEvent;
import com.xrq.xxq.module.practice.common.PracticeFileService;
import com.xrq.xxq.module.practice.graduation.dto.GuidanceLogCreateRequest;
import com.xrq.xxq.module.practice.graduation.dto.GuidanceLogResponse;
import com.xrq.xxq.module.practice.graduation.dto.MidtermResponse;
import com.xrq.xxq.module.practice.graduation.dto.MidtermReviewRequest;
import com.xrq.xxq.module.practice.graduation.dto.MidtermSubmitRequest;
import com.xrq.xxq.module.practice.graduation.dto.OpeningReportResponse;
import com.xrq.xxq.module.practice.graduation.dto.OpeningReportReviewRequest;
import com.xrq.xxq.module.practice.graduation.dto.OpeningReportSubmitRequest;
import com.xrq.xxq.module.practice.graduation.entity.GraduationAssignment;
import com.xrq.xxq.module.practice.graduation.entity.GraduationCampaign;
import com.xrq.xxq.module.practice.graduation.entity.GraduationGuidanceLog;
import com.xrq.xxq.module.practice.graduation.entity.GraduationMidterm;
import com.xrq.xxq.module.practice.graduation.entity.GraduationOpeningReport;
import com.xrq.xxq.module.practice.graduation.entity.GraduationProposal;
import com.xrq.xxq.module.practice.graduation.entity.OpeningReportStatusEnum;
import com.xrq.xxq.module.practice.graduation.entity.ProposalStatusEnum;
import com.xrq.xxq.module.practice.graduation.mapper.GraduationAssignmentMapper;
import com.xrq.xxq.module.practice.graduation.mapper.GraduationCampaignMapper;
import com.xrq.xxq.module.practice.graduation.mapper.GraduationGuidanceLogMapper;
import com.xrq.xxq.module.practice.graduation.mapper.GraduationMidtermMapper;
import com.xrq.xxq.module.practice.graduation.mapper.GraduationOpeningReportMapper;
import com.xrq.xxq.module.practice.graduation.mapper.GraduationProposalMapper;
import com.xrq.xxq.module.practice.graduation.service.GraduationLogService;
import com.xrq.xxq.module.practice.graduation.service.GraduationProcessService;
import com.xrq.xxq.module.user.mapper.UserMapper;
import com.xrq.xxq.util.ParamValidator;
import com.xrq.xxq.util.StudentScopeResolver;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class GraduationProcessServiceImpl
        extends ServiceImpl<GraduationOpeningReportMapper, GraduationOpeningReport>
        implements GraduationProcessService {

    private final GraduationCampaignMapper campaignMapper;
    private final GraduationAssignmentMapper assignmentMapper;
    private final GraduationProposalMapper proposalMapper;
    private final GraduationMidtermMapper midtermMapper;
    private final GraduationGuidanceLogMapper guidanceLogMapper;
    private final UserMapper userMapper;
    private final StudentScopeResolver scopeResolver;
    private final PracticeFileService fileService;
    private final GraduationLogService logService;
    private final ApplicationEventPublisher eventPublisher;

    // ==================== 开题报告 ====================

    @Override
    @Transactional
    public OpeningReportResponse submitOpeningReport(Long studentUserId, OpeningReportSubmitRequest request,
                                                     MultipartFile file) {
        ParamValidator.requireNonNull(request.getCampaignId(), "活动");
        ParamValidator.requireNonBlank(request.getTitle(), "开题报告标题");
        ParamValidator.requireNonBlank(request.getContent(), "开题报告内容");
        GraduationCampaign campaign = requireCampaign(request.getCampaignId());
        // R-3.1：选题审批完毕且已确定指导教师后才能进入阶段二
        ensureStageTwoEligible(campaign, studentUserId);
        LocalDateTime now = LocalDateTime.now();
        checkWindow(campaign.getOpeningStartTime(), campaign.getOpeningEndTime(), "开题报告");

        GraduationOpeningReport report = baseMapper.selectOne(new LambdaQueryWrapper<GraduationOpeningReport>()
                .eq(GraduationOpeningReport::getCampaignId, campaign.getId())
                .eq(GraduationOpeningReport::getStudentId, studentUserId)
                .last("LIMIT 1"));
        Boolean isNew = report == null;
        if (!isNew && report.getStatus() == OpeningReportStatusEnum.APPROVED) {
            throw new BusinessException(409, "开题报告已通过审核，不可再修改");
        }
        if (isNew) {
            report = new GraduationOpeningReport();
            report.setCampaignId(campaign.getId());
            report.setStudentId(studentUserId);
        }
        // 附件可选：重提时旧文件先删，新文件再存
        if (file != null && !file.isEmpty()) {
            if (report.getFileName() != null) {
                fileService.delete(report.getFileName());
            }
            PracticeFileService.StoredFile stored = fileService.store(file);
            report.setFileName(stored.storedName());
            report.setFileOriginal(stored.originalName());
        }
        report.setTitle(request.getTitle().trim());
        report.setContent(request.getContent().trim());
        report.setStatus(OpeningReportStatusEnum.SUBMITTED);
        report.setSubmitTime(now);
        report.setReviewComment(null);
        report.setReviewTime(null);
        if (isNew) {
            baseMapper.insert(report);
        } else {
            baseMapper.updateById(report);
        }
        return toOpeningResponse(report);
    }

    @Override
    @Transactional
    public OpeningReportResponse reviewOpeningReport(Long teacherUserId, Long reportId,
                                                     OpeningReportReviewRequest request) {
        if (request.getApprove() == null) {
            throw new BusinessException(400, "审核结果不能为空");
        }
        GraduationOpeningReport report = baseMapper.selectById(reportId);
        if (report == null) {
            throw new BusinessException(404, "开题报告不存在");
        }
        ensureTeacherOfStudent(teacherUserId, report.getCampaignId(), report.getStudentId());
        if (report.getStatus() != OpeningReportStatusEnum.SUBMITTED) {
            throw new BusinessException(409, "该开题报告已审核");
        }
        if (!request.getApprove() && (request.getComment() == null || request.getComment().isBlank())) {
            throw new BusinessException(400, "退回必须填写审核意见");
        }
        report.setStatus(request.getApprove() ? OpeningReportStatusEnum.APPROVED : OpeningReportStatusEnum.REVISION);
        report.setReviewTeacherId(teacherUserId);
        report.setReviewComment(request.getComment());
        report.setReviewTime(LocalDateTime.now());
        baseMapper.updateById(report);
        eventPublisher.publishEvent(new PracticeNoticeEvent(report.getStudentId(),
                "开题报告审核结果",
                "你的开题报告《" + report.getTitle() + "》"
                        + (request.getApprove() ? "已通过。" : "被退回修改：" + request.getComment())));
        logService.record(report.getCampaignId(), teacherUserId, "teacher", "审核开题报告",
                "graduation_opening_report", reportId,
                "结果: " + (request.getApprove() ? "通过" : "退回"));
        return toOpeningResponse(report);
    }

    @Override
    public OpeningReportResponse getMyOpeningReport(Long studentUserId, Long campaignId) {
        GraduationOpeningReport report = baseMapper.selectOne(new LambdaQueryWrapper<GraduationOpeningReport>()
                .eq(GraduationOpeningReport::getCampaignId, campaignId)
                .eq(GraduationOpeningReport::getStudentId, studentUserId)
                .last("LIMIT 1"));
        return report == null ? null : toOpeningResponse(report);
    }

    @Override
    public List<OpeningReportResponse> listOpeningReportsByTeacher(Long teacherUserId, Long campaignId) {
        List<GraduationAssignment> assignments = assignmentMapper.selectList(
                new LambdaQueryWrapper<GraduationAssignment>()
                        .eq(GraduationAssignment::getCampaignId, campaignId)
                        .eq(GraduationAssignment::getTeacherId, teacherUserId));
        if (assignments.isEmpty()) {
            return List.of();
        }
        List<Long> studentIds = assignments.stream().map(GraduationAssignment::getStudentId).toList();
        List<GraduationOpeningReport> reports = baseMapper.selectList(
                new LambdaQueryWrapper<GraduationOpeningReport>()
                        .eq(GraduationOpeningReport::getCampaignId, campaignId)
                        .in(GraduationOpeningReport::getStudentId, studentIds)
                        .orderByDesc(GraduationOpeningReport::getSubmitTime));
        return toOpeningResponses(reports);
    }

    // ==================== 中期检查 ====================

    @Override
    @Transactional
    public MidtermResponse submitMidterm(Long studentUserId, MidtermSubmitRequest request, MultipartFile file) {
        ParamValidator.requireNonNull(request.getCampaignId(), "活动");
        ParamValidator.requireNonBlank(request.getContent(), "中期检查内容");
        GraduationCampaign campaign = requireCampaign(request.getCampaignId());
        // 门禁：开题已通过（R-3.2 前置环节要求）
        GraduationOpeningReport opening = baseMapper.selectOne(new LambdaQueryWrapper<GraduationOpeningReport>()
                .eq(GraduationOpeningReport::getCampaignId, campaign.getId())
                .eq(GraduationOpeningReport::getStudentId, studentUserId)
                .last("LIMIT 1"));
        if (opening == null || opening.getStatus() != OpeningReportStatusEnum.APPROVED) {
            throw new BusinessException(409, "开题报告通过后才能提交中期检查");
        }
        checkWindow(campaign.getMidtermStartTime(), campaign.getMidtermEndTime(), "中期检查");

        GraduationMidterm midterm = midtermMapper.selectOne(new LambdaQueryWrapper<GraduationMidterm>()
                .eq(GraduationMidterm::getCampaignId, campaign.getId())
                .eq(GraduationMidterm::getStudentId, studentUserId)
                .last("LIMIT 1"));
        Boolean isNew = midterm == null;
        if (!isNew && "REVIEWED".equals(midterm.getStatus())) {
            throw new BusinessException(409, "中期检查已评审，不可再修改");
        }
        if (isNew) {
            midterm = new GraduationMidterm();
            midterm.setCampaignId(campaign.getId());
            midterm.setStudentId(studentUserId);
            GraduationAssignment assignment = findAssignment(campaign.getId(), studentUserId);
            if (assignment == null) {
                throw new BusinessException(409, "尚未确定指导教师");
            }
            midterm.setAssignmentId(assignment.getId());
        }
        if (file != null && !file.isEmpty()) {
            if (midterm.getFileName() != null) {
                fileService.delete(midterm.getFileName());
            }
            PracticeFileService.StoredFile stored = fileService.store(file);
            midterm.setFileName(stored.storedName());
            midterm.setFileOriginal(stored.originalName());
        }
        midterm.setContent(request.getContent().trim());
        midterm.setStatus("SUBMITTED");
        midterm.setSubmitTime(LocalDateTime.now());
        if (isNew) {
            midtermMapper.insert(midterm);
        } else {
            midtermMapper.updateById(midterm);
        }
        return toMidtermResponse(midterm);
    }

    @Override
    @Transactional
    public MidtermResponse reviewMidterm(Long teacherUserId, Long midtermId, MidtermReviewRequest request) {
        if (request.getConclusion() == null) {
            throw new BusinessException(400, "中期结论不能为空");
        }
        GraduationMidterm midterm = midtermMapper.selectById(midtermId);
        if (midterm == null) {
            throw new BusinessException(404, "中期检查不存在");
        }
        ensureTeacherOfStudent(teacherUserId, midterm.getCampaignId(), midterm.getStudentId());
        if (!"SUBMITTED".equals(midterm.getStatus())) {
            throw new BusinessException(409, "该中期检查已评审");
        }
        midterm.setStatus("REVIEWED");
        midterm.setConclusion(request.getConclusion());
        midterm.setReviewTeacherId(teacherUserId);
        midterm.setReviewComment(request.getComment());
        midterm.setReviewTime(LocalDateTime.now());
        midtermMapper.updateById(midterm);
        eventPublisher.publishEvent(new PracticeNoticeEvent(midterm.getStudentId(),
                "中期检查评审结果",
                "你的中期检查结论：" + request.getConclusion().getDescription()
                        + (request.getComment() != null ? "，" + request.getComment() : "") + "。"));
        logService.record(midterm.getCampaignId(), teacherUserId, "teacher", "评审中期检查",
                "graduation_midterm", midtermId, "结论: " + request.getConclusion().getDescription());
        return toMidtermResponse(midterm);
    }

    @Override
    public MidtermResponse getMyMidterm(Long studentUserId, Long campaignId) {
        GraduationMidterm midterm = midtermMapper.selectOne(new LambdaQueryWrapper<GraduationMidterm>()
                .eq(GraduationMidterm::getCampaignId, campaignId)
                .eq(GraduationMidterm::getStudentId, studentUserId)
                .last("LIMIT 1"));
        return midterm == null ? null : toMidtermResponse(midterm);
    }

    @Override
    public List<MidtermResponse> listMidtermsByTeacher(Long teacherUserId, Long campaignId) {
        List<GraduationAssignment> assignments = assignmentMapper.selectList(
                new LambdaQueryWrapper<GraduationAssignment>()
                        .eq(GraduationAssignment::getCampaignId, campaignId)
                        .eq(GraduationAssignment::getTeacherId, teacherUserId));
        if (assignments.isEmpty()) {
            return List.of();
        }
        List<Long> studentIds = assignments.stream().map(GraduationAssignment::getStudentId).toList();
        List<GraduationMidterm> midterms = midtermMapper.selectList(
                new LambdaQueryWrapper<GraduationMidterm>()
                        .eq(GraduationMidterm::getCampaignId, campaignId)
                        .in(GraduationMidterm::getStudentId, studentIds)
                        .orderByDesc(GraduationMidterm::getSubmitTime));
        return toMidtermResponses(midterms);
    }

    // ==================== 过程指导记录 ====================

    @Override
    @Transactional
    public GuidanceLogResponse createGuidanceLog(Long teacherUserId, GuidanceLogCreateRequest request) {
        ParamValidator.requireNonNull(request.getCampaignId(), "活动");
        ParamValidator.requireNonNull(request.getStudentId(), "学生");
        ParamValidator.requireNonNull(request.getLogTime(), "指导时间");
        ParamValidator.requireNonNull(request.getForm(), "指导形式");
        ParamValidator.requireNonBlank(request.getSummary(), "指导内容摘要");
        ensureTeacherOfStudent(teacherUserId, request.getCampaignId(), request.getStudentId());
        GraduationAssignment assignment = findAssignment(request.getCampaignId(), request.getStudentId());
        if (assignment == null) {
            throw new BusinessException(409, "该学生不在你名下");
        }
        GraduationGuidanceLog log = new GraduationGuidanceLog();
        log.setCampaignId(request.getCampaignId());
        log.setAssignmentId(assignment.getId());
        log.setTeacherId(teacherUserId);
        log.setStudentId(request.getStudentId());
        log.setLogTime(request.getLogTime());
        log.setForm(request.getForm());
        log.setSummary(request.getSummary().trim());
        guidanceLogMapper.insert(log);
        return toGuidanceResponse(log);
    }

    @Override
    public List<GuidanceLogResponse> listGuidanceLogs(Long campaignId, Long studentId,
                                                      String userType, Long operatorUserId) {
        LambdaQueryWrapper<GraduationGuidanceLog> wrapper = new LambdaQueryWrapper<GraduationGuidanceLog>()
                .eq(campaignId != null, GraduationGuidanceLog::getCampaignId, campaignId)
                .eq(studentId != null, GraduationGuidanceLog::getStudentId, studentId)
                .orderByDesc(GraduationGuidanceLog::getLogTime);
        List<GraduationGuidanceLog> logs = guidanceLogMapper.selectList(wrapper);
        if ("teacher".equals(userType)) {
            logs = logs.stream().filter(l -> l.getTeacherId().equals(operatorUserId)).toList();
        } else if ("department".equals(userType)) {
            // R-10.1 院系可见性：批量解析学生院系后内存过滤（替代逐条 departmentOwnsStudent 查库）
            Long deptCollegeId = scopeResolver.deptCollegeId(operatorUserId);
            Map<Long, Long> collegeByStudent = scopeResolver.studentCollegeIdMap(
                    logs.stream().map(GraduationGuidanceLog::getStudentId).toList());
            logs = logs.stream()
                    .filter(l -> deptCollegeId != null
                            && Objects.equals(deptCollegeId, collegeByStudent.get(l.getStudentId())))
                    .toList();
        }
        return toGuidanceResponses(logs);
    }

    // ==================== 附件下载（权限校验后返回文件路径） ====================

    @Override
    public FileView resolveOpeningReportFile(String userType, Long userId, Long reportId) {
        GraduationOpeningReport report = baseMapper.selectById(reportId);
        if (report == null || report.getFileName() == null) {
            throw new BusinessException(404, "开题报告不存在");
        }
        checkFileAccess(userType, userId, report.getCampaignId(), report.getStudentId());
        return new FileView(fileService.resolve(report.getFileName()), report.getFileOriginal());
    }

    @Override
    public FileView resolveMidtermFile(String userType, Long userId, Long midtermId) {
        GraduationMidterm midterm = midtermMapper.selectById(midtermId);
        if (midterm == null || midterm.getFileName() == null) {
            throw new BusinessException(404, "中期检查不存在");
        }
        checkFileAccess(userType, userId, midterm.getCampaignId(), midterm.getStudentId());
        return new FileView(fileService.resolve(midterm.getFileName()), midterm.getFileOriginal());
    }

    // ---- helpers ----

    private void ensureStageTwoEligible(GraduationCampaign campaign, Long studentUserId) {
        // R-3.1：选题审批完毕（APPROVED）且已确定指导教师
        GraduationProposal proposal = proposalMapper.selectOne(new LambdaQueryWrapper<GraduationProposal>()
                .eq(GraduationProposal::getCampaignId, campaign.getId())
                .eq(GraduationProposal::getStudentId, studentUserId)
                .last("LIMIT 1"));
        if (proposal == null || proposal.getStatus() != ProposalStatusEnum.APPROVED) {
            throw new BusinessException(409, "选题审批完毕后才能提交开题报告");
        }
        if (findAssignment(campaign.getId(), studentUserId) == null) {
            throw new BusinessException(409, "确定指导教师后才能提交开题报告");
        }
    }

    private void checkWindow(LocalDateTime start, LocalDateTime end, String name) {
        LocalDateTime now = LocalDateTime.now();
        if (start != null && now.isBefore(start)) {
            throw new BusinessException(409, name + "提交尚未开始");
        }
        if (end != null && now.isAfter(end)) {
            throw new BusinessException(409, name + "提交已截止");
        }
    }

    private GraduationAssignment findAssignment(Long campaignId, Long studentId) {
        return assignmentMapper.selectOne(new LambdaQueryWrapper<GraduationAssignment>()
                .eq(GraduationAssignment::getCampaignId, campaignId)
                .eq(GraduationAssignment::getStudentId, studentId)
                .last("LIMIT 1"));
    }

    /** 教师只能操作自己名下学生（R-10.1） */
    private void ensureTeacherOfStudent(Long teacherUserId, Long campaignId, Long studentId) {
        GraduationAssignment assignment = findAssignment(campaignId, studentId);
        if (assignment == null || !assignment.getTeacherId().equals(teacherUserId)) {
            throw new BusinessException(403, "权限不足");
        }
    }

    private void checkFileAccess(String userType, Long userId, Long campaignId, Long studentId) {
        if ("student".equals(userType)) {
            if (!userId.equals(studentId)) {
                throw new BusinessException(403, "权限不足");
            }
            return;
        }
        if ("teacher".equals(userType)) {
            GraduationAssignment assignment = findAssignment(campaignId, studentId);
            if (assignment == null || !assignment.getTeacherId().equals(userId)) {
                throw new BusinessException(403, "权限不足");
            }
            return;
        }
        if ("department".equals(userType)) {
            if (scopeResolver.departmentOwnsStudent(userId, studentId)) {
                throw new BusinessException(403, "权限不足");
            }
            return;
        }
        // academic_admin：全量可见
    }

    private GraduationCampaign requireCampaign(Long id) {
        GraduationCampaign campaign = campaignMapper.selectById(id);
        if (campaign == null) {
            throw new BusinessException(404, "活动不存在");
        }
        return campaign;
    }

    private String nameOf(Long userId) {
        if (userId == null) {
            return null;
        }
        return userMapper.toNameMap(List.of(userId)).get(userId);
    }

    private List<OpeningReportResponse> toOpeningResponses(List<GraduationOpeningReport> reports) {
        if (reports.isEmpty()) {
            return List.of();
        }
        List<Long> studentIds = reports.stream().map(GraduationOpeningReport::getStudentId).distinct().toList();
        Map<Long, String> nameMap = userMapper.toNameMap(studentIds);
        return reports.stream().map(r -> toOpeningResponse(r, nameMap.get(r.getStudentId()))).toList();
    }

    private OpeningReportResponse toOpeningResponse(GraduationOpeningReport report) {
        return toOpeningResponse(report, nameOf(report.getStudentId()));
    }

    private OpeningReportResponse toOpeningResponse(GraduationOpeningReport report, String studentName) {
        OpeningReportResponse resp = new OpeningReportResponse();
        resp.setId(report.getId());
        resp.setCampaignId(report.getCampaignId());
        resp.setAssignmentId(report.getAssignmentId());
        resp.setStudentId(report.getStudentId());
        resp.setStudentName(studentName);
        resp.setTitle(report.getTitle());
        resp.setContent(report.getContent());
        resp.setFileOriginal(report.getFileOriginal());
        resp.setStatus(report.getStatus());
        resp.setSubmitTime(report.getSubmitTime());
        resp.setReviewTeacherId(report.getReviewTeacherId());
        resp.setReviewTeacherName(report.getReviewTeacherId() != null ? nameOf(report.getReviewTeacherId()) : null);
        resp.setReviewComment(report.getReviewComment());
        resp.setReviewTime(report.getReviewTime());
        return resp;
    }

    private List<MidtermResponse> toMidtermResponses(List<GraduationMidterm> midterms) {
        if (midterms.isEmpty()) {
            return List.of();
        }
        List<Long> studentIds = midterms.stream().map(GraduationMidterm::getStudentId).distinct().toList();
        Map<Long, String> nameMap = userMapper.toNameMap(studentIds);
        return midterms.stream().map(m -> toMidtermResponse(m, nameMap.get(m.getStudentId()))).toList();
    }

    private MidtermResponse toMidtermResponse(GraduationMidterm midterm) {
        return toMidtermResponse(midterm, nameOf(midterm.getStudentId()));
    }

    private MidtermResponse toMidtermResponse(GraduationMidterm midterm, String studentName) {
        MidtermResponse resp = new MidtermResponse();
        resp.setId(midterm.getId());
        resp.setCampaignId(midterm.getCampaignId());
        resp.setAssignmentId(midterm.getAssignmentId());
        resp.setStudentId(midterm.getStudentId());
        resp.setStudentName(studentName);
        resp.setContent(midterm.getContent());
        resp.setFileOriginal(midterm.getFileOriginal());
        resp.setStatus(midterm.getStatus());
        resp.setConclusion(midterm.getConclusion());
        resp.setSubmitTime(midterm.getSubmitTime());
        resp.setReviewTeacherId(midterm.getReviewTeacherId());
        resp.setReviewTeacherName(midterm.getReviewTeacherId() != null ? nameOf(midterm.getReviewTeacherId()) : null);
        resp.setReviewComment(midterm.getReviewComment());
        resp.setReviewTime(midterm.getReviewTime());
        return resp;
    }

    private List<GuidanceLogResponse> toGuidanceResponses(List<GraduationGuidanceLog> logs) {
        if (logs.isEmpty()) {
            return List.of();
        }
        List<Long> studentIds = logs.stream().map(GraduationGuidanceLog::getStudentId).distinct().toList();
        Map<Long, String> nameMap = userMapper.toNameMap(studentIds);
        return logs.stream().map(l -> {
            GuidanceLogResponse resp = new GuidanceLogResponse();
            resp.setId(l.getId());
            resp.setCampaignId(l.getCampaignId());
            resp.setStudentId(l.getStudentId());
            resp.setStudentName(nameMap.get(l.getStudentId()));
            resp.setLogTime(l.getLogTime());
            resp.setForm(l.getForm());
            resp.setSummary(l.getSummary());
            resp.setCreateTime(l.getCreateTime());
            return resp;
        }).toList();
    }

    private GuidanceLogResponse toGuidanceResponse(GraduationGuidanceLog log) {
        return toGuidanceResponses(List.of(log)).get(0);
    }
}
