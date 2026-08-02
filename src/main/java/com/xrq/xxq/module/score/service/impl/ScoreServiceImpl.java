package com.xrq.xxq.module.score.service.impl;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Arrays;
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
import com.xrq.xxq.module.clazz.entity.ClassName;
import com.xrq.xxq.module.clazz.mapper.ClassNameMapper;
import com.xrq.xxq.module.course.entity.Course;
import com.xrq.xxq.module.course.mapper.CourseMapper;
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
import com.xrq.xxq.module.notification.entity.NotificationTypeEnum;
import com.xrq.xxq.module.notification.service.NotificationService;
import com.xrq.xxq.module.selection.entity.SelectionClass;
import com.xrq.xxq.module.selection.entity.SelectionClassMember;
import com.xrq.xxq.module.selection.mapper.SelectionClassMapper;
import com.xrq.xxq.module.selection.mapper.SelectionClassMemberMapper;
import com.xrq.xxq.module.semester.entity.Semester;
import com.xrq.xxq.module.semester.service.SemesterService;
import com.xrq.xxq.module.teachinfo.entity.TeachInfo;
import com.xrq.xxq.module.teachinfo.mapper.TeachInfoMapper;
import com.xrq.xxq.module.user.entity.User;
import com.xrq.xxq.module.user.entity.user.Department;
import com.xrq.xxq.module.user.entity.user.Student;
import com.xrq.xxq.module.user.entity.user.Teacher;
import com.xrq.xxq.module.user.mapper.DepartmentMapper;
import com.xrq.xxq.module.user.mapper.StudentMapper;
import com.xrq.xxq.module.user.mapper.TeacherMapper;
import com.xrq.xxq.module.user.mapper.UserMapper;
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
    private final CourseMapper courseMapper;
    private final StudentMapper studentMapper;
    private final ClassNameMapper classNameMapper;
    private final UserMapper userMapper;
    private final TeacherMapper teacherMapper;
    private final DepartmentMapper departmentMapper;
    private final SelectionClassMapper selectionClassMapper;
    private final SelectionClassMemberMapper selectionClassMemberMapper;
    private final ScoreConfigMapper scoreConfigMapper;
    private final NotificationService notificationService;
    private final ExamMapper examMapper;
    private final SemesterService semesterService;

    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

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
            Teacher t = teacherMapper.selectOne(
                    new LambdaQueryWrapper<Teacher>().eq(Teacher::getUserId, userId));
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
            Teacher t = teacherMapper.selectOne(
                    new LambdaQueryWrapper<Teacher>().eq(Teacher::getUserId, userId));
            if (t != null && t.getId().equals(info.getTeacherId())) {
                return;
            }
            throw new BusinessException(403, "权限不足");
        }
        if (AuthFacade.USER_TYPE_DEPARTMENT.equals(userType)) {
            Department dept = departmentMapper.selectOne(
                    new LambdaQueryWrapper<Department>().eq(Department::getUserId, userId));
            if (dept != null && belongsToCollege(info.getClassName(), dept.getDepartmentName())) {
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
        List<Long> studentUserIds = resolveRosterUserIds(info, scopeClassName);
        if (studentUserIds.isEmpty()) {
            return List.of();
        }
        Map<Long, String> nameMap = userMapper.selectByIds(studentUserIds).stream()
                .collect(Collectors.toMap(User::getId, User::getName, (a, b) -> a));
        Map<Long, String> noMap = studentMapper.selectList(
                        new LambdaQueryWrapper<Student>().in(Student::getUserId, studentUserIds)).stream()
                .collect(Collectors.toMap(Student::getUserId, Student::getStudentNo, (a, b) -> a));
        return studentUserIds.stream().map(uid -> {
            ScoreRosterDto dto = new ScoreRosterDto();
            dto.setStudentUserId(uid);
            dto.setStudentName(nameMap.get(uid));
            dto.setStudentNo(noMap.get(uid));
            return dto;
        }).toList();
    }

    /**
     * 解析授课安排的学生 user.id 列表：公选课班走选课成员，常规班走班级名册。
     * <p>合班时若传入 scopeClassName（考试排考班级），则仅返回该班级学生，避免把未参加考试的学生纳入名单。
     */
    private List<Long> resolveRosterUserIds(TeachInfo info, String scopeClassName) {
        List<SelectionClass> selClasses = selectionClassMapper.selectList(
                new LambdaQueryWrapper<SelectionClass>().eq(SelectionClass::getTeachInfoId, info.getId()));
        if (!selClasses.isEmpty()) {
            List<Long> classIds = selClasses.stream().map(SelectionClass::getId).toList();
            return selectionClassMemberMapper.selectList(
                            new LambdaQueryWrapper<SelectionClassMember>().in(SelectionClassMember::getClassId, classIds))
                    .stream().map(SelectionClassMember::getStudentId).distinct().toList();
        }
        List<String> classNames = splitClassNames(info.getClassName());
        if (classNames.isEmpty()) {
            return List.of();
        }
        // 合班且指定考试排考班级：只取该班级名册，杜绝未参加考试的班级学生混入
        if (scopeClassName != null && !scopeClassName.isBlank()) {
            if (!classNames.contains(scopeClassName)) {
                throw new BusinessException(400, "考试排考班级不在该授课安排的合班范围内");
            }
            classNames = List.of(scopeClassName);
        }
        List<Long> classIds = classNameMapper.selectList(
                        new LambdaQueryWrapper<ClassName>().in(ClassName::getClassName, classNames)).stream()
                .map(ClassName::getId).toList();
        if (classIds.isEmpty()) {
            return List.of();
        }
        return studentMapper.selectList(
                        new LambdaQueryWrapper<Student>().in(Student::getClassId, classIds)).stream()
                .map(Student::getUserId).distinct().toList();
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

        // 合班时按考试排考班级限定可录入学生，避免给未参加考试的班级录入成绩
        String scopeClassName = resolveExamScopeClassName(request.getExamId(), info);
        Set<Long> allowedUserIds = scopeClassName != null
                ? new HashSet<>(resolveRosterUserIds(info, scopeClassName))
                : null;

        ScoreConfig config = scoreConfigMapper.selectOne(
                new LambdaQueryWrapper<ScoreConfig>().eq(ScoreConfig::getTeachInfoId, teachInfoId));
        if (config == null) {
            throw new BusinessException(400, "请先设置平时分占比");
        }
        int ratio = config.getRegularRatio();
        Course course = courseMapper.selectById(info.getCourseId());
        String courseName = course != null ? course.getCourseName() : "未知课程";

        List<Score> saved = new ArrayList<>();
        for (ScoreEntryRequest e : request.getEntries()) {
            if (e.getStudentUserId() == null) {
                throw new BusinessException(400, "学生ID不能为空");
            }
            if (allowedUserIds != null && !allowedUserIds.contains(e.getStudentUserId())) {
                throw new BusinessException(400, "存在未参加本次考试的学生，无法录入成绩");
            }
            validateScore(e.getRegularScore());
            validateScore(e.getFinalScore());
            BigDecimal total = computeTotal(e.getRegularScore(), e.getFinalScore(), ratio);
            String level = levelOf(total);

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

            // 录入即生效：新建且不及格者即时发送站内消息（失败不阻断录入）
            if (isNew && total != null && total.doubleValue() < 60) {
                try {
                    notificationService.sendToUser(e.getStudentUserId(), NotificationTypeEnum.GRADE,
                            "成绩通知",
                            "您的《" + courseName + "》总评成绩为 " + total.stripTrailingZeros().toPlainString()
                                    + " 分，未及格，请关注后续补考安排。");
                } catch (Exception ex) {
                    log.warn("不及格通知发送失败: studentUserId={}, teachInfoId={}", e.getStudentUserId(), teachInfoId, ex);
                }
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
        validateScore(entry.getRegularScore());
        validateScore(entry.getFinalScore());
        int ratio = g.getRegularRatio() != null ? g.getRegularRatio() : 0;
        BigDecimal total = computeTotal(entry.getRegularScore(), entry.getFinalScore(), ratio);
        g.setRegularScore(entry.getRegularScore());
        g.setFinalScore(entry.getFinalScore());
        g.setTotalScore(total);
        g.setScoreLevel(levelOf(total));
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
        Long effectiveSemesterId = semesterId;
        if (effectiveSemesterId == null) {
            Semester current = semesterService.getCurrent();
            if (current == null) {
                return List.of();
            }
            effectiveSemesterId = current.getId();
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
        assertCanEnterCourse(exam.getCourseId(), enterUserId, userType);
        ScoreTypeEnum gt = exam.getExamType() == ExamTypeEnum.MAKEUP ? ScoreTypeEnum.MAKEUP : ScoreTypeEnum.RETAKE;

        List<Score> saved = new ArrayList<>();
        for (MakeupScoreEntryRequest e : entries) {
            if (e.getStudentUserId() == null) {
                throw new BusinessException(400, "学生ID不能为空");
            }
            validateScore(e.getScore());
            // 查原不及格 REGULAR 成绩，确定 teachInfoId/teacherId 并关联
            Score original = baseMapper.selectOne(new LambdaQueryWrapper<Score>()
                    .eq(Score::getStudentUserId, e.getStudentUserId())
                    .eq(Score::getCourseId, exam.getCourseId())
                    .eq(Score::getScoreType, ScoreTypeEnum.REGULAR)
                    .orderByDesc(Score::getCreateTime).last("LIMIT 1"));
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
            g.setTeacherId(original.getTeacherId());
            g.setStudentUserId(e.getStudentUserId());
            g.setSemesterId(exam.getSemesterId());
            g.setRegularScore(null);
            g.setFinalScore(e.getScore());
            g.setRegularRatio(0);
            g.setTotalScore(e.getScore());
            g.setScoreLevel(levelOf(e.getScore()));
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
    private void assertCanEnterCourse(Long courseId, Long userId, String userType) {
        if (AuthFacade.USER_TYPE_ACADEMIC_ADMIN.equals(userType)) {
            return;
        }
        if (AuthFacade.USER_TYPE_TEACHER.equals(userType)) {
            Teacher t = teacherMapper.selectOne(
                    new LambdaQueryWrapper<Teacher>().eq(Teacher::getUserId, userId));
            if (t == null) {
                throw new BusinessException(403, "权限不足");
            }
            Long count = teachInfoMapper.selectCount(new LambdaQueryWrapper<TeachInfo>()
                    .eq(TeachInfo::getTeacherId, t.getId()).eq(TeachInfo::getCourseId, courseId));
            if (count == null || count == 0) {
                throw new BusinessException(403, "权限不足");
            }
            return;
        }
        throw new BusinessException(403, "权限不足");
    }

    // ==================== 统计 ====================

    @Override
    public List<ScoreStatisticsDto> statistics(Long courseId, String className, Long semesterId,
                                               Long userId, String userType) {
        List<Long> studentUserIds = resolveScopedStudentUserIds(userType, userId, className);
        LambdaQueryWrapper<Score> w = new LambdaQueryWrapper<Score>()
                .eq(Score::getScoreType, ScoreTypeEnum.REGULAR);
        if (courseId != null) {
            w.eq(Score::getCourseId, courseId);
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
        Map<Long, List<Score>> byCourse = grades.stream().collect(Collectors.groupingBy(Score::getCourseId));
        Map<Long, String> courseNameMap = courseMapper.selectByIds(byCourse.keySet()).stream()
                .collect(Collectors.toMap(Course::getId, Course::getCourseName, (a, b) -> a));
        List<ScoreStatisticsDto> result = new ArrayList<>();
        for (Map.Entry<Long, List<Score>> e : byCourse.entrySet()) {
            result.add(buildStats(e.getKey(), courseNameMap.get(e.getKey()), e.getValue()));
        }
        return result;
    }

    /**
     * 解析当前角色可见的学生 user.id 列表。
     * <p>返回 null 表示不过滤（教务且未指定班级 = 全部）；空 list 表示无可见学生。
     */
    private List<Long> resolveScopedStudentUserIds(String userType, Long userId, String className) {
        List<Long> classIds;
        if (AuthFacade.USER_TYPE_ACADEMIC_ADMIN.equals(userType)) {
            if (className == null || className.isBlank()) {
                return null;
            }
            classIds = classNameMapper.selectList(new LambdaQueryWrapper<ClassName>()
                            .eq(ClassName::getClassName, className)).stream()
                    .map(ClassName::getId).toList();
        } else if (AuthFacade.USER_TYPE_DEPARTMENT.equals(userType)) {
            Department dept = departmentMapper.selectOne(
                    new LambdaQueryWrapper<Department>().eq(Department::getUserId, userId));
            if (dept == null) {
                return List.of();
            }
            classIds = classNameMapper.selectList(new LambdaQueryWrapper<ClassName>()
                            .eq(ClassName::getCollege, dept.getDepartmentName())).stream()
                    .map(ClassName::getId).toList();
            if (className != null && !className.isBlank()) {
                List<Long> nameIds = classNameMapper.selectList(new LambdaQueryWrapper<ClassName>()
                                .eq(ClassName::getClassName, className)).stream()
                        .map(ClassName::getId).toList();
                classIds = classIds.stream().filter(nameIds::contains).toList();
            }
        } else {
            throw new BusinessException(403, "权限不足");
        }
        if (classIds.isEmpty()) {
            return List.of();
        }
        return studentMapper.selectList(new LambdaQueryWrapper<Student>().in(Student::getClassId, classIds))
                .stream().map(Student::getUserId).toList();
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
                    .multiply(HUNDRED).divide(BigDecimal.valueOf(scored), 2, RoundingMode.HALF_UP));
        }
        return dto;
    }

    // ==================== 富化与计算 ====================

    private List<ScoreView> toViews(List<Score> grades) {
        if (grades.isEmpty()) {
            return List.of();
        }
        List<Long> courseIds = grades.stream().map(Score::getCourseId).filter(Objects::nonNull).distinct().toList();
        List<Long> teacherIds = grades.stream().map(Score::getTeacherId).filter(Objects::nonNull).distinct().toList();
        List<Long> studentUserIds = grades.stream().map(Score::getStudentUserId).distinct().toList();

        Map<Long, String> courseNameMap = courseIds.isEmpty() ? Map.of()
                : courseMapper.selectByIds(courseIds).stream()
                        .collect(Collectors.toMap(Course::getId, Course::getCourseName, (a, b) -> a));

        Map<Long, Long> teacherIdToUserId = teacherIds.isEmpty() ? Map.of()
                : teacherMapper.selectByIds(teacherIds).stream()
                        .collect(Collectors.toMap(Teacher::getId, Teacher::getUserId, (a, b) -> a));

        List<Long> allUserIds = new ArrayList<>(studentUserIds);
        allUserIds.addAll(teacherIdToUserId.values());
        Map<Long, String> userNameMap = allUserIds.isEmpty() ? Map.of()
                : userMapper.selectByIds(allUserIds).stream()
                        .collect(Collectors.toMap(User::getId, User::getName, (a, b) -> a));

        Map<Long, String> studentNoMap = studentUserIds.isEmpty() ? Map.of()
                : studentMapper.selectList(new LambdaQueryWrapper<Student>().in(Student::getUserId, studentUserIds))
                        .stream().collect(Collectors.toMap(Student::getUserId, Student::getStudentNo, (a, b) -> a));

        return grades.stream().map(g -> {
            ScoreView v = new ScoreView();
            v.setId(g.getId());
            v.setTeachInfoId(g.getTeachInfoId());
            v.setCourseId(g.getCourseId());
            v.setCourseName(courseNameMap.get(g.getCourseId()));
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

    private void validateScore(BigDecimal s) {
        if (s == null) {
            throw new BusinessException(400, "成绩不能为空");
        }
        double v = s.doubleValue();
        if (v < 0 || v > 100) {
            throw new BusinessException(400, "成绩必须在 0-100 之间");
        }
    }

    private BigDecimal computeTotal(BigDecimal regular, BigDecimal finale, int ratio) {
        BigDecimal ratioBd = BigDecimal.valueOf(ratio);
        BigDecimal regularPart = regular.multiply(ratioBd).divide(HUNDRED, 2, RoundingMode.HALF_UP);
        BigDecimal finalPart = finale.multiply(HUNDRED.subtract(ratioBd)).divide(HUNDRED, 2, RoundingMode.HALF_UP);
        return regularPart.add(finalPart);
    }

    private String levelOf(BigDecimal total) {
        if (total == null) {
            return null;
        }
        double t = total.doubleValue();
        if (t >= 90) return "优";
        if (t >= 80) return "良";
        if (t >= 70) return "中";
        if (t >= 60) return "及格";
        return "不及格";
    }

    private List<String> splitClassNames(String csv) {
        if (csv == null || csv.isBlank()) {
            return List.of();
        }
        return Arrays.stream(csv.split(",")).map(String::strip).filter(s -> !s.isEmpty()).distinct().toList();
    }

    private boolean belongsToCollege(String classNamesCsv, String college) {
        List<String> names = splitClassNames(classNamesCsv);
        if (names.isEmpty()) {
            return false;
        }
        return classNameMapper.selectList(new LambdaQueryWrapper<ClassName>().in(ClassName::getClassName, names))
                .stream().anyMatch(c -> college != null && college.equals(c.getCollege()));
    }
}
