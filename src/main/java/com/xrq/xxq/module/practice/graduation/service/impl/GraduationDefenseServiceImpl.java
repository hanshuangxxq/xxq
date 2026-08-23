package com.xrq.xxq.module.practice.graduation.service.impl;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.xrq.xxq.common.BusinessException;
import com.xrq.xxq.common.event.PracticeNoticeEvent;
import com.xrq.xxq.module.practice.graduation.dto.DefenseArrangeRequest;
import com.xrq.xxq.module.practice.graduation.dto.DefenseResponse;
import com.xrq.xxq.module.practice.graduation.dto.ScoreConfirmRequest;
import com.xrq.xxq.module.practice.graduation.dto.ScoreResponse;
import com.xrq.xxq.module.practice.graduation.dto.ScoreSubmitRequest;
import com.xrq.xxq.module.practice.graduation.entity.GraduationAssignment;
import com.xrq.xxq.module.practice.graduation.entity.GraduationCampaign;
import com.xrq.xxq.module.practice.graduation.entity.GraduationDefense;
import com.xrq.xxq.module.practice.graduation.entity.GraduationScore;
import com.xrq.xxq.module.practice.graduation.entity.GraduationScoreStatusEnum;
import com.xrq.xxq.module.practice.graduation.entity.GraduationThesis;
import com.xrq.xxq.module.practice.graduation.entity.ThesisStatusEnum;
import com.xrq.xxq.module.practice.graduation.mapper.GraduationAssignmentMapper;
import com.xrq.xxq.module.practice.graduation.mapper.GraduationCampaignMapper;
import com.xrq.xxq.module.practice.graduation.mapper.GraduationDefenseMapper;
import com.xrq.xxq.module.practice.graduation.mapper.GraduationScoreMapper;
import com.xrq.xxq.module.practice.graduation.mapper.GraduationThesisMapper;
import com.xrq.xxq.module.practice.graduation.service.GraduationDefenseService;
import com.xrq.xxq.module.practice.graduation.service.GraduationLogService;
import com.xrq.xxq.module.clazz.mapper.ClassNameMapper;
import com.xrq.xxq.module.college.mapper.CollegeMapper;
import com.xrq.xxq.module.user.mapper.StudentMapper;
import com.xrq.xxq.module.user.mapper.UserMapper;
import com.xrq.xxq.util.ParamValidator;
import com.xrq.xxq.util.StudentScopeResolver;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class GraduationDefenseServiceImpl
        extends ServiceImpl<GraduationDefenseMapper, GraduationDefense>
        implements GraduationDefenseService {

    private final GraduationCampaignMapper campaignMapper;
    private final GraduationAssignmentMapper assignmentMapper;
    private final GraduationThesisMapper thesisMapper;
    private final GraduationScoreMapper scoreMapper;
    private final StudentMapper studentMapper;
    private final UserMapper userMapper;
    private final ClassNameMapper classNameMapper;
    private final CollegeMapper collegeMapper;
    private final StudentScopeResolver scopeResolver;
    private final GraduationLogService logService;
    private final ApplicationEventPublisher eventPublisher;

    @Override
    @Transactional
    public DefenseResponse arrangeDefense(Long deptUserId, DefenseArrangeRequest request) {
        ParamValidator.requireNonNull(request.getCampaignId(), "活动");
        ParamValidator.requireNonNull(request.getStudentId(), "学生");
        GraduationCampaign campaign = requireCampaign(request.getCampaignId());
        // R-10.1：院系管理者仅限本院系学生
        if (scopeResolver.departmentOwnsStudent(deptUserId, request.getStudentId())) {
            throw new BusinessException(403, "权限不足");
        }
        // R-3.3：查重通过（DUPLICATE_PASSED）才能进入答辩环节
        GraduationThesis latest = thesisMapper.selectOne(new LambdaQueryWrapper<GraduationThesis>()
                .eq(GraduationThesis::getCampaignId, campaign.getId())
                .eq(GraduationThesis::getStudentId, request.getStudentId())
                .eq(GraduationThesis::getIsLatest, 1)
                .last("LIMIT 1"));
        if (latest == null || latest.getStatus() != ThesisStatusEnum.DUPLICATE_PASSED) {
            throw new BusinessException(409, "论文查重通过后才能安排答辩");
        }
        // upsert：同一学生同一活动仅一条答辩安排
        GraduationDefense defense = baseMapper.selectOne(new LambdaQueryWrapper<GraduationDefense>()
                .eq(GraduationDefense::getCampaignId, campaign.getId())
                .eq(GraduationDefense::getStudentId, request.getStudentId())
                .last("LIMIT 1"));
        Boolean isNew = defense == null;
        if (isNew) {
            defense = new GraduationDefense();
            defense.setCampaignId(campaign.getId());
            defense.setStudentId(request.getStudentId());
        }
        defense.setGroupName(request.getGroupName());
        defense.setDefenseTime(request.getDefenseTime());
        defense.setLocation(request.getLocation());
        defense.setReviewerId(request.getReviewerId());
        defense.setDefenseTeacherIds(request.getDefenseTeacherIds() == null || request.getDefenseTeacherIds().isEmpty()
                ? null
                : request.getDefenseTeacherIds().stream().map(String::valueOf).collect(Collectors.joining(",")));
        if (isNew) {
            baseMapper.insert(defense);
        } else {
            baseMapper.updateById(defense);
        }
        logService.record(campaign.getId(), deptUserId, "department", "安排答辩",
                "graduation_defense", defense.getId(), "学生: " + request.getStudentId());
        return toDefenseResponse(defense);
    }

    @Override
    public List<DefenseResponse> listDefenses(Long campaignId, String userType, Long userId) {
        ParamValidator.requireNonNull(campaignId, "活动");
        List<GraduationDefense> list = baseMapper.selectList(new LambdaQueryWrapper<GraduationDefense>()
                .eq(GraduationDefense::getCampaignId, campaignId)
                .orderByDesc(GraduationDefense::getId));
        if ("student".equals(userType)) {
            list = list.stream().filter(d -> d.getStudentId().equals(userId)).toList();
        } else if ("department".equals(userType)) {
            // R-10.1 院系可见性：批量解析学生院系后内存过滤（替代逐条 departmentOwnsStudent 查库）
            Long deptCollegeId = scopeResolver.deptCollegeId(userId);
            Map<Long, Long> collegeByStudent = scopeResolver.studentCollegeIdMap(
                    list.stream().map(GraduationDefense::getStudentId).toList());
            list = list.stream()
                    .filter(d -> deptCollegeId != null
                            && Objects.equals(deptCollegeId, collegeByStudent.get(d.getStudentId())))
                    .toList();
        }
        return toDefenseResponses(list);
    }

    @Override
    @Transactional
    public ScoreResponse submitAdvisorScore(Long teacherUserId, ScoreSubmitRequest request) {
        // 指导教师仅评自己名下学生
        ensureTeacherOfStudent(teacherUserId, request);
        return submitScorePart(request, "ADVISOR", teacherUserId, null);
    }

    @Override
    @Transactional
    public ScoreResponse submitReviewerScore(Long reviewerUserId, ScoreSubmitRequest request) {
        // 评阅教师 = 答辩安排中的 reviewer_id
        GraduationDefense defense = requireDefense(request.getCampaignId(), request.getStudentId());
        if (defense.getReviewerId() == null || !defense.getReviewerId().equals(reviewerUserId)) {
            throw new BusinessException(403, "你不是该学生的评阅教师");
        }
        return submitScorePart(request, "REVIEWER", reviewerUserId, null);
    }

    @Override
    @Transactional
    public ScoreResponse submitDefenseScore(Long userId, String userType, ScoreSubmitRequest request) {
        if ("department".equals(userType)
                && scopeResolver.departmentOwnsStudent(userId, request.getStudentId())) {
            throw new BusinessException(403, "权限不足");
        }
        return submitScorePart(request, "DEFENSE", userId, userType);
    }

    @Override
    @Transactional
    public ScoreResponse confirmScore(Long deptUserId, ScoreConfirmRequest request) {
        ParamValidator.requireNonNull(request.getCampaignId(), "活动");
        ParamValidator.requireNonNull(request.getStudentId(), "学生");
        if (scopeResolver.departmentOwnsStudent(deptUserId, request.getStudentId())) {
            throw new BusinessException(403, "权限不足");
        }
        GraduationScore score = requireScore(request.getCampaignId(), request.getStudentId());
        if (score.getStatus() != GraduationScoreStatusEnum.COMPLETE) {
            throw new BusinessException(409, "总评成绩未合成完毕，不可发布");
        }
        // R-9.3：院系确认后发布给学生（终态，不可再改）
        score.setStatus(GraduationScoreStatusEnum.PUBLISHED);
        score.setConfirmBy(deptUserId);
        score.setConfirmTime(LocalDateTime.now());
        score.setPublishTime(LocalDateTime.now());
        scoreMapper.updateById(score);
        GraduationCampaign campaign = campaignMapper.selectById(request.getCampaignId());
        eventPublisher.publishEvent(new PracticeNoticeEvent(request.getStudentId(),
                "毕设成绩发布",
                "你的毕业设计总评成绩已发布：" + score.getTotalScore()
                        + "分" + (campaign != null ? "（" + campaign.getName() + "）" : "") + "。"));
        logService.record(request.getCampaignId(), deptUserId, "department", "发布成绩",
                "graduation_score", score.getId(),
                "学生: " + request.getStudentId() + ", 总评: " + score.getTotalScore());
        return toScoreResponse(score);
    }

    @Override
    public List<ScoreResponse> listScores(Long campaignId, String userType, Long userId) {
        ParamValidator.requireNonNull(campaignId, "活动");
        List<GraduationScore> list = scoreMapper.selectList(new LambdaQueryWrapper<GraduationScore>()
                .eq(GraduationScore::getCampaignId, campaignId)
                .orderByAsc(GraduationScore::getStudentId));
        if ("student".equals(userType)) {
            list = list.stream().filter(s -> s.getStudentId().equals(userId)).toList();
        } else if ("teacher".equals(userType)) {
            List<Long> studentIds = assignmentMapper.selectList(new LambdaQueryWrapper<GraduationAssignment>()
                            .eq(GraduationAssignment::getCampaignId, campaignId)
                            .eq(GraduationAssignment::getTeacherId, userId))
                    .stream().map(GraduationAssignment::getStudentId).toList();
            list = list.stream().filter(s -> studentIds.contains(s.getStudentId())).toList();
        } else if ("department".equals(userType)) {
            // R-10.1 院系可见性：批量解析学生院系后内存过滤（替代逐条 departmentOwnsStudent 查库）
            Long deptCollegeId = scopeResolver.deptCollegeId(userId);
            Map<Long, Long> collegeByStudent = scopeResolver.studentCollegeIdMap(
                    list.stream().map(GraduationScore::getStudentId).toList());
            list = list.stream()
                    .filter(s -> deptCollegeId != null
                            && Objects.equals(deptCollegeId, collegeByStudent.get(s.getStudentId())))
                    .toList();
        }
        return toScoreResponses(list);
    }

    @Override
    public ExportFile exportScores(Long academicUserId, Long campaignId) {
        GraduationCampaign campaign = requireCampaign(campaignId);
        List<GraduationScore> scores = scoreMapper.selectList(new LambdaQueryWrapper<GraduationScore>()
                .eq(GraduationScore::getCampaignId, campaignId)
                .orderByAsc(GraduationScore::getStudentId));
        byte[] data = buildScoreXlsx(scores, campaign);
        logService.record(campaignId, academicUserId, "academic_admin", "导出成绩总表",
                "graduation_campaign", campaignId, "成绩条数: " + scores.size());
        return new ExportFile(data,
                "毕业设计成绩总表-" + safeName(campaign.getName())
                        + "-" + LocalDateTime.now().format(java.time.format.DateTimeFormatter
                        .ofPattern("yyyyMMddHHmmss")) + ".xlsx");
    }

    @Override
    public ScoreResponse getMyScore(Long studentUserId, Long campaignId) {
        GraduationScore score = scoreMapper.selectOne(new LambdaQueryWrapper<GraduationScore>()
                .eq(GraduationScore::getCampaignId, campaignId)
                .eq(GraduationScore::getStudentId, studentUserId)
                .last("LIMIT 1"));
        return score == null ? null : toScoreResponse(score);
    }

    // ---- helpers ----

    /** 录入分项成绩（part: ADVISOR/REVIEWER/DEFENSE），录入后尝试合成总评 */
    private ScoreResponse submitScorePart(ScoreSubmitRequest request, String part,
                                          Long operatorId, String operatorType) {
        ParamValidator.requireNonNull(request.getCampaignId(), "活动");
        ParamValidator.requireNonNull(request.getStudentId(), "学生");
        ParamValidator.requireNonNull(request.getScore(), "得分");
        if (request.getScore() < 0 || request.getScore() > 100) {
            throw new BusinessException(400, "得分需在0-100之间");
        }
        GraduationCampaign campaign = requireCampaign(request.getCampaignId());
        // 门禁 R-3.3：查重通过才可进入成绩环节
        GraduationThesis latest = thesisMapper.selectOne(new LambdaQueryWrapper<GraduationThesis>()
                .eq(GraduationThesis::getCampaignId, campaign.getId())
                .eq(GraduationThesis::getStudentId, request.getStudentId())
                .eq(GraduationThesis::getIsLatest, 1)
                .last("LIMIT 1"));
        if (latest == null || latest.getStatus() != ThesisStatusEnum.DUPLICATE_PASSED) {
            throw new BusinessException(409, "论文查重通过后才能录入成绩");
        }
        GraduationScore score = scoreMapper.selectOne(new LambdaQueryWrapper<GraduationScore>()
                .eq(GraduationScore::getCampaignId, campaign.getId())
                .eq(GraduationScore::getStudentId, request.getStudentId())
                .last("LIMIT 1"));
        Boolean isNew = score == null;
        if (!isNew && score.getStatus() == GraduationScoreStatusEnum.PUBLISHED) {
            throw new BusinessException(409, "成绩已发布，不可修改");
        }
        if (isNew) {
            score = new GraduationScore();
            score.setCampaignId(campaign.getId());
            score.setStudentId(request.getStudentId());
            score.setStatus(GraduationScoreStatusEnum.INCOMPLETE);
        }
        LocalDateTime now = LocalDateTime.now();
        switch (part) {
            case "ADVISOR" -> {
                score.setAdvisorScore(request.getScore());
                score.setAdvisorBy(operatorId);
                score.setAdvisorTime(now);
            }
            case "REVIEWER" -> {
                score.setReviewerScore(request.getScore());
                score.setReviewerBy(operatorId);
                score.setReviewerTime(now);
            }
            default -> {
                score.setDefenseScore(request.getScore());
                score.setDefenseBy(operatorId);
                score.setDefenseTime(now);
            }
        }
        // R-9.2：三项齐全后按活动权重自动合成总评
        if (score.getAdvisorScore() != null && score.getReviewerScore() != null && score.getDefenseScore() != null) {
            BigDecimal total = BigDecimal.valueOf(
                    score.getAdvisorScore() * campaign.getAdvisorWeight()
                            + score.getReviewerScore() * campaign.getReviewerWeight()
                            + score.getDefenseScore() * campaign.getDefenseWeight())
                    .divide(BigDecimal.valueOf(100), 1, RoundingMode.HALF_UP);
            score.setTotalScore(total);
            if (score.getStatus() != GraduationScoreStatusEnum.PUBLISHED) {
                score.setStatus(GraduationScoreStatusEnum.COMPLETE);
            }
        }
        if (isNew) {
            scoreMapper.insert(score);
        } else {
            scoreMapper.updateById(score);
        }
        return toScoreResponse(score);
    }

    private void ensureTeacherOfStudent(Long teacherUserId, ScoreSubmitRequest request) {
        GraduationAssignment assignment = assignmentMapper.selectOne(new LambdaQueryWrapper<GraduationAssignment>()
                .eq(GraduationAssignment::getCampaignId, request.getCampaignId())
                .eq(GraduationAssignment::getStudentId, request.getStudentId())
                .last("LIMIT 1"));
        if (assignment == null || !assignment.getTeacherId().equals(teacherUserId)) {
            throw new BusinessException(403, "权限不足");
        }
    }

    private GraduationCampaign requireCampaign(Long id) {
        GraduationCampaign campaign = campaignMapper.selectById(id);
        if (campaign == null) {
            throw new BusinessException(404, "活动不存在");
        }
        return campaign;
    }

    private GraduationDefense requireDefense(Long campaignId, Long studentId) {
        GraduationDefense defense = baseMapper.selectOne(new LambdaQueryWrapper<GraduationDefense>()
                .eq(GraduationDefense::getCampaignId, campaignId)
                .eq(GraduationDefense::getStudentId, studentId)
                .last("LIMIT 1"));
        if (defense == null) {
            throw new BusinessException(404, "该学生尚未安排答辩");
        }
        return defense;
    }

    private GraduationScore requireScore(Long campaignId, Long studentId) {
        GraduationScore score = scoreMapper.selectOne(new LambdaQueryWrapper<GraduationScore>()
                .eq(GraduationScore::getCampaignId, campaignId)
                .eq(GraduationScore::getStudentId, studentId)
                .last("LIMIT 1"));
        if (score == null) {
            throw new BusinessException(404, "该学生尚无成绩记录");
        }
        return score;
    }

    private List<Long> defenseTeacherIdList(GraduationDefense defense) {
        if (defense.getDefenseTeacherIds() == null || defense.getDefenseTeacherIds().isBlank()) {
            return List.of();
        }
        return Arrays.stream(defense.getDefenseTeacherIds().split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .map(Long::valueOf)
                .toList();
    }

    private List<DefenseResponse> toDefenseResponses(List<GraduationDefense> defenses) {
        if (defenses.isEmpty()) {
            return List.of();
        }
        List<Long> personIds = new ArrayList<>();
        defenses.forEach(d -> {
            personIds.add(d.getStudentId());
            if (d.getReviewerId() != null) {
                personIds.add(d.getReviewerId());
            }
            personIds.addAll(defenseTeacherIdList(d));
        });
        personIds.removeIf(Objects::isNull);
        Map<Long, String> nameMap = userMapper.toNameMap(personIds);
        Map<Long, String> noMap = studentMapper.toStudentNoMap(
                defenses.stream().map(GraduationDefense::getStudentId).distinct().toList());
        return defenses.stream().map(d -> {
            DefenseResponse resp = new DefenseResponse();
            resp.setId(d.getId());
            resp.setCampaignId(d.getCampaignId());
            resp.setStudentId(d.getStudentId());
            resp.setStudentName(nameMap.get(d.getStudentId()));
            resp.setStudentNo(noMap.get(d.getStudentId()));
            resp.setGroupName(d.getGroupName());
            resp.setDefenseTime(d.getDefenseTime());
            resp.setLocation(d.getLocation());
            resp.setReviewerId(d.getReviewerId());
            resp.setReviewerName(d.getReviewerId() != null ? nameMap.get(d.getReviewerId()) : null);
            List<Long> teacherIds = defenseTeacherIdList(d);
            resp.setDefenseTeacherIds(teacherIds);
            resp.setDefenseTeacherNames(teacherIds.stream().map(nameMap::get).toList());
            return resp;
        }).toList();
    }

    private DefenseResponse toDefenseResponse(GraduationDefense defense) {
        return toDefenseResponses(List.of(defense)).get(0);
    }

    private List<ScoreResponse> toScoreResponses(List<GraduationScore> scores) {
        if (scores.isEmpty()) {
            return List.of();
        }
        List<Long> personIds = new ArrayList<>();
        scores.forEach(s -> {
            personIds.add(s.getStudentId());
            if (s.getAdvisorBy() != null) {
                personIds.add(s.getAdvisorBy());
            }
            if (s.getReviewerBy() != null) {
                personIds.add(s.getReviewerBy());
            }
            if (s.getDefenseBy() != null) {
                personIds.add(s.getDefenseBy());
            }
            if (s.getConfirmBy() != null) {
                personIds.add(s.getConfirmBy());
            }
        });
        personIds.removeIf(Objects::isNull);
        Map<Long, String> nameMap = userMapper.toNameMap(personIds);
        return scores.stream().map(s -> toScoreResponse(s, nameMap)).toList();
    }

    private ScoreResponse toScoreResponse(GraduationScore score) {
        List<Long> personIds = new ArrayList<>();
        personIds.add(score.getStudentId());
        if (score.getAdvisorBy() != null) {
            personIds.add(score.getAdvisorBy());
        }
        if (score.getReviewerBy() != null) {
            personIds.add(score.getReviewerBy());
        }
        if (score.getDefenseBy() != null) {
            personIds.add(score.getDefenseBy());
        }
        if (score.getConfirmBy() != null) {
            personIds.add(score.getConfirmBy());
        }
        return toScoreResponse(score, userMapper.toNameMap(personIds));
    }

    private ScoreResponse toScoreResponse(GraduationScore score, Map<Long, String> nameMap) {
        ScoreResponse resp = new ScoreResponse();
        resp.setId(score.getId());
        resp.setCampaignId(score.getCampaignId());
        resp.setStudentId(score.getStudentId());
        resp.setStudentName(nameMap.get(score.getStudentId()));
        resp.setAdvisorScore(score.getAdvisorScore());
        resp.setAdvisorBy(score.getAdvisorBy());
        resp.setAdvisorName(score.getAdvisorBy() != null ? nameMap.get(score.getAdvisorBy()) : null);
        resp.setAdvisorTime(score.getAdvisorTime());
        resp.setReviewerScore(score.getReviewerScore());
        resp.setReviewerBy(score.getReviewerBy());
        resp.setReviewerName(score.getReviewerBy() != null ? nameMap.get(score.getReviewerBy()) : null);
        resp.setReviewerTime(score.getReviewerTime());
        resp.setDefenseScore(score.getDefenseScore());
        resp.setDefenseBy(score.getDefenseBy());
        resp.setDefenseName(score.getDefenseBy() != null ? nameMap.get(score.getDefenseBy()) : null);
        resp.setDefenseTime(score.getDefenseTime());
        resp.setTotalScore(score.getTotalScore());
        resp.setStatus(score.getStatus());
        resp.setConfirmBy(score.getConfirmBy());
        resp.setConfirmName(score.getConfirmBy() != null ? nameMap.get(score.getConfirmBy()) : null);
        resp.setConfirmTime(score.getConfirmTime());
        resp.setPublishTime(score.getPublishTime());
        return resp;
    }

    // ---- 成绩总表导出（R-9.4）----

    private byte[] buildScoreXlsx(List<GraduationScore> scores, GraduationCampaign campaign) {
        String[] headers = {"学号", "姓名", "院系", "指导教师评分", "评阅教师评分", "答辩评分", "总评成绩", "状态", "发布时间"};
        Map<Long, String> nameMap = userMapper.toNameMap(
                scores.stream().map(GraduationScore::getStudentId).distinct().toList());
        Map<Long, String> noMap = studentMapper.toStudentNoMap(
                scores.stream().map(GraduationScore::getStudentId).distinct().toList());
        Map<Long, String> collegeMap = collegeNameMap(scores);
        try (org.apache.poi.xssf.usermodel.XSSFWorkbook workbook =
                     new org.apache.poi.xssf.usermodel.XSSFWorkbook();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            org.apache.poi.ss.usermodel.Sheet sheet = workbook.createSheet("成绩总表");
            org.apache.poi.ss.usermodel.CellStyle headerStyle = workbook.createCellStyle();
            org.apache.poi.ss.usermodel.Font headerFont = workbook.createFont();
            headerFont.setBold(true);
            headerStyle.setFont(headerFont);
            org.apache.poi.ss.usermodel.Row header = sheet.createRow(0);
            for (Integer i = 0; i < headers.length; i++) {
                org.apache.poi.ss.usermodel.Cell cell = header.createCell(i);
                cell.setCellValue(headers[i]);
                cell.setCellStyle(headerStyle);
            }
            Integer r = 1;
            for (GraduationScore s : scores) {
                org.apache.poi.ss.usermodel.Row dataRow = sheet.createRow(r++);
                dataRow.createCell(0).setCellValue(nvl(noMap.get(s.getStudentId())));
                dataRow.createCell(1).setCellValue(nvl(nameMap.get(s.getStudentId())));
                dataRow.createCell(2).setCellValue(nvl(collegeMap.get(s.getStudentId())));
                dataRow.createCell(3).setCellValue(s.getAdvisorScore() != null ? s.getAdvisorScore() : 0);
                dataRow.createCell(4).setCellValue(s.getReviewerScore() != null ? s.getReviewerScore() : 0);
                dataRow.createCell(5).setCellValue(s.getDefenseScore() != null ? s.getDefenseScore() : 0);
                dataRow.createCell(6).setCellValue(s.getTotalScore() != null ? s.getTotalScore().doubleValue() : 0);
                dataRow.createCell(7).setCellValue(s.getStatus() != null ? s.getStatus().getDescription() : "");
                dataRow.createCell(8).setCellValue(s.getPublishTime() != null
                        ? s.getPublishTime().format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
                        : "");
            }
            for (Integer i = 0; i < headers.length; i++) {
                sheet.autoSizeColumn(i);
            }
            workbook.write(out);
            return out.toByteArray();
        } catch (java.io.IOException e) {
            throw new BusinessException(500, "成绩总表导出失败");
        }
    }

    /** 学生 -> 院系名（student -> class_name -> college 链） */
    private Map<Long, String> collegeNameMap(List<GraduationScore> scores) {
        List<Long> studentIds = scores.stream().map(GraduationScore::getStudentId).distinct().toList();
        if (studentIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, Long> studentClass = studentMapper.selectBatchIds(studentIds).stream()
                .collect(Collectors.toMap(
                        s -> s.getUserId(), s -> s.getClassId() != null ? s.getClassId() : -1L, (a, b) -> a));
        List<Long> classIds = studentClass.values().stream().filter(id -> id > 0).distinct().toList();
        if (classIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, Long> classCollege = classNameMapper.selectBatchIds(classIds).stream()
                .collect(Collectors.toMap(
                        c -> c.getId(), c -> c.getCollegeId() != null ? c.getCollegeId() : -1L, (a, b) -> a));
        List<Long> collegeIds = classCollege.values().stream().filter(id -> id > 0).distinct().toList();
        Map<Long, String> collegeNames = collegeIds.isEmpty() ? Map.of() : collegeMapper.toNameMap(collegeIds);
        Map<Long, String> result = new java.util.HashMap<>();
        for (Long studentId : studentIds) {
            Long classId = studentClass.getOrDefault(studentId, -1L);
            Long collegeId = classCollege.getOrDefault(classId, -1L);
            result.put(studentId, collegeNames.getOrDefault(collegeId, ""));
        }
        return result;
    }

    private String safeName(String name) {
        return name == null ? "活动" : name.replaceAll("[\\\\/:*?\"<>|]", "_");
    }

    private String nvl(String value) {
        return value == null ? "" : value;
    }
}
