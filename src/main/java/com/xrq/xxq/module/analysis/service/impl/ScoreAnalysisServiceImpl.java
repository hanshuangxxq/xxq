package com.xrq.xxq.module.analysis.service.impl;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.xrq.xxq.common.BusinessException;
import com.xrq.xxq.module.analysis.dto.ScoreComparisonDto;
import com.xrq.xxq.module.analysis.dto.ScoreComparisonDto.ClassPoint;
import com.xrq.xxq.module.analysis.dto.ScoreDistributionDto;
import com.xrq.xxq.module.analysis.dto.ScoreTrendDto;
import com.xrq.xxq.module.analysis.dto.ScoreTrendDto.SemesterPoint;
import com.xrq.xxq.module.analysis.service.ScoreAnalysisService;
import com.xrq.xxq.module.analysis.util.StudentScopeResolver;
import com.xrq.xxq.module.clazz.entity.ClassName;
import com.xrq.xxq.module.clazz.mapper.ClassNameMapper;
import com.xrq.xxq.module.course.entity.Course;
import com.xrq.xxq.module.course.mapper.CourseMapper;
import com.xrq.xxq.module.course.service.CourseInfoResolver;
import com.xrq.xxq.module.score.entity.Score;
import com.xrq.xxq.module.score.entity.ScoreTypeEnum;
import com.xrq.xxq.module.score.mapper.ScoreMapper;
import com.xrq.xxq.module.semester.entity.Semester;
import com.xrq.xxq.module.semester.service.SemesterService;
import com.xrq.xxq.module.user.entity.user.Student;
import com.xrq.xxq.module.user.mapper.StudentMapper;
import com.xrq.xxq.util.auth.AuthFacade;

import lombok.RequiredArgsConstructor;

/**
 * 成绩分析服务实现：分数段分布、跨学期趋势、班级横向对比。
 */
@Service
@RequiredArgsConstructor
public class ScoreAnalysisServiceImpl implements ScoreAnalysisService {

    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    private final ScoreMapper scoreMapper;
    private final CourseMapper courseMapper;
    private final CourseInfoResolver courseInfoResolver;
    private final StudentMapper studentMapper;
    private final ClassNameMapper classNameMapper;
    private final SemesterService semesterService;
    private final StudentScopeResolver scopeResolver;

    @Override
    public ScoreDistributionDto distribution(Long courseId, String source, String className, Long semesterId,
                                             Long callerUserId, String callerUserType) {
        if (courseId == null) {
            throw new BusinessException(400, "课程ID不能为空");
        }
        Long semId = resolveSemesterId(semesterId);
        List<Long> studentUserIds = resolveScopeForCourse(courseId, source, className, callerUserId, callerUserType);
        if (studentUserIds != null && studentUserIds.isEmpty()) {
            return emptyDistribution(courseId);
        }
        LambdaQueryWrapper<Score> w = new LambdaQueryWrapper<Score>()
                .eq(Score::getScoreType, ScoreTypeEnum.REGULAR);
        if (Course.SOURCE_SELECTION_CAMPAIGN.equals(source)) {
            w.eq(Score::getCampaignId, courseId);
        } else {
            w.eq(Score::getCourseId, courseId);
        }
        if (semId != null) {
            w.eq(Score::getSemesterId, semId);
        }
        if (studentUserIds != null) {
            w.in(Score::getStudentUserId, studentUserIds);
        }
        return buildDistribution(courseId, scoreMapper.selectList(w));
    }

    @Override
    public ScoreTrendDto trend(Long courseId, String source, String className, Long callerUserId, String callerUserType) {
        if (courseId == null) {
            throw new BusinessException(400, "课程ID不能为空");
        }
        List<Long> studentUserIds = resolveScopeForCourse(courseId, source, className, callerUserId, callerUserType);
        ScoreTrendDto dto = new ScoreTrendDto();
        dto.setCourseId(courseId);
        dto.setCourseName(resolveCourseName(courseId));
        if (studentUserIds != null && studentUserIds.isEmpty()) {
            dto.setPoints(List.of());
            return dto;
        }
        LambdaQueryWrapper<Score> w = new LambdaQueryWrapper<Score>()
                .eq(Score::getScoreType, ScoreTypeEnum.REGULAR);
        if (Course.SOURCE_SELECTION_CAMPAIGN.equals(source)) {
            w.eq(Score::getCampaignId, courseId);
        } else {
            w.eq(Score::getCourseId, courseId);
        }
        if (studentUserIds != null) {
            w.in(Score::getStudentUserId, studentUserIds);
        }
        List<Score> grades = scoreMapper.selectList(w);
        Map<Long, List<Score>> bySem = grades.stream()
                .filter(s -> s.getSemesterId() != null)
                .collect(Collectors.groupingBy(Score::getSemesterId));
        if (bySem.isEmpty()) {
            dto.setPoints(List.of());
            return dto;
        }
        Map<Long, Semester> semMap = semesterService.listByIds(bySem.keySet()).stream()
                .collect(Collectors.toMap(Semester::getId, s -> s, (a, b) -> a));
        List<SemesterPoint> points = new ArrayList<>();
        for (Map.Entry<Long, List<Score>> e : bySem.entrySet()) {
            SemesterPoint p = new SemesterPoint();
            p.setSemesterId(e.getKey());
            Semester sem = semMap.get(e.getKey());
            p.setSemesterName(sem != null ? sem.getName() : null);
            p.setTotalCount(e.getValue().size());
            p.setAvgScore(avgOf(e.getValue()));
            p.setPassRate(passRateOf(e.getValue()));
            points.add(p);
        }
        points.sort(Comparator.comparing(SemesterPoint::getSemesterId));
        dto.setPoints(points);
        return dto;
    }

