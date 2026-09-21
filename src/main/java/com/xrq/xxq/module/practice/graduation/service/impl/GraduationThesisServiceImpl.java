package com.xrq.xxq.module.practice.graduation.service.impl;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.xrq.xxq.common.BusinessException;
import com.xrq.xxq.module.notification.notice.PracticeNoticeScenes;
import com.xrq.xxq.module.file.dto.StoredFileRef;
import com.xrq.xxq.module.file.entity.FileBizEnum;
import com.xrq.xxq.module.file.service.FileStorageService;
import com.xrq.xxq.module.practice.common.FileView;
import com.xrq.xxq.module.practice.common.PracticeFileSupport;
import com.xrq.xxq.module.practice.graduation.dto.DuplicateCheckRegisterRequest;
import com.xrq.xxq.module.practice.graduation.dto.DuplicateCheckResponse;
import com.xrq.xxq.module.practice.graduation.dto.ThesisResponse;
import com.xrq.xxq.module.practice.graduation.dto.ThesisReviewRequest;
import com.xrq.xxq.module.practice.graduation.dto.ThesisSubmitRequest;
import com.xrq.xxq.module.practice.graduation.entity.GraduationAssignment;
import com.xrq.xxq.module.practice.graduation.entity.GraduationCampaign;
import com.xrq.xxq.module.practice.graduation.entity.GraduationDuplicateCheck;
import com.xrq.xxq.module.practice.graduation.entity.GraduationOpeningReport;
import com.xrq.xxq.module.practice.graduation.entity.GraduationThesis;
import com.xrq.xxq.module.practice.graduation.entity.OpeningReportStatusEnum;
import com.xrq.xxq.module.practice.graduation.entity.ThesisStatusEnum;
import com.xrq.xxq.module.practice.graduation.mapper.GraduationAssignmentMapper;
import com.xrq.xxq.module.practice.graduation.mapper.GraduationCampaignMapper;
import com.xrq.xxq.module.practice.graduation.mapper.GraduationDuplicateCheckMapper;
import com.xrq.xxq.module.practice.graduation.mapper.GraduationOpeningReportMapper;
import com.xrq.xxq.module.practice.graduation.mapper.GraduationThesisMapper;
import com.xrq.xxq.module.practice.graduation.service.GraduationLogService;
import com.xrq.xxq.module.practice.graduation.service.GraduationThesisService;
import com.xrq.xxq.module.clazz.service.ClassNameService;
import com.xrq.xxq.module.college.mapper.CollegeMapper;
import com.xrq.xxq.module.user.entity.user.Student;
import com.xrq.xxq.module.user.mapper.StudentMapper;
import com.xrq.xxq.module.user.mapper.UserMapper;
import com.xrq.xxq.util.ParamValidator;
import com.xrq.xxq.util.StudentScopeResolver;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class GraduationThesisServiceImpl
        extends ServiceImpl<GraduationThesisMapper, GraduationThesis>
        implements GraduationThesisService {

    /** 版本管理：保留最近 N 版（R-8.2 默认 N=3） */
    private static final int MAX_VERSIONS = 3;

    @Value("${file.storage-path:uploads/files}")
    private String fileStoragePath;

    private final GraduationCampaignMapper campaignMapper;
    private final GraduationAssignmentMapper assignmentMapper;
    private final GraduationOpeningReportMapper openingReportMapper;
    private final GraduationDuplicateCheckMapper duplicateCheckMapper;
    private final StudentMapper studentMapper;
    private final UserMapper userMapper;
    private final ClassNameService classNameService;
    private final CollegeMapper collegeMapper;
    private final StudentScopeResolver scopeResolver;
    private final PracticeFileSupport fileSupport;
    private final GraduationLogService logService;
    private final PracticeNoticeScenes practiceNoticeScenes;

    @Override
    @Transactional
    public ThesisResponse submitThesis(Long studentUserId, ThesisSubmitRequest request, MultipartFile file) {
        ParamValidator.requireNonNull(request.getCampaignId(), "活动");
        ParamValidator.requireNonBlank(request.getTitle(), "论文题目");
        // 注意：不要在这里校验 file 非空 —— 大文件走分片时客户端只传 data.filePath、
        // 根本没有 file 部分，提前拦截会把分片提交这条路整个堵死。
        // 文件存在性由下面的 fileSupport.resolveSubmit(..., required=true) 统一负责。
        GraduationCampaign campaign = requireCampaign(request.getCampaignId());
        // 门禁 R-3.2：开题通过后才能提交论文（中期为软门禁，开题硬门禁）
        GraduationOpeningReport opening = openingReportMapper.selectOne(
                new LambdaQueryWrapper<GraduationOpeningReport>()
                        .eq(GraduationOpeningReport::getCampaignId, campaign.getId())
                        .eq(GraduationOpeningReport::getStudentId, studentUserId)
                        .last("LIMIT 1"));
        if (opening == null || opening.getStatus() != OpeningReportStatusEnum.APPROVED) {
            throw new BusinessException(409, "开题报告通过后才能提交论文");
        }
        LocalDateTime now = LocalDateTime.now();
        if (campaign.getThesisStartTime() != null && now.isBefore(campaign.getThesisStartTime())) {
            throw new BusinessException(409, "论文提交尚未开始");
        }
        if (campaign.getThesisEndTime() != null && now.isAfter(campaign.getThesisEndTime())) {
            throw new BusinessException(409, "论文提交已截止");
        }
        GraduationAssignment assignment = assignmentMapper.selectOne(new LambdaQueryWrapper<GraduationAssignment>()
                .eq(GraduationAssignment::getCampaignId, campaign.getId())
                .eq(GraduationAssignment::getStudentId, studentUserId)
                .last("LIMIT 1"));
        if (assignment == null) {
            throw new BusinessException(409, "尚未确定指导教师，无法提交论文");
        }

        // R-8.2 版本管理：最新版可重提状态才允许新版本（待审查/退回/查重不通过）
        GraduationThesis latest = findLatest(campaign.getId(), studentUserId);
        if (latest != null) {
            ThesisStatusEnum s = latest.getStatus();
            if (s == ThesisStatusEnum.APPROVED) {
                throw new BusinessException(409, "论文已通过形式审查待查重，不可修改");
            }
            if (s == ThesisStatusEnum.DUPLICATE_PASSED) {
                throw new BusinessException(409, "论文已查重通过，不可修改");
            }
        }

        // 文件：整传（file）与分片产物（filePath）二选一，必填
        StoredFileRef ref = fileSupport.resolveSubmit(request.getFilePath(), request.getFileOriginal(),
                file, FileBizEnum.GRADUATION_THESIS, true);
        GraduationThesis thesis = new GraduationThesis();
        thesis.setCampaignId(campaign.getId());
        thesis.setAssignmentId(assignment.getId());
        thesis.setStudentId(studentUserId);
        thesis.setTitle(request.getTitle().trim());
        thesis.setFileName(ref.storedPath());
        thesis.setFileOriginal(ref.originalName());
        thesis.setVersion(latest != null ? latest.getVersion() + 1 : 1);
        thesis.setIsLatest(1);
        thesis.setStatus(ThesisStatusEnum.SUBMITTED);
        thesis.setSubmitTime(now);
        // 旧最新版不再是最新
        if (latest != null) {
            latest.setIsLatest(0);
            baseMapper.updateById(latest);
        }
        baseMapper.insert(thesis);
        // 版本数超限：删除最旧版本（记录 + 文件）
        List<GraduationThesis> allVersions = baseMapper.selectList(new LambdaQueryWrapper<GraduationThesis>()
                .eq(GraduationThesis::getAssignmentId, assignment.getId())
                .orderByAsc(GraduationThesis::getVersion));
        while (allVersions.size() > MAX_VERSIONS) {
            GraduationThesis oldest = allVersions.removeFirst ();
            baseMapper.deleteById(oldest.getId());
            fileSupport.release(oldest.getFileName());
        }
        return toResponse(thesis);
    }

    @Override
    @Transactional
    public ThesisResponse reviewThesis(Long teacherUserId, Long thesisId, ThesisReviewRequest request) {
        if (request.getApprove() == null) {
            throw new BusinessException(400, "审查结果不能为空");
        }
        GraduationThesis thesis = baseMapper.selectById(thesisId);
        if (thesis == null) {
            throw new BusinessException(404, "论文不存在");
        }
        ensureTeacherOfStudent(teacherUserId, thesis.getCampaignId(), thesis.getStudentId());
        if (thesis.getStatus() != ThesisStatusEnum.SUBMITTED) {
            throw new BusinessException(409, "该论文不在待形式审查状态");
        }
        if (!request.getApprove() && (request.getComment() == null || request.getComment().isBlank())) {
            throw new BusinessException(400, "退回必须填写审查意见");
        }
        // R-8.3：形式审查通过 → 待查重（APPROVED）；退回 → REVISION
        thesis.setStatus(request.getApprove() ? ThesisStatusEnum.APPROVED : ThesisStatusEnum.REVISION);
        thesis.setReviewTeacherId(teacherUserId);
        thesis.setReviewComment(request.getComment());
        thesis.setReviewTime(LocalDateTime.now());
        baseMapper.updateById(thesis);
        practiceNoticeScenes.thesisFormReviewed(thesis.getStudentId(), thesis.getTitle(),
                request.getApprove(), request.getComment());
        logService.record(thesis.getCampaignId(), teacherUserId, "teacher", "论文形式审查",
                "graduation_thesis", thesisId, "结果: " + (request.getApprove() ? "通过" : "退回"));
        return toResponse(thesis);
    }

    @Override
    @Transactional
    public DuplicateCheckResponse registerDuplicateCheck(Long academicUserId, DuplicateCheckRegisterRequest request,
                                                         MultipartFile file) {
        ParamValidator.requireNonNull(request.getThesisId(), "论文");
        ParamValidator.requireNonNull(request.getDuplicateRate(), "重复率");
        if (request.getDuplicateRate() < 0 || request.getDuplicateRate() > 100) {
            throw new BusinessException(400, "重复率需在0-100之间");
        }
        ParamValidator.requireNonNull(request.getCheckTime(), "检测时间");
        ParamValidator.requireNonNull(request.getResult(), "查重结论");
        GraduationThesis thesis = baseMapper.selectById(request.getThesisId());
        if (thesis == null) {
            throw new BusinessException(404, "论文不存在");
        }
        // R-8.5：仅最新版且待查重/查重不通过状态可登记（查重不通过后可再次检测）
        if (thesis.getIsLatest() != 1
                || (thesis.getStatus() != ThesisStatusEnum.APPROVED
                    && thesis.getStatus() != ThesisStatusEnum.DUPLICATE_FAILED)) {
            throw new BusinessException(409, "该论文不在待查重状态");
        }
        // R-8.6：每次检测一条记录，历史保留
        GraduationDuplicateCheck check = new GraduationDuplicateCheck();
        check.setThesisId(thesis.getId());
        check.setCampaignId(thesis.getCampaignId());
        check.setStudentId(thesis.getStudentId());
        check.setDuplicateRate(request.getDuplicateRate());
        check.setPlatform(request.getPlatform());
        check.setCheckTime(request.getCheckTime());
        check.setResult(request.getResult());
        check.setComment(request.getComment());
        check.setOperatorId(academicUserId);
        // 可选查重报告附件：multipart 整传 与 data.filePath 分片产物 二选一
        StoredFileRef fileRef = fileSupport.resolveSubmit(request.getFilePath(), request.getFileOriginal(),
                file, FileBizEnum.GRADUATION_DUPLICATE_REPORT, false);
        if (fileRef != null) {
            check.setFileName(fileRef.storedPath());
            check.setFileOriginal(fileRef.originalName());
        }
        duplicateCheckMapper.insert(check);
        // 门禁 R-3.3：查重通过进入答辩环节；不通过退回修改
        thesis.setStatus(request.getResult() == com.xrq.xxq.module.practice.graduation.entity.DuplicateResultEnum.PASS
                ? ThesisStatusEnum.DUPLICATE_PASSED : ThesisStatusEnum.DUPLICATE_FAILED);
        baseMapper.updateById(thesis);
        practiceNoticeScenes.thesisDuplicateChecked(thesis.getStudentId(), thesis.getTitle(),
                request.getResult() == com.xrq.xxq.module.practice.graduation.entity.DuplicateResultEnum.PASS,
                request.getDuplicateRate());
        logService.record(thesis.getCampaignId(), academicUserId, "academic_admin", "登记查重结果",
                "graduation_duplicate_check", check.getId(),
                "论文: " + thesis.getId() + ", 重复率: " + request.getDuplicateRate() + "%"
                        + ", 结论: " + request.getResult().getDescription());
        return toCheckResponse(check);
    }

    @Override
    public List<ThesisResponse> listMyThesis(Long studentUserId, Long campaignId) {
        List<GraduationThesis> list = baseMapper.selectList(new LambdaQueryWrapper<GraduationThesis>()
                .eq(campaignId != null, GraduationThesis::getCampaignId, campaignId)
                .eq(GraduationThesis::getStudentId, studentUserId)
                .orderByAsc(GraduationThesis::getVersion));
        return toResponses(list);
    }

    @Override
    public List<ThesisResponse> listTeacherThesis(Long teacherUserId, Long campaignId) {
        List<GraduationAssignment> assignments = assignmentMapper.selectList(
                new LambdaQueryWrapper<GraduationAssignment>()
                        .eq(GraduationAssignment::getCampaignId, campaignId)
                        .eq(GraduationAssignment::getTeacherId, teacherUserId));
        if (assignments.isEmpty()) {
            return List.of();
        }
        List<Long> studentIds = assignments.stream().map(GraduationAssignment::getStudentId).toList();
        List<GraduationThesis> list = baseMapper.selectList(new LambdaQueryWrapper<GraduationThesis>()
                .eq(GraduationThesis::getCampaignId, campaignId)
                .in(GraduationThesis::getStudentId, studentIds)
                .orderByDesc(GraduationThesis::getId));
        return toResponses(list);
    }

    @Override
    public List<ThesisResponse> listCampaignThesis(Long campaignId, ThesisStatusEnum status) {
        ParamValidator.requireNonNull(campaignId, "活动");
        List<GraduationThesis> list = baseMapper.selectList(new LambdaQueryWrapper<GraduationThesis>()
                .eq(GraduationThesis::getCampaignId, campaignId)
                .eq(status != null, GraduationThesis::getStatus, status)
                .orderByDesc(GraduationThesis::getSubmitTime));
        return toResponses(list);
    }

    @Override
    public FileView resolveThesisFile(String userType, Long userId, Long thesisId) {
        GraduationThesis thesis = baseMapper.selectById(thesisId);
        if (thesis == null) {
            throw new BusinessException(404, "论文不存在");
        }
        checkFileAccess(userType, userId, thesis.getCampaignId(), thesis.getStudentId());
        return new FileView(fileSupport.resolveForDownload(thesis.getFileName()), thesis.getFileOriginal());
    }

    @Override
    public ExportPackage exportPackage(Long academicUserId, Long campaignId, ThesisStatusEnum status) {
        GraduationCampaign campaign = requireCampaign(campaignId);
        List<GraduationThesis> theses = baseMapper.selectList(new LambdaQueryWrapper<GraduationThesis>()
                .eq(GraduationThesis::getCampaignId, campaignId)
                .eq(GraduationThesis::getIsLatest, 1)
                .eq(status != null, GraduationThesis::getStatus, status)
                .orderByAsc(GraduationThesis::getStudentId));
        // 富化：学号/姓名/院系
        Map<Long, String> studentNoMap = studentNoMap(theses);
        Map<Long, String> nameMap = userMapper.toNameMap(
                theses.stream().map(GraduationThesis::getStudentId).distinct().toList());
        Map<Long, String> collegeMap = collegeNameMap(theses);
        byte[] xlsx = buildThesisXlsx(theses, studentNoMap, nameMap, collegeMap);
        // zip 打包：名单 + 论文文件（R-8.4 默认假设 xlsx 名单 + zip 文件包），流式落盘 exports/
        Path zip = buildZip(theses, studentNoMap, nameMap, xlsx);
        logService.record(campaignId, academicUserId, "academic_admin", "导出查重数据包",
                "graduation_campaign", campaignId,
                "论文条数: " + theses.size() + (status != null ? ", 状态筛选: " + status.getCode() : ""));
        return new ExportPackage(zip,
                "查重数据包-" + safeName(campaign.getName())
                        + "-" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss")) + ".zip");
    }

    @Override
    public List<DuplicateCheckResponse> listDuplicateChecks(Long thesisId) {
        return batchDuplicateChecks(List.of(thesisId)).getOrDefault(thesisId, List.of());
    }

    @Override
    public FileView resolveDuplicateCheckFile(String userType, Long userId, Long checkId) {
        GraduationDuplicateCheck check = duplicateCheckMapper.selectById(checkId);
        if (check == null) {
            throw new BusinessException(404, "查重记录不存在");
        }
        // 与论文文件同一判权链：学生本人/指导教师/院系/教务
        checkFileAccess(userType, userId, check.getCampaignId(), check.getStudentId());
        if (check.getFileName() == null || check.getFileName().isBlank()) {
            throw new BusinessException(404, "该次检测未上传查重报告");
        }
        return new FileView(fileSupport.resolveForDownload(check.getFileName()), check.getFileOriginal());
    }

    // ---- helpers ----

    private GraduationThesis findLatest(Long campaignId, Long studentId) {
        return baseMapper.selectOne(new LambdaQueryWrapper<GraduationThesis>()
                .eq(GraduationThesis::getCampaignId, campaignId)
                .eq(GraduationThesis::getStudentId, studentId)
                .eq(GraduationThesis::getIsLatest, 1)
                .last("LIMIT 1"));
    }

    private GraduationCampaign requireCampaign(Long id) {
        GraduationCampaign campaign = campaignMapper.selectById(id);
        if (campaign == null) {
            throw new BusinessException(404, "活动不存在");
        }
        return campaign;
    }

    /** 教师只能操作自己名下学生（R-10.1） */
    private void ensureTeacherOfStudent(Long teacherUserId, Long campaignId, Long studentId) {
        GraduationAssignment assignment = assignmentMapper.selectOne(new LambdaQueryWrapper<GraduationAssignment>()
                .eq(GraduationAssignment::getCampaignId, campaignId)
                .eq(GraduationAssignment::getStudentId, studentId)
                .last("LIMIT 1"));
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
            ensureTeacherOfStudent(userId, campaignId, studentId);
            return;
        }
        if ("department".equals(userType)) {
            if (scopeResolver.isOutsideDept(userId, studentId)) {
                throw new BusinessException(403, "权限不足");
            }
            return;
        }
        // academic_admin：全量可见
    }

    private List<ThesisResponse> toResponses(List<GraduationThesis> theses) {
        if (theses.isEmpty()) {
            return List.of();
        }
        // 批量解析姓名（学生 + 审核教师一次查询，替代逐篇 nameOf）
        List<Long> personIds = new ArrayList<>();
        theses.forEach(t -> {
            personIds.add(t.getStudentId());
            if (t.getReviewTeacherId() != null) {
                personIds.add(t.getReviewTeacherId());
            }
        });
        personIds.removeIf(Objects::isNull);
        Map<Long, String> nameMap = userMapper.toNameMap(personIds);
        // 批量加载查重记录并按论文分组（替代逐篇查询）
        Map<Long, List<DuplicateCheckResponse>> checksByThesis = batchDuplicateChecks(
                theses.stream().map(GraduationThesis::getId).toList());
        return theses.stream()
                .map(t -> toResponse(t, nameMap.get(t.getStudentId()), nameMap,
                        checksByThesis.getOrDefault(t.getId(), List.of())))
                .toList();
    }

    private ThesisResponse toResponse(GraduationThesis thesis) {
        return toResponses(List.of(thesis)).getFirst();
    }

    private ThesisResponse toResponse(GraduationThesis thesis, String studentName,
                                      Map<Long, String> nameMap, List<DuplicateCheckResponse> checks) {
        ThesisResponse resp = new ThesisResponse();
        resp.setId(thesis.getId());
        resp.setCampaignId(thesis.getCampaignId());
        resp.setAssignmentId(thesis.getAssignmentId());
        resp.setStudentId(thesis.getStudentId());
        resp.setStudentName(studentName);
        resp.setTitle(thesis.getTitle());
        resp.setFileName(thesis.getFileName());
        resp.setFileOriginal(thesis.getFileOriginal());
        resp.setVersion(thesis.getVersion());
        resp.setIsLatest(thesis.getIsLatest());
        resp.setStatus(thesis.getStatus());
        resp.setSubmitTime(thesis.getSubmitTime());
        resp.setReviewTeacherId(thesis.getReviewTeacherId());
        resp.setReviewTeacherName(thesis.getReviewTeacherId() != null
                ? nameMap.get(thesis.getReviewTeacherId()) : null);
        resp.setReviewComment(thesis.getReviewComment());
        resp.setReviewTime(thesis.getReviewTime());
        resp.setDuplicateChecks(checks);
        return resp;
    }

    /** 批量加载查重记录并按论文分组（操作人姓名一次批查）。 */
    private Map<Long, List<DuplicateCheckResponse>> batchDuplicateChecks(List<Long> thesisIds) {
        List<GraduationDuplicateCheck> checks = duplicateCheckMapper.selectList(
                new LambdaQueryWrapper<GraduationDuplicateCheck>()
                        .in(GraduationDuplicateCheck::getThesisId, thesisIds)
                        .orderByAsc(GraduationDuplicateCheck::getCheckTime));
        if (checks.isEmpty()) {
            return new HashMap<>(); // 不用 Map.of():get(null) 会 NPE
        }
        Map<Long, String> operatorNames = userMapper.toNameMap(
                checks.stream().map(GraduationDuplicateCheck::getOperatorId)
                        .filter(Objects::nonNull).distinct().toList());
        Map<Long, List<DuplicateCheckResponse>> map = new HashMap<>();
        for (GraduationDuplicateCheck c : checks) {
            map.computeIfAbsent(c.getThesisId(), k -> new ArrayList<>())
                    .add(toCheckResponse(c, operatorNames));
        }
        return map;
    }

    private DuplicateCheckResponse toCheckResponse(GraduationDuplicateCheck c, Map<Long, String> operatorNames) {
        DuplicateCheckResponse resp = new DuplicateCheckResponse();
        resp.setId(c.getId());
        resp.setThesisId(c.getThesisId());
        resp.setDuplicateRate(c.getDuplicateRate());
        resp.setPlatform(c.getPlatform());
        resp.setCheckTime(c.getCheckTime());
        resp.setResult(c.getResult());
        resp.setComment(c.getComment());
        resp.setFileName(c.getFileName());
        resp.setFileOriginal(c.getFileOriginal());
        resp.setOperatorId(c.getOperatorId());
        resp.setOperatorName(c.getOperatorId() == null ? null : operatorNames.get(c.getOperatorId()));
        resp.setCreateTime(c.getCreateTime());
        return resp;
    }

    private DuplicateCheckResponse toCheckResponse(GraduationDuplicateCheck check) {
        Map<Long, String> operatorNames = check.getOperatorId() == null
                ? Map.of()
                : userMapper.toNameMap(List.of(check.getOperatorId()));
        return toCheckResponse(check, operatorNames);
    }

    // ---- 查重数据包导出（R-8.4）----

    private Map<Long, String> studentNoMap(List<GraduationThesis> theses) {
        List<Long> studentIds = theses.stream().map(GraduationThesis::getStudentId).distinct().toList();
        return studentIds.isEmpty() ? new HashMap<>() : studentMapper.toStudentNoMap(studentIds);
    }

    /** 学生 -> 院系名（student -> class_name -> major -> college 链；归属缺失记空串） */
    private Map<Long, String> collegeNameMap(List<GraduationThesis> theses) {
        List<Long> studentIds = theses.stream().map(GraduationThesis::getStudentId).distinct().toList();
        if (studentIds.isEmpty()) {
            return new HashMap<>(); // 不用 Map.of():get(null) 会 NPE
        }
        Map<Long, Long> studentClass = studentMapper.selectByIds(studentIds).stream()
                .collect(java.util.stream.Collectors.toMap(Student::getUserId,
                        s -> s.getClassId() != null ? s.getClassId() : -1L, (a, b) -> a));
        Map<Long, Long> classCollege = classNameService.toCollegeIdMap(
                studentClass.values().stream().filter(id -> id > 0).distinct().toList());
        Map<Long, String> collegeNames = classCollege.isEmpty() ? Map.of()
                : collegeMapper.toNameMap(classCollege.values().stream().distinct().toList());
        Map<Long, String> result = new java.util.HashMap<>();
        for (Long studentId : studentIds) {
            Long collegeId = classCollege.get(studentClass.getOrDefault(studentId, -1L));
            result.put(studentId, collegeId == null ? "" : collegeNames.getOrDefault(collegeId, ""));
        }
        return result;
    }

    private byte[] buildThesisXlsx(List<GraduationThesis> theses, Map<Long, String> noMap,
                                   Map<Long, String> nameMap, Map<Long, String> collegeMap) {
        String[] headers = {"学号", "姓名", "院系", "论文题目", "版本", "状态", "提交时间"};
        try (XSSFWorkbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("查重名单");
            CellStyle headerStyle = workbook.createCellStyle();
            Font headerFont = workbook.createFont();
            headerFont.setBold(true);
            headerStyle.setFont(headerFont);
            Row header = sheet.createRow(0);
            for (Integer i = 0; i < headers.length; i++) {
                Cell cell = header.createCell(i);
                cell.setCellValue(headers[i]);
                cell.setCellStyle(headerStyle);
            }
            Integer r = 1;
            for (GraduationThesis t : theses) {
                Row dataRow = sheet.createRow(r++);
                dataRow.createCell(0).setCellValue(nvl(noMap.get(t.getStudentId())));
                dataRow.createCell(1).setCellValue(nvl(nameMap.get(t.getStudentId())));
                dataRow.createCell(2).setCellValue(nvl(collegeMap.get(t.getStudentId())));
                dataRow.createCell(3).setCellValue(nvl(t.getTitle()));
                dataRow.createCell(4).setCellValue(t.getVersion());
                dataRow.createCell(5).setCellValue(t.getStatus() != null ? t.getStatus().getDescription() : "");
                dataRow.createCell(6).setCellValue(t.getSubmitTime() != null
                        ? t.getSubmitTime().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")) : "");
            }
            for (Integer i = 0; i < headers.length; i++) {
                sheet.autoSizeColumn(i);
            }
            workbook.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new BusinessException(500, "查重名单导出失败");
        }
    }

    /**
     * 流式打包到 exports/ 暂存：内存占用与论文总量解耦
     * （旧实现整个 zip 在 ByteArrayOutputStream 里，文件量一大即 OOM）。
     */
    private Path buildZip(List<GraduationThesis> theses, Map<Long, String> noMap,
                          Map<Long, String> nameMap, byte[] xlsx) {
        Path tmp;
        try {
            Path exportsDir = Path.of(fileStoragePath, FileStorageService.EXPORTS_DIR);
            Files.createDirectories(exportsDir);
            tmp = Files.createTempFile(exportsDir, "export-", ".zip");
        } catch (IOException e) {
            throw new BusinessException(500, "查重数据包打包失败");
        }
        try (OutputStream out = Files.newOutputStream(tmp);
             ZipOutputStream zip = new ZipOutputStream(out, java.nio.charset.StandardCharsets.UTF_8)) {
            zip.putNextEntry(new ZipEntry("查重名单.xlsx"));
            zip.write(xlsx);
            zip.closeEntry();
            for (GraduationThesis t : theses) {
                Path path = fileSupport.resolveForDownload(t.getFileName());
                String entryName = safeName(noMap.getOrDefault(t.getStudentId(), "")
                        + "-" + nameMap.getOrDefault(t.getStudentId(), "")
                        + "-" + t.getTitle()
                        + "-v" + t.getVersion() + extOf(t.getFileOriginal()));
                zip.putNextEntry(new ZipEntry(entryName));
                try (InputStream in = Files.newInputStream(path)) {
                    in.transferTo(zip);
                }
                zip.closeEntry();
            }
            return tmp;
        } catch (IOException e) {
            try {
                Files.deleteIfExists(tmp);
            } catch (IOException ignored) {
                // 删除失败由 FileMaintenanceTask 按龄期清扫兜底
            }
            throw new BusinessException(500, "查重数据包打包失败");
        }
    }

    private String extOf(String original) {
        if (original == null) {
            return "";
        }
        Integer dot = original.lastIndexOf('.');
        return dot >= 0 ? original.substring(dot) : "";
    }

    private String safeName(String name) {
        if (name == null || name.isBlank()) {
            return "unnamed";
        }
        return name.replaceAll("[\\\\/:*?\"<>|]", "_");
    }

    private String nvl(String value) {
        return value == null ? "" : value;
    }
}
