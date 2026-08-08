package com.xrq.xxq.module.analysis.service.impl;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xrq.xxq.common.BusinessException;
import com.xrq.xxq.common.PageQuery;
import com.xrq.xxq.common.PageResult;
import com.xrq.xxq.module.analysis.dto.WarningConfigDto;
import com.xrq.xxq.module.analysis.dto.WarningItemDto;
import com.xrq.xxq.module.analysis.dto.WarningScanResultDto;
import com.xrq.xxq.module.analysis.entity.WarningConfig;
import com.xrq.xxq.module.analysis.entity.WarningLevelEnum;
import com.xrq.xxq.module.analysis.entity.WarningRecord;
import com.xrq.xxq.module.analysis.entity.WarningStatusEnum;
import com.xrq.xxq.module.analysis.mapper.WarningConfigMapper;
import com.xrq.xxq.module.analysis.mapper.WarningRecordMapper;
import com.xrq.xxq.module.analysis.service.WarningService;
import com.xrq.xxq.module.analysis.util.CreditSource;
import com.xrq.xxq.module.analysis.util.GpaCalculator;
import com.xrq.xxq.util.StudentScopeResolver;
import com.xrq.xxq.module.clazz.service.ClassNameService;
import com.xrq.xxq.module.course.mapper.CourseMapper;
import com.xrq.xxq.module.selection.mapper.SelectionCampaignMapper;
import org.springframework.context.ApplicationEventPublisher;
import com.xrq.xxq.common.event.WarningActivatedEvent;
import com.xrq.xxq.module.score.entity.Score;
import com.xrq.xxq.module.score.entity.ScoreTypeEnum;
import com.xrq.xxq.module.score.mapper.ScoreMapper;
import com.xrq.xxq.module.semester.entity.Semester;
import com.xrq.xxq.module.semester.mapper.SemesterMapper;
import com.xrq.xxq.module.semester.service.SemesterService;
import com.xrq.xxq.module.user.entity.user.Student;
import com.xrq.xxq.module.user.mapper.StudentMapper;
import com.xrq.xxq.module.user.mapper.UserMapper;
import com.xrq.xxq.util.ParamValidator;
import com.xrq.xxq.util.ReferenceValidator;
import com.xrq.xxq.util.auth.AuthFacade;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 学业预警服务实现：阈值配置、扫描（持久化+通知）、看板与自查。
 * <p>
 * 挂科判定：某课程所有尝试（REGULAR/MAKEUP/RETAKE）最高分 &lt; 60 视为仍未通过。
 * GPA 沿用 {@link GpaCalculator}（5 分制，仅 REGULAR，挂科计 0 但学分计入分母）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WarningServiceImpl implements WarningService {

    private final WarningConfigMapper warningConfigMapper;
    private final WarningRecordMapper warningRecordMapper;
    private final ScoreMapper scoreMapper;
    private final CourseMapper courseMapper;
    private final SelectionCampaignMapper selectionCampaignMapper;
    private final StudentMapper studentMapper;
    private final UserMapper userMapper;
    private final ClassNameService classNameService;
    private final SemesterService semesterService;
    private final SemesterMapper semesterMapper;
    private final ApplicationEventPublisher eventPublisher;
    private final StudentScopeResolver scopeResolver;
    private final ReferenceValidator referenceValidator;

    // ==================== 配置 ====================

    @Override
    public List<WarningConfigDto> listConfig() {
        return warningConfigMapper.selectList(new LambdaQueryWrapper<WarningConfig>()
                        .orderByAsc(WarningConfig::getId)).stream()
                .map(this::toConfigDto).toList();
    }

    @Override
    @Transactional
    public void updateConfig(List<WarningConfigDto> configs) {
        ParamValidator.requireNonEmpty(configs, "配置");
        for (WarningConfigDto dto : configs) {
            ParamValidator.requireNonNull(dto.getLevel(), "预警级别");
            WarningConfig exist = warningConfigMapper.selectOne(
                    new LambdaQueryWrapper<WarningConfig>().eq(WarningConfig::getLevel, dto.getLevel()));
            if (exist == null) {
                throw new BusinessException(404, "预警级别 " + dto.getLevel() + " 配置不存在");
            }
            if (warningConfigMapper.selectCount(new LambdaQueryWrapper<WarningConfig>()
                    .eq(WarningConfig::getLevel, dto.getLevel())
                    .ne(WarningConfig::getId, exist.getId())) > 0) {
                throw new BusinessException(409, "预警级别已存在");
            }
            exist.setGpaThreshold(dto.getGpaThreshold());
            exist.setFailCountThreshold(dto.getFailCountThreshold());
            exist.setSemesterFailThreshold(dto.getSemesterFailThreshold());
            exist.setEnabled(dto.getEnabled() == null ? 1 : dto.getEnabled());
            warningConfigMapper.updateById(exist);
        }
    }

    // ==================== 扫描 ====================

    @Override
    @Transactional
    public WarningScanResultDto scan(Long callerUserId) {
        Semester current = semesterService.getCurrent();
        if (current == null) {
            throw new BusinessException(400, "无当前学期，无法扫描");
        }
        Long semesterId = current.getId();
        referenceValidator.requireExists(semesterMapper, semesterId, "学期");

        // 启用的阈值配置，按严重程度降序（红>橙>黄）
        List<WarningConfig> configs = warningConfigMapper.selectList(new LambdaQueryWrapper<WarningConfig>()
                .eq(WarningConfig::getEnabled, 1));
        if (configs.isEmpty()) {
            throw new BusinessException(400, "无启用的预警阈值配置");
        }
        configs.sort(Comparator.comparingInt((WarningConfig c) -> c.getLevel().ordinal()).reversed());

        // 一次性加载全部成绩与课程学分，内存分组
        List<Score> allScores = scoreMapper.selectList(new LambdaQueryWrapper<>());
        // 学分映射：常规课 course.id + 公选课 selection_campaign.id，合并到同一 map
        CreditSource creditSource = CreditSource.loadAll(courseMapper, selectionCampaignMapper);
        Map<Long, List<Score>> byStudent = allScores.stream()
                .collect(Collectors.groupingBy(Score::getStudentUserId));

        int scanned = 0;
        int warned = 0;
        int resolved = 0;
        Map<String, Integer> byLevel = new LinkedHashMap<>();
        byLevel.put(WarningLevelEnum.YELLOW.getDescription(), 0);
        byLevel.put(WarningLevelEnum.ORANGE.getDescription(), 0);
        byLevel.put(WarningLevelEnum.RED.getDescription(), 0);

        for (Map.Entry<Long, List<Score>> e : byStudent.entrySet()) {
            Long studentUserId = e.getKey();
            List<Score> scores = e.getValue();
            scanned++;
            StudentMetrics m = computeMetrics(scores, creditSource, semesterId);
            WarningLevelEnum target = matchLevel(configs, m);

            if (target != null) {
                int newlyActivated = upsertWarning(studentUserId, semesterId, target, m);
                warned += newlyActivated;
                byLevel.merge(target.getDescription(), 1, Integer::sum);
            } else {
                resolved += resolveAll(studentUserId, semesterId);
            }
        }

        WarningScanResultDto result = new WarningScanResultDto();
        result.setScannedCount(scanned);
        result.setWarnedCount(warned);
        result.setResolvedCount(resolved);
        result.setByLevel(byLevel);
        return result;
    }

    /** 计算学生指标：累计 GPA、累计挂科、本学期挂科。挂科=该课程所有尝试最高分<60。 */
    private StudentMetrics computeMetrics(List<Score> scores, CreditSource creditSource, Long semesterId) {
        List<Score> regular = scores.stream()
                .filter(s -> s.getScoreType() == ScoreTypeEnum.REGULAR).toList();
        BigDecimal gpa = GpaCalculator.weightedGpa(regular, creditSource);

        // 每门课最高分（含补考/重修）+ REGULAR 所在学期；键加前缀避免 course.id 与 campaign.id 数值空间重叠
        Map<String, BigDecimal> bestByCourse = new HashMap<>();
        Map<String, Long> regularSemByCourse = new HashMap<>();
        for (Score s : scores) {
            if (s.getTotalScore() == null) {
                continue;
            }
            String key = CreditSource.keyOf(s);
            if (key == null) {
                continue;
            }
            bestByCourse.merge(key, s.getTotalScore(),
                    (a, b) -> a.compareTo(b) >= 0 ? a : b);
            if (s.getScoreType() == ScoreTypeEnum.REGULAR && s.getSemesterId() != null) {
                regularSemByCourse.putIfAbsent(key, s.getSemesterId());
            }
        }
        int failCount = 0;
        int semesterFailCount = 0;
        for (Map.Entry<String, BigDecimal> be : bestByCourse.entrySet()) {
            if (be.getValue().doubleValue() < 60) {
                failCount++;
                if (Objects.equals(regularSemByCourse.get(be.getKey()), semesterId)) {
                    semesterFailCount++;
                }
            }
        }
        return new StudentMetrics(gpa, failCount, semesterFailCount);
    }

    /** 按严重程度降序匹配，返回首个命中的级别；无命中返回 null。 */
    private WarningLevelEnum matchLevel(List<WarningConfig> configs, StudentMetrics m) {
        for (WarningConfig c : configs) {
            boolean hit = c.getGpaThreshold() != null && m.gpa() != null
                    && m.gpa().compareTo(c.getGpaThreshold()) < 0;
            if (!hit && c.getFailCountThreshold() != null && m.failCount() >= c.getFailCountThreshold()) {
                hit = true;
            }
            if (!hit && c.getSemesterFailThreshold() != null
                    && m.semesterFailCount() >= c.getSemesterFailThreshold()) {
                hit = true;
            }
            if (hit) {
                return c.getLevel();
            }
        }
        return null;
    }

    /**
     * upsert 预警记录：命中则激活目标级别（新激活时发通知），其余 ACTIVE 级别解除。
     * 返回 1 表示本次新激活，0 表示此前已是该级别 ACTIVE。
     */
    private int upsertWarning(Long studentUserId, Long semesterId, WarningLevelEnum target, StudentMetrics m) {
        List<WarningRecord> existing = warningRecordMapper.selectList(new LambdaQueryWrapper<WarningRecord>()
                .eq(WarningRecord::getStudentUserId, studentUserId)
                .eq(WarningRecord::getSemesterId, semesterId));
        String reason = String.format("累计GPA %s，累计挂科 %d 门，本学期挂科 %d 门",
                m.gpa() != null ? m.gpa().toPlainString() : "无", m.failCount(), m.semesterFailCount());

        int newlyActivated = 0;
        WarningRecord targetRec = existing.stream()
                .filter(r -> r.getLevel() == target).findFirst().orElse(null);
        boolean wasActive = targetRec != null && targetRec.getStatus() == WarningStatusEnum.ACTIVE;
        if (targetRec == null) {
            targetRec = new WarningRecord();
            targetRec.setStudentUserId(studentUserId);
            targetRec.setSemesterId(semesterId);
            targetRec.setLevel(target);
        }
        targetRec.setReason(reason);
        targetRec.setGpaSnapshot(m.gpa());
        targetRec.setFailCount(m.failCount());
        targetRec.setSemesterFailCount(m.semesterFailCount());
        targetRec.setStatus(WarningStatusEnum.ACTIVE);
        if (targetRec.getId() == null) {
            referenceValidator.requireExists(userMapper, studentUserId, "用户");
            warningRecordMapper.insert(targetRec);
        } else {
            warningRecordMapper.updateById(targetRec);
        }
        if (!wasActive) {
            newlyActivated = 1;
            sendWarningNotification(studentUserId, target, reason);
        }

        // 解除其它 ACTIVE 级别（学生已好转或级别变化）
        for (WarningRecord r : existing) {
            if (r.getStatus() == WarningStatusEnum.ACTIVE && r.getLevel() != target) {
                r.setStatus(WarningStatusEnum.RESOLVED);
                warningRecordMapper.updateById(r);
            }
        }
        return newlyActivated;
    }

    /** 解除该学生本学期所有 ACTIVE 记录，返回解除数。 */
    private int resolveAll(Long studentUserId, Long semesterId) {
        List<WarningRecord> existing = warningRecordMapper.selectList(new LambdaQueryWrapper<WarningRecord>()
                .eq(WarningRecord::getStudentUserId, studentUserId)
                .eq(WarningRecord::getSemesterId, semesterId)
                .eq(WarningRecord::getStatus, WarningStatusEnum.ACTIVE));
        for (WarningRecord r : existing) {
            r.setStatus(WarningStatusEnum.RESOLVED);
            warningRecordMapper.updateById(r);
        }
        return existing.size();
    }

    private void sendWarningNotification(Long studentUserId, WarningLevelEnum level, String reason) {
        eventPublisher.publishEvent(new WarningActivatedEvent(studentUserId, level.getDescription(), reason));
    }

    // ==================== 看板 / 查询 ====================

    @Override
    public PageResult<WarningItemDto> list(Long semesterId, WarningLevelEnum level,
                                           Long callerUserId, String callerUserType, PageQuery pageQuery) {
        Long semId = semesterService.resolveOrDefault(semesterId);
        // 院系仅看本院学生
        List<Long> scopedStudentIds;
        if (AuthFacade.USER_TYPE_ACADEMIC_ADMIN.equals(callerUserType)) {
            scopedStudentIds = null;
        } else if (AuthFacade.USER_TYPE_DEPARTMENT.equals(callerUserType)) {
            scopedStudentIds = scopeResolver.resolveScopedStudentUserIds(callerUserType, callerUserId, null);
        } else {
            throw new BusinessException(403, "权限不足");
        }

        LambdaQueryWrapper<WarningRecord> w = new LambdaQueryWrapper<WarningRecord>()
                .eq(WarningRecord::getStatus, WarningStatusEnum.ACTIVE);
        if (semId != null) {
            w.eq(WarningRecord::getSemesterId, semId);
        }
        if (level != null) {
            w.eq(WarningRecord::getLevel, level);
        }
        if (scopedStudentIds != null) {
            if (scopedStudentIds.isEmpty()) {
                return new PageResult<>(List.of(), 0, pageQuery.resolvedPage(), pageQuery.resolvedSize(), 0);
            }
            w.in(WarningRecord::getStudentUserId, scopedStudentIds);
        }
        // 红色 > 橙色 > 黄色，同级按更新时间降序：下推到 SQL 以保证分页全局有序
        w.last("ORDER BY FIELD(level, 'RED', 'ORANGE', 'YELLOW'), update_time DESC");
        Page<WarningRecord> page = warningRecordMapper.selectPage(pageQuery.toPage(), w);
        return PageResult.of(page, enrich(page.getRecords()));
    }

    @Override
    public List<WarningItemDto> myWarnings(Long studentUserId) {
        List<WarningRecord> records = warningRecordMapper.selectList(new LambdaQueryWrapper<WarningRecord>()
                .eq(WarningRecord::getStudentUserId, studentUserId)
                .eq(WarningRecord::getStatus, WarningStatusEnum.ACTIVE)
                .orderByDesc(WarningRecord::getUpdateTime));
        records.sort(Comparator.comparingInt((WarningRecord r) -> r.getLevel().ordinal()).reversed());
        return enrich(records);
    }

    /** 富化：补学生姓名/学号/班级名/学期名。 */
    private List<WarningItemDto> enrich(List<WarningRecord> records) {
        if (records.isEmpty()) {
            return List.of();
        }
        List<Long> studentUserIds = records.stream().map(WarningRecord::getStudentUserId).distinct().toList();
        Map<Long, String> nameMap = userMapper.toNameMap(studentUserIds);
        Map<Long, Student> stuMap = studentMapper.selectList(
                        new LambdaQueryWrapper<Student>().in(Student::getUserId, studentUserIds)).stream()
                .collect(Collectors.toMap(Student::getUserId, s -> s, (a, b) -> a));
        Set<Long> classIds = stuMap.values().stream().map(Student::getClassId)
                .filter(Objects::nonNull).collect(Collectors.toSet());
        Map<Long, String> classNameMap = classNameService.toNameMap(classIds);
        Set<Long> semesterIds = records.stream().map(WarningRecord::getSemesterId)
                .filter(Objects::nonNull).collect(Collectors.toSet());
        Map<Long, String> semNameMap = semesterService.toNameMap(semesterIds);

        return records.stream().map(r -> {
            WarningItemDto dto = new WarningItemDto();
            dto.setId(r.getId());
            dto.setStudentUserId(r.getStudentUserId());
            dto.setStudentName(nameMap.get(r.getStudentUserId()));
            Student stu = stuMap.get(r.getStudentUserId());
            dto.setStudentNo(stu != null ? stu.getStudentNo() : null);
            dto.setClassName(stu != null && stu.getClassId() != null ? classNameMap.get(stu.getClassId()) : null);
            dto.setLevel(r.getLevel());
            dto.setReason(r.getReason());
            dto.setGpa(r.getGpaSnapshot());
            dto.setFailCount(r.getFailCount());
            dto.setSemesterFailCount(r.getSemesterFailCount());
            dto.setSemesterId(r.getSemesterId());
            dto.setSemesterName(semNameMap.get(r.getSemesterId()));
            dto.setStatus(r.getStatus());
            dto.setCreateTime(r.getCreateTime());
            return dto;
        }).toList();
    }

    private WarningConfigDto toConfigDto(WarningConfig c) {
        WarningConfigDto dto = new WarningConfigDto();
        dto.setId(c.getId());
        dto.setLevel(c.getLevel());
        dto.setGpaThreshold(c.getGpaThreshold());
        dto.setFailCountThreshold(c.getFailCountThreshold());
        dto.setSemesterFailThreshold(c.getSemesterFailThreshold());
        dto.setEnabled(c.getEnabled());
        return dto;
    }

    /** 学生扫描指标中间结构。 */
    private record StudentMetrics(BigDecimal gpa, int failCount, int semesterFailCount) {
    }
}