    @Override
    public ScoreComparisonDto comparison(Long courseId, String source, Long semesterId,
                                         Long callerUserId, String callerUserType) {
        if (courseId == null) {
            throw new BusinessException(400, "课程ID不能为空");
        }
        Long semId = resolveSemesterId(semesterId);
        List<Long> studentUserIds = resolveScopeForCourse(courseId, source, null, callerUserId, callerUserType);
        ScoreComparisonDto dto = new ScoreComparisonDto();
        dto.setCourseId(courseId);
        dto.setCourseName(resolveCourseName(courseId));
        dto.setSemesterId(semId);
        Semester sem = semId == null ? null : semesterService.getById(semId);
        dto.setSemesterName(sem != null ? sem.getName() : null);
        if (studentUserIds != null && studentUserIds.isEmpty()) {
            dto.setClasses(List.of());
            return dto;
        }
        LambdaQueryWrapper<Score> w = new LambdaQueryWrapper<Score>()
                .eq(Score::getScoreType, ScoreTypeEnum.REGULAR);
        if (Course.SOURCE_SELECTION_CAMPAIGN.equals(source)) {
            w.eq(Score::getCampaignId, courseId);
        } else {
            w.eq(Score::getCourseId, courseId);
        }
        if (semId != null) {
            w.eq(Score::getSemesterId, semId);
        }
        if (studentUserIds != null) {
            w.in(Score::getStudentUserId, studentUserIds);
        }
        dto.setClasses(buildClassPoints(scoreMapper.selectList(w)));
        return dto;
    }

    // ==================== 内部计算 ====================

    /** 教师校验课程归属后看本课程全部学生；教务/院系走范围解析。 */
    private List<Long> resolveScopeForCourse(Long courseId, String source, String className, Long callerUserId, String callerUserType) {
        if (AuthFacade.USER_TYPE_TEACHER.equals(callerUserType)) {
            if (!scopeResolver.teacherCanAccessCourse(callerUserId, courseId, source)) {
                throw new BusinessException(403, "权限不足");
            }
            return null;
        }
        return scopeResolver.resolveScopedStudentUserIds(callerUserType, callerUserId, className);
    }

    private Long resolveSemesterId(Long semesterId) {
        if (semesterId != null) {
            return semesterId;
        }
        Semester current = semesterService.getCurrent();
        return current != null ? current.getId() : null;
    }

    /** 解析课程名：courseId 可能是常规课 course.id 或公选课 selection_campaign.id，两表依次尝试。 */
    private String resolveCourseName(Long courseId) {
        CourseInfoResolver.CourseInfo info = courseInfoResolver.resolveOne(courseId, null);
        if (info == null) {
            info = courseInfoResolver.resolveOne(null, courseId);
        }
        return info != null ? info.getCourseName() : null;
    }

    private ScoreDistributionDto emptyDistribution(Long courseId) {
        ScoreDistributionDto dto = new ScoreDistributionDto();
        dto.setCourseId(courseId);
        dto.setCourseName(resolveCourseName(courseId));
        dto.setTotalCount(0);
        dto.setSeg0to59(0);
        dto.setSeg60to69(0);
        dto.setSeg70to79(0);
        dto.setSeg80to89(0);
        dto.setSeg90to100(0);
        return dto;
    }

