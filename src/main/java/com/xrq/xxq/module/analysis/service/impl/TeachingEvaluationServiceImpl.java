package com.xrq.xxq.module.analysis.service.impl;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.xrq.xxq.common.BusinessException;
import com.xrq.xxq.common.PageQuery;
import com.xrq.xxq.common.PageResult;
import com.xrq.xxq.module.analysis.dto.EvaluationScoreView;
import com.xrq.xxq.module.analysis.dto.EvaluationStatusDto;
import com.xrq.xxq.module.analysis.dto.EvaluationSubmitRequest;
import com.xrq.xxq.module.analysis.dto.LearningProgressDto;
import com.xrq.xxq.module.analysis.dto.ScoreItemDto;
import com.xrq.xxq.module.analysis.dto.TeacherQualityDto;
import com.xrq.xxq.module.analysis.dto.TeachingEvaluationView;
import com.xrq.xxq.module.analysis.dto.TemplateItemDto;
import com.xrq.xxq.module.analysis.entity.EvaluationStatusEnum;
import com.xrq.xxq.module.analysis.entity.EvaluationTemplate;
import com.xrq.xxq.module.analysis.entity.EvaluationPeriod;
import com.xrq.xxq.module.analysis.entity.TeachingEvaluation;
import com.xrq.xxq.module.analysis.entity.TeachingEvaluationScore;
import com.xrq.xxq.module.analysis.mapper.EvaluationPeriodMapper;
import com.xrq.xxq.module.analysis.mapper.EvaluationTemplateMapper;
import com.xrq.xxq.module.analysis.mapper.TeachingEvaluationMapper;
import com.xrq.xxq.module.analysis.mapper.TeachingEvaluationScoreMapper;
import com.xrq.xxq.module.analysis.service.EvaluationTemplateService;
import com.xrq.xxq.module.analysis.service.ProgressService;
import com.xrq.xxq.module.analysis.service.TeachingEvaluationService;
import com.xrq.xxq.module.analysis.util.ScoreStats;
import com.xrq.xxq.module.course.service.CourseInfoResolver;
import com.xrq.xxq.module.score.entity.Score;
import com.xrq.xxq.module.score.entity.ScoreTypeEnum;
import com.xrq.xxq.module.score.mapper.ScoreMapper;
import com.xrq.xxq.module.semester.entity.Semester;
import com.xrq.xxq.module.semester.mapper.SemesterMapper;
import com.xrq.xxq.module.semester.service.SemesterService;
import com.xrq.xxq.module.teachinfo.entity.TeachInfo;
import com.xrq.xxq.module.teachinfo.mapper.TeachInfoMapper;
import com.xrq.xxq.module.user.entity.User;
import com.xrq.xxq.module.user.entity.user.Department;
import com.xrq.xxq.module.user.entity.user.Teacher;
import com.xrq.xxq.module.user.mapper.DepartmentMapper;
import com.xrq.xxq.module.user.mapper.TeacherMapper;
import com.xrq.xxq.module.user.mapper.UserMapper;
import com.xrq.xxq.module.college.mapper.CollegeMapper;
import com.xrq.xxq.util.DistributedLock;
import com.xrq.xxq.util.ParamValidator;
import com.xrq.xxq.util.ReferenceValidator;
import com.xrq.xxq.util.StudentEnrollmentResolver;
import com.xrq.xxq.util.TeacherNameResolver;
import com.xrq.xxq.util.auth.AuthFacade;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 教师教学质量评估服务实现（模板驱动）。
 * <p>评教侧：按所绑定模板（课程覆盖优先，否则全局默认）的指标动态评分，明细存 teaching_evaluation_score，
 * avg_score 为各指标原始分均值；成绩侧：所授课程均分/及格率（score.teacherId 直查）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TeachingEvaluationServiceImpl implements TeachingEvaluationService {

    private final TeachingEvaluationMapper evaluationMapper;
    private final TeachingEvaluationScoreMapper scoreDetailMapper;
    private final EvaluationPeriodMapper periodMapper;
    private final EvaluationTemplateMapper evaluationTemplateMapper;
    private final EvaluationTemplateService evaluationTemplateService;
    private final TeachInfoMapper teachInfoMapper;
    private final CourseInfoResolver courseInfoResolver;
    private final TeacherMapper teacherMapper;
    private final UserMapper userMapper;
    private final DepartmentMapper departmentMapper;
    private final ScoreMapper scoreMapper;
    private final SemesterService semesterService;
    private final ProgressService progressService;
    private final ReferenceValidator referenceValidator;
    private final DistributedLock distributedLock;
    private final SemesterMapper semesterMapper;
    private final StudentEnrollmentResolver enrollmentResolver;
    private final TeacherNameResolver teacherNameResolver;
    private final CollegeMapper collegeMapper;

    // ==================== 评教提交 ====================

    @Override
    @Transactional
    public TeachingEvaluationView submit(EvaluationSubmitRequest req, Long studentUserId) {
        ParamValidator.requireNonNull(req.getTeachInfoId(), "授课安排ID");
        if (req.getScores() == null || req.getScores().isEmpty()) {
            throw new BusinessException(400, "至少需要 1 项评分");
        }

        TeachInfo info = teachInfoMapper.selectById(req.getTeachInfoId());
        if (info == null) {
            throw new BusinessException(404, "授课安排不存在");
        }
        referenceValidator.requireExists(teacherMapper, info.getTeacherId(), "教师");
        referenceValidator.requireCourseRef(info.getCourseId(), info.getCampaignId());
        referenceValidator.requireExists(semesterMapper, info.getSemesterId(), "学期");
        referenceValidator.requireExists(userMapper, studentUserId, "用户");
        assertPeriodOpen(info.getSemesterId());
        if (!enrollmentResolver.isEnrolled(info.getId(), studentUserId)) {
            throw new BusinessException(403, "权限不足");
        }

        // 解析评教模板（课程覆盖优先，否则全局默认）+ 校验评分
        var form = evaluationTemplateService.getEvaluationForm(req.getTeachInfoId());
        referenceValidator.requireExists(evaluationTemplateMapper, form.getTemplateId(), "评教模板");
        List<TemplateItemDto> templateItems = form.getItems();
        if (templateItems == null || templateItems.isEmpty()) {
            throw new BusinessException(400, "评教模板无指标");
        }
        Map<Long, TemplateItemDto> itemMap = templateItems.stream()
                .collect(Collectors.toMap(TemplateItemDto::getItemId, i -> i, (a, b) -> a));
        Map<Long, Integer> scoreMap = req.getScores().stream()
                .collect(Collectors.toMap(ScoreItemDto::getItemId, s -> s.getScore(), (a, b) -> a));

        for (TemplateItemDto item : templateItems) {
            Integer score = scoreMap.get(item.getItemId());
            if (score == null) {
                if (item.getRequired() == null || item.getRequired() == 1) {
                    throw new BusinessException(400, "指标「" + item.getItemName() + "」未评分");
                }
                continue;
            }
            Integer max = item.getMaxScore() == null ? 5 : item.getMaxScore();
            if (score < 1 || score > max) {
                throw new BusinessException(400, "指标「" + item.getItemName() + "」评分须为 1-" + max);
            }
        }
        for (Long submittedId : scoreMap.keySet()) {
            if (!itemMap.containsKey(submittedId)) {
                throw new BusinessException(400, "提交了不属于评教模板的指标");
            }
        }

        // avg = 已评分项原始分均值
        Double avg = req.getScores().stream().mapToInt(s -> s.getScore()).average().orElse(0);
        BigDecimal avgScore = BigDecimal.valueOf(avg).setScale(2, RoundingMode.HALF_UP);

        return distributedLock.withLock("eval:" + req.getTeachInfoId() + ":" + studentUserId, 30L, () -> {
            TeachingEvaluation exist = evaluationMapper.selectOne(new LambdaQueryWrapper<TeachingEvaluation>()
                    .eq(TeachingEvaluation::getTeachInfoId, req.getTeachInfoId())
                    .eq(TeachingEvaluation::getStudentUserId, studentUserId));
            TeachingEvaluation ev = exist != null ? exist : new TeachingEvaluation();
            ev.setTeachInfoId(info.getId());
            ev.setTeacherId(info.getTeacherId());
            ev.setCourseId(info.getCourseId());
            ev.setCampaignId(info.getCampaignId());
            ev.setSemesterId(info.getSemesterId());
            ev.setStudentUserId(studentUserId);
            ev.setTemplateId(form.getTemplateId());
            ev.setAvgScore(avgScore);
            ev.setComment(req.getComment());
            ev.setAnonymous(1);
            if (exist == null) {
                evaluationMapper.insert(ev);
            } else {
                evaluationMapper.updateById(ev);
            }

            // 增量更新得分明细（按 itemId diff，避免全删全插；快照指标名/满分）
            List<TeachingEvaluationScore> existingDetails = scoreDetailMapper.selectList(
                    new LambdaQueryWrapper<TeachingEvaluationScore>()
                            .eq(TeachingEvaluationScore::getEvaluationId, ev.getId()));
            Map<Long, TeachingEvaluationScore> existingByItemId = existingDetails.stream()
                    .collect(Collectors.toMap(TeachingEvaluationScore::getItemId, d -> d, (a, b) -> a));
            Set<Long> newItemIds = req.getScores().stream().map(ScoreItemDto::getItemId).collect(Collectors.toSet());
            // 删除：现有 - 新
            for (TeachingEvaluationScore d : existingDetails) {
                if (!newItemIds.contains(d.getItemId())) {
                    scoreDetailMapper.deleteById(d.getId());
                }
            }
            // 新增/更新
            for (ScoreItemDto s : req.getScores()) {
                TemplateItemDto item = itemMap.get(s.getItemId());
                TeachingEvaluationScore existing = existingByItemId.get(s.getItemId());
                if (existing == null) {
                    TeachingEvaluationScore detail = new TeachingEvaluationScore();
                    detail.setEvaluationId(ev.getId());
                    detail.setItemId(s.getItemId());
                    detail.setItemName(item.getItemName());
                    detail.setMaxScore(item.getMaxScore());
                    detail.setScore(s.getScore());
                    scoreDetailMapper.insert(detail);
                } else {
                    existing.setItemName(item.getItemName());
                    existing.setMaxScore(item.getMaxScore());
                    existing.setScore(s.getScore());
                    scoreDetailMapper.updateById(existing);
                }
            }
            return toView(ev, loadNameMaps(List.of(ev)));
        });
    }

    // ==================== 我的评教 ====================

    @Override
    public List<TeachingEvaluationView> myEvaluations(Long studentUserId) {
        List<TeachingEvaluation> evals = evaluationMapper.selectList(new LambdaQueryWrapper<TeachingEvaluation>()
                .eq(TeachingEvaluation::getStudentUserId, studentUserId)
                .orderByDesc(TeachingEvaluation::getCreateTime));
        if (evals.isEmpty()) {
            return List.of();
        }
        NameMaps names = loadNameMaps(evals);
        return evals.stream().map(e -> toView(e, names)).toList();
    }

    // ==================== 评教周期 ====================

    @Override
    @Transactional
    public EvaluationStatusDto openPeriod(Long callerUserId) {
        Semester current = semesterService.getCurrent();
        if (current == null) {
            throw new BusinessException(400, "无当前学期");
        }
        referenceValidator.requireExists(semesterMapper, current.getId(), "学期");
        if (evaluationTemplateMapper.selectCount(new LambdaQueryWrapper<EvaluationTemplate>()
                .eq(EvaluationTemplate::getIsDefault, 1)) == 0) {
            throw new BusinessException(400, "请先配置默认评教模板");
        }
        EvaluationPeriod period = periodMapper.selectOne(
                new LambdaQueryWrapper<EvaluationPeriod>().eq(EvaluationPeriod::getSemesterId, current.getId()));
        if (period == null) {
            period = new EvaluationPeriod();
            period.setSemesterId(current.getId());
        }
        period.setStatus(EvaluationStatusEnum.OPEN);
        period.setOpenUserId(callerUserId);
        period.setOpenTime(LocalDateTime.now());
        period.setCloseTime(null);
        if (period.getId() == null) {
            periodMapper.insert(period);
        } else {
            periodMapper.updateById(period);
        }
        return toStatusDto(period, current);
    }

    @Override
    @Transactional
    public EvaluationStatusDto closePeriod(Long callerUserId) {
        Semester current = semesterService.getCurrent();
        if (current == null) {
            throw new BusinessException(400, "无当前学期");
        }
        EvaluationPeriod period = periodMapper.selectOne(
                new LambdaQueryWrapper<EvaluationPeriod>().eq(EvaluationPeriod::getSemesterId, current.getId()));
        if (period == null) {
            return toStatusDto(null, current);
        }
        period.setStatus(EvaluationStatusEnum.CLOSED);
        period.setCloseTime(LocalDateTime.now());
        periodMapper.updateById(period);
        return toStatusDto(period, current);
    }

    @Override
    public EvaluationStatusDto getPeriodStatus(Long callerUserId, String callerUserType) {
        Semester current = semesterService.getCurrent();
        if (current == null) {
            EvaluationStatusDto dto = new EvaluationStatusDto();
            dto.setOpen(false);
            dto.setMessage("暂无评教");
            dto.setCourses(List.of());
            return dto;
        }
        EvaluationPeriod period = periodMapper.selectOne(
                new LambdaQueryWrapper<EvaluationPeriod>().eq(EvaluationPeriod::getSemesterId, current.getId()));
        EvaluationStatusDto dto = toStatusDto(period, current);
        // 学生且已开放：附带可评课程列表（含课程名，解决公选课不知课程名的问题）
        if (Boolean.TRUE.equals(dto.getOpen()) && AuthFacade.USER_TYPE_STUDENT.equals(callerUserType)) {
            dto.setCourses(loadEvaluableCourses(callerUserId));
        } else {
            dto.setCourses(List.of());
        }
        return dto;
    }

    /** 学生当前学期可评课程：复用学习进度的课程解析（含公选课 + 课程名），附加是否已评教。 */
    private List<EvaluationStatusDto.EvaluableCourse> loadEvaluableCourses(Long studentUserId) {
        LearningProgressDto progress = progressService.getProgress(
                studentUserId, studentUserId, AuthFacade.USER_TYPE_STUDENT);
        List<LearningProgressDto.CourseProgress> courses = progress.getCourses();
        if (courses == null || courses.isEmpty()) {
            return List.of();
        }
        List<Long> teachInfoIds = courses.stream()
                .map(LearningProgressDto.CourseProgress::getTeachInfoId)
                .filter(Objects::nonNull).toList();
        Set<Long> evaluatedIds = teachInfoIds.isEmpty() ? Set.of()
                : evaluationMapper.selectList(new LambdaQueryWrapper<TeachingEvaluation>()
                        .select(TeachingEvaluation::getTeachInfoId)
                        .eq(TeachingEvaluation::getStudentUserId, studentUserId)
                        .in(TeachingEvaluation::getTeachInfoId, teachInfoIds)).stream()
                        .map(TeachingEvaluation::getTeachInfoId).collect(Collectors.toSet());
        return courses.stream().map(c -> {
            EvaluationStatusDto.EvaluableCourse ec = new EvaluationStatusDto.EvaluableCourse();
            ec.setTeachInfoId(c.getTeachInfoId());
            ec.setCourseId(c.getCourseId());
            ec.setCourseName(c.getCourseName());
            ec.setTeacherName(c.getTeacherName());
            ec.setEvaluated(evaluatedIds.contains(c.getTeachInfoId()));
            return ec;
        }).toList();
    }

    /** 提交评教前置：该授课安排所属学期的评教周期必须处于 OPEN。 */
    private void assertPeriodOpen(Long semesterId) {
        if (semesterId == null) {
            throw new BusinessException(400, "评教暂未开放，请等待教务开启");
        }
        EvaluationPeriod period = periodMapper.selectOne(
                new LambdaQueryWrapper<EvaluationPeriod>().eq(EvaluationPeriod::getSemesterId, semesterId));
        if (period == null || period.getStatus() != EvaluationStatusEnum.OPEN) {
            throw new BusinessException(400, "评教暂未开放，请等待教务开启");
        }
    }

    private EvaluationStatusDto toStatusDto(EvaluationPeriod period, Semester semester) {
        EvaluationStatusDto dto = new EvaluationStatusDto();
        Boolean open = period != null && period.getStatus() == EvaluationStatusEnum.OPEN;
        dto.setOpen(open);
        dto.setMessage(open ? null : "暂无评教");
        dto.setSemesterId(semester != null ? semester.getId() : null);
        dto.setSemesterName(semester != null ? semester.getName() : null);
        if (period != null) {
            dto.setOpenTime(period.getOpenTime());
            dto.setCloseTime(period.getCloseTime());
        }
        return dto;
    }

    // ==================== 教师质量 ====================

    @Override
    public TeacherQualityDto teacherQuality(Long teacherId, Long semesterId,
                                            Long callerUserId, String callerUserType) {
        Teacher teacher = teacherMapper.selectById(teacherId);
        if (teacher == null) {
            throw new BusinessException(404, "教师不存在");
        }
        assertCanViewTeacher(teacher, callerUserId, callerUserType);

        LambdaQueryWrapper<TeachingEvaluation> ew = new LambdaQueryWrapper<TeachingEvaluation>()
                .eq(TeachingEvaluation::getTeacherId, teacherId);
        if (semesterId != null) {
            ew.eq(TeachingEvaluation::getSemesterId, semesterId);
        }
        List<TeachingEvaluation> evals = evaluationMapper.selectList(ew);
        LambdaQueryWrapper<Score> sw = new LambdaQueryWrapper<Score>()
                .eq(Score::getTeacherId, teacherId)
                .eq(Score::getScoreType, ScoreTypeEnum.REGULAR);
        if (semesterId != null) {
            sw.eq(Score::getSemesterId, semesterId);
        }
        List<Score> scores = scoreMapper.selectList(sw);
        Map<Long, String> collegeNameMap = collegeMapper.toNameMap(
                teacher.getCollegeId() != null ? List.of(teacher.getCollegeId()) : List.of());
        TeacherQualityDto dto = buildQuality(teacher, evals, scores, collegeNameMap);
        if (teacher.getUserId() != null) {
            User u = userMapper.selectById(teacher.getUserId());
            dto.setTeacherName(u != null ? u.getName() : null);
        }
        return dto;
    }

    @Override
    public TeacherQualityDto myTeacherQuality(Long callerUserId, Long semesterId) {
        Teacher self = teacherMapper.findByUserId(callerUserId);
        if (self == null) {
            throw new BusinessException(404, "教师信息不存在");
        }
        return teacherQuality(self.getId(), semesterId, callerUserId, AuthFacade.USER_TYPE_TEACHER);
    }

    @Override
    public PageResult<TeacherQualityDto> listTeacherQuality(Long semesterId, Long callerUserId, String callerUserType, PageQuery pageQuery) {
        List<Teacher> teachers = teacherMapper.selectList(new LambdaQueryWrapper<>());
        if (AuthFacade.USER_TYPE_DEPARTMENT.equals(callerUserType)) {
            Department dept = departmentMapper.findByUserId(callerUserId);
            Long collegeId = dept != null ? dept.getCollegeId() : null;
            teachers = teachers.stream()
                    .filter(t -> collegeId != null && collegeId.equals(t.getCollegeId()))
                    .toList();
        } else if (!AuthFacade.USER_TYPE_ACADEMIC_ADMIN.equals(callerUserType)) {
            throw new BusinessException(403, "权限不足");
        }
        if (teachers.isEmpty()) {
            return new PageResult<>(List.of(), 0L, pageQuery.resolvedPage(), pageQuery.resolvedSize(), 0L);
        }

        List<Long> teacherIds = teachers.stream().map(Teacher::getId).toList();
        LambdaQueryWrapper<TeachingEvaluation> ew = new LambdaQueryWrapper<TeachingEvaluation>()
                .in(TeachingEvaluation::getTeacherId, teacherIds);
        if (semesterId != null) {
            ew.eq(TeachingEvaluation::getSemesterId, semesterId);
        }
        Map<Long, List<TeachingEvaluation>> evalsByTeacher = evaluationMapper.selectList(ew).stream()
                .collect(Collectors.groupingBy(TeachingEvaluation::getTeacherId));
        LambdaQueryWrapper<Score> sw = new LambdaQueryWrapper<Score>()
                .in(Score::getTeacherId, teacherIds)
                .eq(Score::getScoreType, ScoreTypeEnum.REGULAR);
        if (semesterId != null) {
            sw.eq(Score::getSemesterId, semesterId);
        }
        Map<Long, List<Score>> scoresByTeacher = scoreMapper.selectList(sw).stream()
                .collect(Collectors.groupingBy(Score::getTeacherId));

        // 批量取教师姓名
        Map<Long, Long> teacherUserId = teachers.stream()
                .filter(t -> t.getUserId() != null)
                .collect(Collectors.toMap(Teacher::getId, Teacher::getUserId, (a, b) -> a));
        Map<Long, String> userNameMap = userMapper.toNameMap(teacherUserId.values());
        Map<Long, String> collegeNameMap = collegeMapper.toNameMap(
                teachers.stream().map(Teacher::getCollegeId).filter(Objects::nonNull).distinct().toList());

        List<TeacherQualityDto> result = new ArrayList<>();
        for (Teacher t : teachers) {
            List<TeachingEvaluation> evals = evalsByTeacher.getOrDefault(t.getId(), List.of());
            List<Score> scores = scoresByTeacher.getOrDefault(t.getId(), List.of());
            if (evals.isEmpty() && scores.isEmpty()) {
                continue; // 无教学数据，跳过
            }
            TeacherQualityDto dto = buildQuality(t, evals, scores, collegeNameMap);
            dto.setTeacherName(userNameMap.get(teacherUserId.get(t.getId())));
            result.add(dto);
        }
        result.sort(Comparator.comparing(TeacherQualityDto::getAvgEvaluationScore,
                Comparator.nullsLast(Comparator.reverseOrder())));
        // 聚合查询无法将分页下推到 SQL，全局排序后内存切片
        return PageResult.slice(result, pageQuery);
    }

    private void assertCanViewTeacher(Teacher teacher, Long callerUserId, String callerUserType) {
        if (AuthFacade.USER_TYPE_ACADEMIC_ADMIN.equals(callerUserType)) {
            return;
        }
        if (AuthFacade.USER_TYPE_TEACHER.equals(callerUserType)) {
            Teacher self = teacherMapper.findByUserId(callerUserId);
            if (self == null || !self.getId().equals(teacher.getId())) {
                throw new BusinessException(403, "权限不足");
            }
            return;
        }
        if (AuthFacade.USER_TYPE_DEPARTMENT.equals(callerUserType)) {
            Department dept = departmentMapper.findByUserId(callerUserId);
            if (dept == null || !Objects.equals(dept.getCollegeId(), teacher.getCollegeId())) {
                throw new BusinessException(403, "权限不足");
            }
            return;
        }
        throw new BusinessException(403, "权限不足");
    }

    /** 由已加载的评教与成绩列表组装质量 DTO。itemAverages 按 teaching_evaluation_score 快照指标名分组。 */
    private TeacherQualityDto buildQuality(Teacher teacher, List<TeachingEvaluation> evals, List<Score> scores,
                                           Map<Long, String> collegeNameMap) {
        TeacherQualityDto dto = new TeacherQualityDto();
        dto.setTeacherId(teacher.getId());
        dto.setTeacherName(null); // 由调用方按需富化（批量场景已在外部解析）
        dto.setDepartment(collegeNameMap.get(teacher.getCollegeId()));

        // 评教侧
        dto.setEvalCount(evals.size());
        if (!evals.isEmpty()) {
            BigDecimal sumAvg = BigDecimal.ZERO;
            for (TeachingEvaluation e : evals) {
                if (e.getAvgScore() != null) {
                    sumAvg = sumAvg.add(e.getAvgScore());
                }
            }
            dto.setAvgEvaluationScore(sumAvg.divide(BigDecimal.valueOf(evals.size()), 2, RoundingMode.HALF_UP));

            List<Long> evalIds = evals.stream().map(TeachingEvaluation::getId).toList();
            List<TeachingEvaluationScore> details = scoreDetailMapper.selectList(
                    new LambdaQueryWrapper<TeachingEvaluationScore>()
                            .in(TeachingEvaluationScore::getEvaluationId, evalIds));
            Map<String, List<TeachingEvaluationScore>> byName = details.stream()
                    .collect(Collectors.groupingBy(TeachingEvaluationScore::getItemName,
                            LinkedHashMap::new, Collectors.toList()));
            Map<String, BigDecimal> itemAverages = new LinkedHashMap<>();
            for (Map.Entry<String, List<TeachingEvaluationScore>> en : byName.entrySet()) {
                Double avg = en.getValue().stream().mapToInt(s -> n(s.getScore())).average().orElse(0);
                itemAverages.put(en.getKey(), BigDecimal.valueOf(avg).setScale(2, RoundingMode.HALF_UP));
            }
            dto.setItemAverages(itemAverages);
        }

        // 成绩侧
        if (!scores.isEmpty()) {
            Set<Long> courseIds = new HashSet<>();
            Set<Long> studentIds = new HashSet<>();
            BigDecimal sum = BigDecimal.ZERO;
            Integer scored = 0;
            Integer pass = 0;
            for (Score s : scores) {
                if (s.getCourseId() != null) {
                    courseIds.add(s.getCourseId());
                }
                if (s.getStudentUserId() != null) {
                    studentIds.add(s.getStudentUserId());
                }
                if (s.getTotalScore() != null) {
                    sum = sum.add(s.getTotalScore());
                    scored++;
                    if (s.getTotalScore().doubleValue() >= 60) {
                        pass++;
                    }
                }
            }
            dto.setCourseCount(courseIds.size());
            dto.setStudentCount(studentIds.size());
            if (scored > 0) {
                dto.setCourseAvgScore(sum.divide(BigDecimal.valueOf(scored), 2, RoundingMode.HALF_UP));
                dto.setCoursePassRate(BigDecimal.valueOf(pass).multiply(ScoreStats.HUNDRED)
                        .divide(BigDecimal.valueOf(scored), 2, RoundingMode.HALF_UP));
            }
        }
        return dto;
    }

    private Integer n(Integer v) {
        return v == null ? 0 : v;
    }

    // ==================== 富化 ====================

    private record NameMaps(Map<Long, String> courseNames, Map<Long, String> teacherNames,
                            Map<Long, String> semNames, Map<Long, String> templateNames) {
    }

    private NameMaps loadNameMaps(List<TeachingEvaluation> evals) {
        List<Long> courseIds = evals.stream().map(TeachingEvaluation::getCourseId)
                .filter(Objects::nonNull).distinct().toList();
        List<Long> campaignIds = evals.stream().map(TeachingEvaluation::getCampaignId)
                .filter(Objects::nonNull).distinct().toList();
        Set<Long> teacherIds = evals.stream().map(TeachingEvaluation::getTeacherId)
                .filter(Objects::nonNull).collect(Collectors.toSet());
        Set<Long> semesterIds = evals.stream().map(TeachingEvaluation::getSemesterId)
                .filter(Objects::nonNull).collect(Collectors.toSet());
        Set<Long> templateIds = evals.stream().map(TeachingEvaluation::getTemplateId)
                .filter(Objects::nonNull).collect(Collectors.toSet());

        Map<Long, String> courseNames = new HashMap<>();
        courseNames.putAll(courseInfoResolver.resolveCourseNameMap(courseIds));
        courseNames.putAll(courseInfoResolver.resolveCampaignNameMap(campaignIds));
        Map<Long, String> teacherNames = teacherNameResolver.namesByIds(teacherIds);
        Map<Long, String> semNames = semesterService.toNameMap(semesterIds);
        Map<Long, String> templateNames = templateIds.isEmpty() ? Map.of()
                : evaluationTemplateMapper.selectByIds(templateIds).stream()
                        .collect(Collectors.toMap(EvaluationTemplate::getId, EvaluationTemplate::getName, (a, b) -> a));
        return new NameMaps(courseNames, teacherNames, semNames, templateNames);
    }

    private TeachingEvaluationView toView(TeachingEvaluation e, NameMaps names) {
        TeachingEvaluationView v = new TeachingEvaluationView();
        v.setId(e.getId());
        v.setTeachInfoId(e.getTeachInfoId());
        v.setCourseId(e.getCourseId());
        v.setCourseName(e.getCampaignId() != null
                ? names.courseNames().get(e.getCampaignId())
                : names.courseNames().get(e.getCourseId()));
        v.setTeacherId(e.getTeacherId());
        v.setTeacherName(names.teacherNames().get(e.getTeacherId()));
        v.setSemesterId(e.getSemesterId());
        v.setSemesterName(names.semNames().get(e.getSemesterId()));
        v.setTemplateId(e.getTemplateId());
        v.setTemplateName(names.templateNames().get(e.getTemplateId()));
        v.setItems(loadScoreViews(e.getId()));
        v.setAvgScore(e.getAvgScore());
        v.setComment(e.getComment());
        v.setCreateTime(e.getCreateTime());
        return v;
    }

    private List<EvaluationScoreView> loadScoreViews(Long evaluationId) {
        return scoreDetailMapper.selectList(new LambdaQueryWrapper<TeachingEvaluationScore>()
                        .eq(TeachingEvaluationScore::getEvaluationId, evaluationId)
                        .orderByAsc(TeachingEvaluationScore::getId)).stream()
                .map(s -> {
                    EvaluationScoreView sv = new EvaluationScoreView();
                    sv.setItemId(s.getItemId());
                    sv.setItemName(s.getItemName());
                    sv.setMaxScore(s.getMaxScore());
                    sv.setScore(s.getScore());
                    return sv;
                })
                .toList();
    }
}
