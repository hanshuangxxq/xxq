package com.xrq.xxq.module.score.service.impl;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.xrq.xxq.common.BusinessException;
import com.xrq.xxq.module.analysis.util.ScoreStats;
import com.xrq.xxq.module.clazz.entity.ClassName;
import com.xrq.xxq.module.clazz.mapper.ClassNameMapper;
import com.xrq.xxq.module.clazz.util.ClassNameUtil;
import com.xrq.xxq.module.course.service.CourseInfoResolver;
import com.xrq.xxq.module.course.util.CourseRouting;
import com.xrq.xxq.module.exam.dto.MakeupScoreEntryRequest;
import com.xrq.xxq.module.exam.entity.Exam;
import com.xrq.xxq.module.exam.entity.ExamTypeEnum;
import com.xrq.xxq.module.exam.mapper.ExamMapper;
import com.xrq.xxq.module.score.dto.ScoreBatchRequest;
import com.xrq.xxq.module.score.dto.ScoreEntryRequest;
import com.xrq.xxq.module.score.dto.ScoreRosterDto;
import com.xrq.xxq.module.score.dto.ScoreStatisticsDto;
import com.xrq.xxq.module.score.dto.ScoreView;
import com.xrq.xxq.module.score.entity.Score;
import com.xrq.xxq.module.score.entity.ScoreConfig;
import com.xrq.xxq.module.score.entity.ScoreTypeEnum;
import com.xrq.xxq.module.score.mapper.ScoreConfigMapper;
import com.xrq.xxq.module.score.mapper.ScoreMapper;
import com.xrq.xxq.module.score.service.ScoreService;
import com.xrq.xxq.module.semester.mapper.SemesterMapper;
import org.springframework.context.ApplicationEventPublisher;
import com.xrq.xxq.common.event.GradeFailedEvent;
import com.xrq.xxq.module.semester.entity.Semester;
import com.xrq.xxq.module.semester.service.SemesterService;
import com.xrq.xxq.module.teachinfo.entity.TeachInfo;
import com.xrq.xxq.module.teachinfo.mapper.TeachInfoMapper;
import com.xrq.xxq.module.user.entity.User;
import com.xrq.xxq.module.user.entity.user.Department;
import com.xrq.xxq.module.user.entity.user.Teacher;
import com.xrq.xxq.module.user.mapper.DepartmentMapper;
import com.xrq.xxq.module.user.mapper.StudentMapper;
import com.xrq.xxq.module.user.mapper.TeacherMapper;
import com.xrq.xxq.module.user.mapper.UserMapper;
import com.xrq.xxq.util.ReferenceValidator;
import com.xrq.xxq.util.StudentEnrollmentResolver;
import com.xrq.xxq.util.StudentScopeResolver;
import com.xrq.xxq.util.auth.AuthFacade;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 成绩服务实现：录入即生效、总评/等级派生、不及格自动通知、按角色 scope 查询。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ScoreServiceImpl extends ServiceImpl<ScoreMapper, Score> implements ScoreService {

    private final TeachInfoMapper teachInfoMapper;
    private final CourseInfoResolver courseInfoResolver;
    private final StudentMapper studentMapper;
    private final ClassNameMapper classNameMapper;
    private final UserMapper userMapper;
    private final TeacherMapper teacherMapper;
    private final DepartmentMapper departmentMapper;
    private final ScoreConfigMapper scoreConfigMapper;
    private final ApplicationEventPublisher eventPublisher;
    private final ExamMapper examMapper;
    private final SemesterService semesterService;
    private final SemesterMapper semesterMapper;
    private final ReferenceValidator referenceValidator;
    private final StudentScopeResolver studentScopeResolver;
    private final StudentEnrollmentResolver enrollmentResolver;

    // ==================== 权限 ====================

    @Override
    public void assertCanEnterTeachInfo(Long teachInfoId, Long userId, String userType) {
        TeachInfo info = teachInfoMapper.selectById(teachInfoId);
        if (info == null) {
            throw new BusinessException(404, "授课安排不存在");
        }
        assertCanEnter(info, userId, userType);
    }

    private void assertCanEnter(TeachInfo info, Long userId, String userType) {
        if (AuthFacade.USER_TYPE_ACADEMIC_ADMIN.equals(userType)) {
            return;
        }
        if (AuthFacade.USER_TYPE_TEACHER.equals(userType)) {
            Teacher t = teacherMapper.findByUserId(userId);
            if (t == null || !t.getId().equals(info.getTeacherId())) {
                throw new BusinessException(403, "权限不足");
            }
            return;
        }
        throw new BusinessException(403, "权限不足");
    }

    private void assertCanView(TeachInfo info, Long userId, String userType) {
        if (AuthFacade.USER_TYPE_ACADEMIC_ADMIN.equals(userType)) {
            return;
        }
        if (AuthFacade.USER_TYPE_TEACHER.equals(userType)) {
            Teacher t = teacherMapper.findByUserId(userId);
            if (t != null && t.getId().equals(info.getTeacherId())) {
                return;
            }
            throw new BusinessException(403, "权限不足");
        }
        if (AuthFacade.USER_TYPE_DEPARTMENT.equals(userType)) {
            Department dept = departmentMapper.findByUserId(userId);
            if (dept != null && belongsToCollege(info.getClassName(), dept.getCollegeId())) {
                return;
            }
            throw new BusinessException(403, "权限不足");
        }
        throw new BusinessException(403, "权限不足");
    }

    // ==================== 名单 ====================

    @Override
    public List<ScoreRosterDto> roster(Long teachInfoId, Long examId) {
        TeachInfo info = teachInfoMapper.selectById(teachInfoId);
        if (info == null) {
            throw new BusinessException(404, "授课安排不存在");
        }
        String scopeClassName = resolveExamScopeClassName(examId, info);
        List<Long> studentUserIds = enrollmentResolver.rosterUserIds(info.getId(), scopeClassName);
        if (studentUserIds.isEmpty()) {
            return List.of();
        }
        Map<Long, String> nameMap = userMapper.toNameMap(studentUserIds);
        Map<Long, String> noMap = studentMapper.toStudentNoMap(studentUserIds);
        return studentUserIds.stream().map(uid -> {
            ScoreRosterDto dto = new ScoreRosterDto();
            dto.setStudentUserId(uid);
            dto.setStudentName(nameMap.get(uid));
            dto.setStudentNo(noMap.get(uid));
            return dto;
        }).toList();
    }

    /**
     * 解析考试排考班级（单班级名）用于合班名单过滤。
     * <p>examId 为 null 时返回 null（不过滤，返回合班全部学生，兼容平时分录入等无考试场景）；
     * 非 null 时校验考试归属该授课安排，返回其 className（期末/期中为单班级名；补考/重修不绑授课安排，会被归属校验拦截）。
     */
    private String resolveExamScopeClassName(Long examId, TeachInfo info) {
        if (examId == null) {
            return null;
        }
        Exam exam = examMapper.selectById(examId);
        if (exam == null) {
            throw new BusinessException(404, "考试不存在");
        }
        if (!info.getId().equals(exam.getTeachInfoId())) {
            throw new BusinessException(400, "考试与授课安排不匹配");
        }
        return exam.getClassName();
    }

    // ==================== 录入 ====================

    @Override
    @Transactional
    public List<ScoreView> saveScores(ScoreBatchRequest request, Long enterUserId, String userType) {
        if (request.getEntries() == null || request.getEntries().isEmpty()) {
            throw new BusinessException(400, "成绩列表不能为空");
        }
        Long teachInfoId = request.getTeachInfoId();
        TeachInfo info = teachInfoMapper.selectById(teachInfoId);
        if (info == null) {
            throw new BusinessException(404, "授课安排不存在");
        }
        assertCanEnter(info, enterUserId, userType);

        // 外键存在性校验（授课安排已由上方 selectById 校验）
        referenceValidator.requireCourseRef(info.getCourseId(), info.getCampaignId());
        referenceValidator.requireExists(semesterMapper, info.getSemesterId(), "学期");
        referenceValidator.requireExists(teacherMapper, info.getTeacherId(), "教师");

        // 合班时按考试排考班级限定可录入学生，避免给未参加考试的班级录入成绩
        String scopeClassName = resolveExamScopeClassName(request.getExamId(), info);
        Set<Long> allowedUserIds = scopeClassName != null
                ? new HashSet<>(enrollmentResolver.rosterUserIds(info.getId(), scopeClassName))
                : null;

        ScoreConfig config = scoreConfigMapper.selectOne(
                new LambdaQueryWrapper<ScoreConfig>().eq(ScoreConfig::getTeachInfoId, teachInfoId));
        if (config == null) {
            throw new BusinessException(400, "请先设置平时分占比");
        }
        int ratio = config.getRegularRatio();
        CourseInfoResolver.CourseInfo courseInfo = courseInfoResolver.resolveOne(
                info.getCourseId(), info.getCampaignId());
        String courseName = courseInfo != null && courseInfo.getCourseName() != null
                ? courseInfo.getCourseName() : "未知课程";

        List<Score> saved = new ArrayList<>();
        // 批量校验学生用户存在性
        List<Long> entryStudentIds = request.getEntries().stream()
                .map(ScoreEntryRequest::getStudentUserId)
                .filter(Objects::nonNull)
                .distinct().toList();
        if (!entryStudentIds.isEmpty()) {
            List<User> students = userMapper.selectByIds(entryStudentIds);
            if (students.size() != entryStudentIds.size()) {
                throw new BusinessException(400, "存在无效的学生用户");
            }
        }
        for (ScoreEntryRequest e : request.getEntries()) {
            if (e.getStudentUserId() == null) {
                throw new BusinessException(400, "学生ID不能为空");
            }
            if (allowedUserIds != null && !allowedUserIds.contains(e.getStudentUserId())) {
                throw new BusinessException(400, "存在未参加本次考试的学生，无法录入成绩");
            }
            ScoreStats.validateScore(e.getRegularScore());
            ScoreStats.validateScore(e.getFinalScore());
            BigDecimal total = computeTotal(e.getRegularScore(), e.getFinalScore(), ratio);
            String level = ScoreStats.levelOf(total);

            Score exist = baseMapper.selectOne(new LambdaQueryWrapper<Score>()
                    .eq(Score::getTeachInfoId, teachInfoId)
                    .eq(Score::getStudentUserId, e.getStudentUserId())
                    .eq(Score::getScoreType, ScoreTypeEnum.REGULAR));
            boolean isNew = (exist == null);
            if (!isNew && Integer.valueOf(1).equals(exist.getLocked())) {
                throw new BusinessException(409, "成绩已锁定，不可修改");
            }
            Score g = isNew ? new Score() : exist;
            g.setTeachInfoId(teachInfoId);
            g.setCourseId(info.getCourseId());
            g.setCampaignId(info.getCampaignId());
            g.setTeacherId(info.getTeacherId());
            g.setStudentUserId(e.getStudentUserId());
            g.setSemesterId(info.getSemesterId());
            g.setRegularScore(e.getRegularScore());
            g.setFinalScore(e.getFinalScore());
            g.setRegularRatio(ratio);
            g.setTotalScore(total);
            g.setScoreLevel(level);
            g.setScoreType(ScoreTypeEnum.REGULAR);
            g.setEnterUserId(enterUserId);
            if (isNew) {
                baseMapper.insert(g);
            } else {
                baseMapper.updateById(g);
            }
            saved.add(g);

            // 录入即生效：新建且不及格者发布事件，由通知监听器 AFTER_COMMIT 异步发送
            if (isNew && ScoreStats.isFail(total)) {
                eventPublisher.publishEvent(new GradeFailedEvent(
                        e.getStudentUserId(), courseName, total.stripTrailingZeros().toPlainString()));
            }
        }
        return toViews(saved);
    }

    @Override
    @Transactional
    public ScoreView updateScore(Long scoreId, ScoreEntryRequest entry, Long enterUserId, String userType) {
        Score g = baseMapper.selectById(scoreId);
        if (g == null) {
            throw new BusinessException(404, "成绩记录不存在");
        }
        if (Integer.valueOf(1).equals(g.getLocked())) {
            throw new BusinessException(409, "成绩已锁定，不可修改");
        }
        TeachInfo info = teachInfoMapper.selectById(g.getTeachInfoId());
        if (info == null) {
            throw new BusinessException(404, "授课安排不存在");
        }
        assertCanEnter(info, enterUserId, userType);
        // 外键存在性校验
        referenceValidator.requireCourseRef(g.getCourseId(), g.getCampaignId());
        referenceValidator.requireExists(userMapper, g.getStudentUserId(), "学生");
        referenceValidator.requireExists(semesterMapper, g.getSemesterId(), "学期");
        referenceValidator.requireExists(teacherMapper, g.getTeacherId(), "教师");
        referenceValidator.requireExists(baseMapper, g.getOriginalScoreId(), "原成绩");
        // 唯一性预检（排除自身，DB 唯一约束已移至应用层）
        Long dup = baseMapper.selectCount(new LambdaQueryWrapper<Score>()
                .eq(Score::getTeachInfoId, g.getTeachInfoId())
                .eq(Score::getStudentUserId, g.getStudentUserId())
                .eq(Score::getScoreType, g.getScoreType())
                .ne(Score::getId, scoreId));
        if (dup != null && dup > 0) {
            throw new BusinessException(409, "成绩记录已存在");
        }
        ScoreStats.validateScore(entry.getRegularScore());
        ScoreStats.validateScore(entry.getFinalScore());
        int ratio = g.getRegularRatio() != null ? g.getRegularRatio() : 0;
        BigDecimal total = computeTotal(entry.getRegularScore(), entry.getFinalScore(), ratio);
        g.setRegularScore(entry.getRegularScore());
        g.setFinalScore(entry.getFinalScore());
        g.setTotalScore(total);
        g.setScoreLevel(ScoreStats.levelOf(total));
        g.setEnterUserId(enterUserId);
        baseMapper.updateById(g);
        return toViews(List.of(g)).getFirst();
    }

    // ==================== 查询 ====================

    @Override
    public List<ScoreView> listByTeachInfo(Long teachInfoId, Long userId, String userType) {
        TeachInfo info = teachInfoMapper.selectById(teachInfoId);
        if (info == null) {
            throw new BusinessException(404, "授课安排不存在");
        }
        assertCanView(info, userId, userType);
        List<Score> grades = baseMapper.selectList(new LambdaQueryWrapper<Score>()
                .eq(Score::getTeachInfoId, teachInfoId)
                .eq(Score::getScoreType, ScoreTypeEnum.REGULAR)
                .orderByAsc(Score::getStudentUserId));
        return toViews(grades);
    }

    @Override
    public List<ScoreView> listMyScores(Long studentUserId, Long semesterId) {
        // 默认查当前学期；前端需查其它学期时显式传 semesterId
        Long effectiveSemesterId = semesterService.resolveOrDefault(semesterId);
        if (effectiveSemesterId == null) {
            return List.of();
        }
        List<Score> grades = baseMapper.selectList(new LambdaQueryWrapper<Score>()
                .eq(Score::getStudentUserId, studentUserId)
                .eq(Score::getSemesterId, effectiveSemesterId)
                .orderByAsc(Score::getCourseId)
                .orderByDesc(Score::getCreateTime));
        return toViews(grades);
    }

    @Override
    public List<Semester> listMyScoreSemesters(Long studentUserId) {
        // 仅返回该学生有成绩记录的学期（去重），按学期 id 倒序
        List<Score> scores = baseMapper.selectList(new LambdaQueryWrapper<Score>()
                .select(Score::getSemesterId)
                .eq(Score::getStudentUserId, studentUserId));
        Set<Long> semesterIds = scores.stream()
                .map(Score::getSemesterId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        if (semesterIds.isEmpty()) {
            return List.of();
        }
        return semesterService.listByIds(semesterIds).stream()
                .sorted(Comparator.comparing(Semester::getId).reversed())
                .toList();
    }

    @Override
    @Transactional
    public List<ScoreView> enterMakeupScore(Long examId, List<MakeupScoreEntryRequest> entries,
                                            Long enterUserId, String userType) {
        if (entries == null || entries.isEmpty()) {
            throw new BusinessException(400, "成绩列表不能为空");
        }
        Exam exam = examMapper.selectById(examId);
        if (exam == null) {
            throw new BusinessException(404, "考试不存在");
        }
        if (exam.getExamType() != ExamTypeEnum.MAKEUP && exam.getExamType() != ExamTypeEnum.RETAKE) {
            throw new BusinessException(400, "仅补考/重修考试可录入补考成绩");
        }
        assertCanEnterCourse(exam.getCourseId(), exam.getCampaignId(), enterUserId, userType);
        ScoreTypeEnum gt = exam.getExamType() == ExamTypeEnum.MAKEUP ? ScoreTypeEnum.MAKEUP : ScoreTypeEnum.RETAKE;

        List<Score> saved = new ArrayList<>();
        for (MakeupScoreEntryRequest e : entries) {
            if (e.getStudentUserId() == null) {
                throw new BusinessException(400, "学生ID不能为空");
            }
            ScoreStats.validateScore(e.getScore());
            // 查原不及格 REGULAR 成绩，确定 teachInfoId/teacherId 并关联
            // 公选课按 campaignId 关联（courseId 为 null）
            LambdaQueryWrapper<Score> ow = new LambdaQueryWrapper<Score>()
                    .eq(Score::getStudentUserId, e.getStudentUserId())
                    .eq(Score::getScoreType, ScoreTypeEnum.REGULAR);
            if (exam.getCampaignId() != null) {
                ow.eq(Score::getCampaignId, exam.getCampaignId());
            } else {
                ow.eq(Score::getCourseId, exam.getCourseId());
            }
            Score original = baseMapper.selectOne(ow.orderByDesc(Score::getCreateTime).last("LIMIT 1"));
            if (original == null) {
                throw new BusinessException(404, "未找到学生 userId=" + e.getStudentUserId() + " 的原成绩记录");
            }
            Score exist = baseMapper.selectOne(new LambdaQueryWrapper<Score>()
                    .eq(Score::getTeachInfoId, original.getTeachInfoId())
                    .eq(Score::getStudentUserId, e.getStudentUserId())
                    .eq(Score::getScoreType, gt));
            Score g = exist != null ? exist : new Score();
            g.setTeachInfoId(original.getTeachInfoId());
            g.setCourseId(exam.getCourseId());
            g.setCampaignId(exam.getCampaignId());
            g.setTeacherId(original.getTeacherId());
            g.setStudentUserId(e.getStudentUserId());
            g.setSemesterId(exam.getSemesterId());
            g.setRegularScore(null);
            g.setFinalScore(e.getScore());
            g.setRegularRatio(0);
            g.setTotalScore(e.getScore());
            g.setScoreLevel(ScoreStats.levelOf(e.getScore()));
            g.setScoreType(gt);
            g.setOriginalScoreId(original.getId());
            g.setEnterUserId(enterUserId);
            if (exist == null) {
                baseMapper.insert(g);
            } else {
                baseMapper.updateById(g);
            }
            saved.add(g);
        }
        return toViews(saved);
    }

    /** 校验当前用户是否可录入该课程的成绩（教务或该课程的任课教师）。 */
    private void assertCanEnterCourse(Long courseId, Long campaignId, Long userId, String userType) {
        if (AuthFacade.USER_TYPE_ACADEMIC_ADMIN.equals(userType)) {
            return;
        }
        if (AuthFacade.USER_TYPE_TEACHER.equals(userType)) {
            Teacher t = teacherMapper.findByUserId(userId);
            if (t == null) {
                throw new BusinessException(403, "权限不足");
            }
            LambdaQueryWrapper<TeachInfo> w = new LambdaQueryWrapper<TeachInfo>()
                    .eq(TeachInfo::getTeacherId, t.getId());
            if (campaignId != null) {
                w.eq(TeachInfo::getCampaignId, campaignId);
            } else {
                w.eq(TeachInfo::getCourseId, courseId);
            }
            Long count = teachInfoMapper.selectCount(w);
            if (count == null || count == 0) {
                throw new BusinessException(403, "权限不足");
            }
            return;
        }
        throw new BusinessException(403, "权限不足");
    }

    // ==================== 统计 ====================

    @Override
    public List<ScoreStatisticsDto> statistics(Long courseId, String source, String className, Long semesterId,
                                               Long userId, String userType) {
        List<Long> studentUserIds = studentScopeResolver.resolveScopedStudentUserIds(userType, userId, className);
        LambdaQueryWrapper<Score> w = new LambdaQueryWrapper<Score>()
                .eq(Score::getScoreType, ScoreTypeEnum.REGULAR);
        if (courseId != null) {
            CourseRouting.apply(w, courseId, source, Score::getCampaignId, Score::getCourseId);
        }
        if (semesterId != null) {
            w.eq(Score::getSemesterId, semesterId);
        }
        if (studentUserIds != null) {
            if (studentUserIds.isEmpty()) {
                return List.of();
            }
            w.in(Score::getStudentUserId, studentUserIds);
        }
        List<Score> grades = baseMapper.selectList(w);
        if (grades.isEmpty()) {
            return List.of();
        }
        // 分组键加前缀避免 course.id 与 campaign.id 数值空间重叠：公选课按 campaignId 分组
        Map<String, List<Score>> byKey = grades.stream().collect(Collectors.groupingBy(
                g -> g.getCampaignId() != null ? "C" + g.getCampaignId() : "K" + g.getCourseId()));
        Map<Long, String> courseNameByCourse = courseInfoResolver.resolveCourseNameMap(
                grades.stream().map(Score::getCourseId).filter(Objects::nonNull).distinct().toList());
        Map<Long, String> courseNameByCampaign = courseInfoResolver.resolveCampaignNameMap(
                grades.stream().map(Score::getCampaignId).filter(Objects::nonNull).distinct().toList());
        List<ScoreStatisticsDto> result = new ArrayList<>();
        for (Map.Entry<String, List<Score>> e : byKey.entrySet()) {
            List<Score> groupScores = e.getValue();
            Score sample = groupScores.get(0);
            Long statCourseId;
            String courseName;
            if (sample.getCampaignId() != null) {
                statCourseId = sample.getCampaignId();
                courseName = courseNameByCampaign.get(sample.getCampaignId());
            } else {
                statCourseId = sample.getCourseId();
                courseName = courseNameByCourse.get(sample.getCourseId());
            }
            result.add(buildStats(statCourseId, courseName, groupScores));
        }
        return result;
    }

    private ScoreStatisticsDto buildStats(Long courseId, String courseName, List<Score> grades) {
        ScoreStatisticsDto dto = new ScoreStatisticsDto();
        dto.setCourseId(courseId);
        dto.setCourseName(courseName);
        dto.setTotalCount(grades.size());
        int exc = 0, good = 0, med = 0, pass = 0, fail = 0;
        BigDecimal sum = BigDecimal.ZERO;
        int scored = 0;
        BigDecimal max = null, min = null;
        for (Score g : grades) {
            BigDecimal t = g.getTotalScore();
            String lv = g.getScoreLevel();
            if ("优".equals(lv)) exc++;
            else if ("良".equals(lv)) good++;
            else if ("中".equals(lv)) med++;
            else if ("及格".equals(lv)) pass++;
            else if ("不及格".equals(lv)) fail++;
            if (t != null) {
                sum = sum.add(t);
                scored++;
                if (max == null || t.compareTo(max) > 0) max = t;
                if (min == null || t.compareTo(min) < 0) min = t;
            }
        }
        dto.setExcellentCount(exc);
        dto.setGoodCount(good);
        dto.setMediumCount(med);
        dto.setPassCount(pass);
        dto.setFailCount(fail);
        dto.setMaxScore(max);
        dto.setMinScore(min);
        if (scored > 0) {
            dto.setAvgScore(sum.divide(BigDecimal.valueOf(scored), 2, RoundingMode.HALF_UP));
            dto.setPassRate(BigDecimal.valueOf(exc + good + med + pass)
                    .multiply(ScoreStats.HUNDRED).divide(BigDecimal.valueOf(scored), 2, RoundingMode.HALF_UP));
        }
        return dto;
    }

    // ==================== 富化与计算 ====================

    private List<ScoreView> toViews(List<Score> grades) {
        if (grades.isEmpty()) {
            return List.of();
        }
        List<Long> courseIds = grades.stream().map(Score::getCourseId).filter(Objects::nonNull).distinct().toList();
        List<Long> campaignIds = grades.stream().map(Score::getCampaignId).filter(Objects::nonNull).distinct().toList();
        List<Long> teacherIds = grades.stream().map(Score::getTeacherId).filter(Objects::nonNull).distinct().toList();
        List<Long> studentUserIds = grades.stream().map(Score::getStudentUserId).distinct().toList();

        // 课程名：常规课走 course 表，公选课走 selection_campaign
        Map<Long, String> courseNameByCourse = courseInfoResolver.resolveCourseNameMap(courseIds);
        Map<Long, String> courseNameByCampaign = courseInfoResolver.resolveCampaignNameMap(campaignIds);

        Map<Long, Long> teacherIdToUserId = teacherIds.isEmpty() ? Map.of()
                : teacherMapper.selectByIds(teacherIds).stream()
                        .collect(Collectors.toMap(Teacher::getId, Teacher::getUserId, (a, b) -> a));

        List<Long> allUserIds = new ArrayList<>(studentUserIds);
        allUserIds.addAll(teacherIdToUserId.values());
        Map<Long, String> userNameMap = userMapper.toNameMap(allUserIds);

        Map<Long, String> studentNoMap = studentMapper.toStudentNoMap(studentUserIds);

        return grades.stream().map(g -> {
            ScoreView v = new ScoreView();
            v.setId(g.getId());
            v.setTeachInfoId(g.getTeachInfoId());
            v.setCourseId(g.getCourseId());
            v.setCourseName(g.getCampaignId() != null
                    ? courseNameByCampaign.get(g.getCampaignId())
                    : courseNameByCourse.get(g.getCourseId()));
            v.setTeacherId(g.getTeacherId());
            Long tuid = teacherIdToUserId.get(g.getTeacherId());
            v.setTeacherName(tuid != null ? userNameMap.get(tuid) : null);
            v.setStudentUserId(g.getStudentUserId());
            v.setStudentName(userNameMap.get(g.getStudentUserId()));
            v.setStudentNo(studentNoMap.get(g.getStudentUserId()));
            v.setSemesterId(g.getSemesterId());
            v.setRegularScore(g.getRegularScore());
            v.setFinalScore(g.getFinalScore());
            v.setRegularRatio(g.getRegularRatio());
            v.setTotalScore(g.getTotalScore());
            v.setScoreLevel(g.getScoreLevel());
            v.setScoreType(g.getScoreType());
            v.setLocked(g.getLocked());
            v.setCreateTime(g.getCreateTime());
            return v;
        }).toList();
    }

    private BigDecimal computeTotal(BigDecimal regular, BigDecimal finale, int ratio) {
        BigDecimal ratioBd = BigDecimal.valueOf(ratio);
        BigDecimal regularPart = regular.multiply(ratioBd).divide(ScoreStats.HUNDRED, 2, RoundingMode.HALF_UP);
        BigDecimal finalPart = finale.multiply(ScoreStats.HUNDRED.subtract(ratioBd)).divide(ScoreStats.HUNDRED, 2, RoundingMode.HALF_UP);
        return regularPart.add(finalPart);
    }

    private boolean belongsToCollege(String classNamesCsv, Long collegeId) {
        List<String> names = ClassNameUtil.splitClassNames(classNamesCsv);
        if (names.isEmpty() || collegeId == null) {
            return false;
        }
        return classNameMapper.selectList(new LambdaQueryWrapper<ClassName>().in(ClassName::getClassName, names))
                .stream().anyMatch(c -> collegeId.equals(c.getCollegeId()));
    }
}