    private ScoreDistributionDto buildDistribution(Long courseId, List<Score> grades) {
        ScoreDistributionDto dto = new ScoreDistributionDto();
        dto.setCourseId(courseId);
        dto.setCourseName(resolveCourseName(courseId));
        int s0 = 0, s6 = 0, s7 = 0, s8 = 0, s9 = 0;
        BigDecimal sum = BigDecimal.ZERO;
        int scored = 0;
        BigDecimal max = null, min = null;
        int pass = 0;
        List<BigDecimal> vals = new ArrayList<>();
        for (Score g : grades) {
            BigDecimal t = g.getTotalScore();
            if (t == null) {
                continue;
            }
            double v = t.doubleValue();
            if (v < 60) {
                s0++;
            } else if (v < 70) {
                s6++;
            } else if (v < 80) {
                s7++;
            } else if (v < 90) {
                s8++;
            } else {
                s9++;
            }
            sum = sum.add(t);
            scored++;
            if (max == null || t.compareTo(max) > 0) {
                max = t;
            }
            if (min == null || t.compareTo(min) < 0) {
                min = t;
            }
            if (v >= 60) {
                pass++;
            }
            vals.add(t);
        }
        dto.setTotalCount(scored);
        dto.setSeg0to59(s0);
        dto.setSeg60to69(s6);
        dto.setSeg70to79(s7);
        dto.setSeg80to89(s8);
        dto.setSeg90to100(s9);
        dto.setMaxScore(max);
        dto.setMinScore(min);
        if (scored > 0) {
            BigDecimal avg = sum.divide(BigDecimal.valueOf(scored), 2, RoundingMode.HALF_UP);
            dto.setAvgScore(avg);
            dto.setPassRate(BigDecimal.valueOf(pass).multiply(HUNDRED)
                    .divide(BigDecimal.valueOf(scored), 2, RoundingMode.HALF_UP));
            dto.setStddev(stddev(vals, avg));
        }
        return dto;
    }

    private List<ClassPoint> buildClassPoints(List<Score> grades) {
        if (grades.isEmpty()) {
            return List.of();
        }
        List<Long> userIds = grades.stream().map(Score::getStudentUserId).distinct().toList();
        Map<Long, Long> userClassId = studentMapper.selectList(
                        new LambdaQueryWrapper<Student>().in(Student::getUserId, userIds)).stream()
                .filter(s -> s.getClassId() != null)
                .collect(Collectors.toMap(Student::getUserId, Student::getClassId, (a, b) -> a));
        Set<Long> classIds = Set.copyOf(userClassId.values());
        Map<Long, String> classNameMap = classIds.isEmpty() ? Map.of()
                : classNameMapper.selectByIds(classIds).stream()
                        .collect(Collectors.toMap(ClassName::getId, ClassName::getClassName, (a, b) -> a));
        Map<String, List<Score>> byClass = new LinkedHashMap<>();
        for (Score g : grades) {
            Long cid = userClassId.get(g.getStudentUserId());
            String name = cid == null ? "未知班级" : classNameMap.getOrDefault(cid, "未知班级");
            byClass.computeIfAbsent(name, k -> new ArrayList<>()).add(g);
        }
        List<ClassPoint> result = new ArrayList<>();
        for (Map.Entry<String, List<Score>> e : byClass.entrySet()) {
            ClassPoint p = new ClassPoint();
            p.setClassName(e.getKey());
            List<Score> ss = e.getValue();
            p.setTotalCount(ss.size());
            p.setAvgScore(avgOf(ss));
            p.setPassRate(passRateOf(ss));
            p.setFailCount((int) ss.stream().filter(this::isFail).count());
            result.add(p);
        }
        return result;
    }

    private boolean isFail(Score s) {
        return s.getTotalScore() != null && s.getTotalScore().doubleValue() < 60;
    }

    private BigDecimal avgOf(List<Score> scores) {
        List<BigDecimal> vals = scores.stream().map(Score::getTotalScore).filter(Objects::nonNull).toList();
        if (vals.isEmpty()) {
            return null;
        }
        BigDecimal sum = BigDecimal.ZERO;
        for (BigDecimal v : vals) {
            sum = sum.add(v);
        }
        return sum.divide(BigDecimal.valueOf(vals.size()), 2, RoundingMode.HALF_UP);
    }

    private BigDecimal passRateOf(List<Score> scores) {
        List<BigDecimal> vals = scores.stream().map(Score::getTotalScore).filter(Objects::nonNull).toList();
        if (vals.isEmpty()) {
            return null;
        }
        long pass = vals.stream().filter(v -> v.doubleValue() >= 60).count();
        return BigDecimal.valueOf(pass).multiply(HUNDRED)
                .divide(BigDecimal.valueOf(vals.size()), 2, RoundingMode.HALF_UP);
    }

    private BigDecimal stddev(List<BigDecimal> vals, BigDecimal avg) {
        if (vals == null || vals.size() < 2 || avg == null) {
            return null;
        }
        BigDecimal sqSum = BigDecimal.ZERO;
        for (BigDecimal v : vals) {
            BigDecimal diff = v.subtract(avg);
            sqSum = sqSum.add(diff.multiply(diff));
        }
        double variance = sqSum.divide(BigDecimal.valueOf(vals.size()), 6, RoundingMode.HALF_UP).doubleValue();
        return BigDecimal.valueOf(Math.sqrt(variance)).setScale(2, RoundingMode.HALF_UP);
    }
}
