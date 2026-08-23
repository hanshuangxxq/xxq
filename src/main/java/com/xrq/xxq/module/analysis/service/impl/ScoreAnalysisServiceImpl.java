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
import com.xrq.xxq.module.analysis.util.ScoreStats;
import com.xrq.xxq.util.StudentScopeResolver;
import com.xrq.xxq.module.clazz.service.ClassNameService;
import com.xrq.xxq.module.course.mapper.CourseMapper;
import com.xrq.xxq.module.course.service.CourseInfoResolver;
import com.xrq.xxq.module.score.entity.Score;
import com.xrq.xxq.module.score.mapper.ScoreMapper;
import com.xrq.xxq.module.score.util.ScoreQueries;
import com.xrq.xxq.module.semester.entity.Semester;
import com.xrq.xxq.module.semester.service.SemesterService;
import com.xrq.xxq.module.user.entity.user.Student;
import com.xrq.xxq.module.user.mapper.StudentMapper;
import com.xrq.xxq.util.ParamValidator;
import com.xrq.xxq.util.auth.AuthFacade;

import lombok.RequiredArgsConstructor;

/**
 * 成绩分析服务实现：分数段分布、跨学期趋势、班级横向对比。
 */
@Service
@RequiredArgsConstructor
public class ScoreAnalysisServiceImpl implements ScoreAnalysisService {

    private final ScoreMapper scoreMapper;
    private final CourseMapper courseMapper;
    private final CourseInfoResolver courseInfoResolver;
    private final StudentMapper studentMapper;
    private final ClassNameService classNameService;
    private final SemesterService semesterService;
    private final StudentScopeResolver scopeResolver;

    @Override
    public ScoreDistributionDto distribution(Long courseId, String source, String className, Long semesterId,
                                             Long callerUserId, String callerUserType) {
        ParamValidator.requireNonNull(courseId, "课程ID");
        Long semId = semesterService.resolveOrDefault(semesterId);
        List<Long> studentUserIds = resolveScopeForCourse(courseId, source, className, callerUserId, callerUserType);
        if (studentUserIds != null && studentUserIds.isEmpty()) {
            return emptyDistribution(courseId);
        }
        LambdaQueryWrapper<Score> w = ScoreQueries.regularByCourseOrCampaign(courseId, source);
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
        ParamValidator.requireNonNull(courseId, "课程ID");
        List<Long> studentUserIds = resolveScopeForCourse(courseId, source, className, callerUserId, callerUserType);
        ScoreTrendDto dto = new ScoreTrendDto();
        dto.setCourseId(courseId);
        dto.setCourseName(courseInfoResolver.resolveNameByEitherId(courseId));
        if (studentUserIds != null && studentUserIds.isEmpty()) {
            dto.setPoints(List.of());
            return dto;
        }
        LambdaQueryWrapper<Score> w = ScoreQueries.regularByCourseOrCampaign(courseId, source);
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
        Map<Long, String> semNameMap = semesterService.toNameMap(bySem.keySet());
        List<SemesterPoint> points = new ArrayList<>();
        for (Map.Entry<Long, List<Score>> e : bySem.entrySet()) {
            SemesterPoint p = new SemesterPoint();
            p.setSemesterId(e.getKey());
            p.setSemesterName(semNameMap.get(e.getKey()));
            p.setTotalCount(e.getValue().size());
            p.setAvgScore(ScoreStats.avg(e.getValue().stream().map(Score::getTotalScore).filter(Objects::nonNull).toList()));
            p.setPassRate(ScoreStats.passRate(e.getValue().stream().map(Score::getTotalScore).filter(Objects::nonNull).toList()));
            points.add(p);
        }
        points.sort(Comparator.comparing(SemesterPoint::getSemesterId));
        dto.setPoints(points);
        return dto;
    }

    @Override
    public ScoreComparisonDto comparison(Long courseId, String source, Long semesterId,
                                         Long callerUserId, String callerUserType) {
        ParamValidator.requireNonNull(courseId, "课程ID");
        Long semId = semesterService.resolveOrDefault(semesterId);
        List<Long> studentUserIds = resolveScopeForCourse(courseId, source, null, callerUserId, callerUserType);
        ScoreComparisonDto dto = new ScoreComparisonDto();
        dto.setCourseId(courseId);
        dto.setCourseName(courseInfoResolver.resolveNameByEitherId(courseId));
        dto.setSemesterId(semId);
        Semester sem = semId == null ? null : semesterService.getById(semId);
        dto.setSemesterName(sem != null ? sem.getName() : null);
        if (studentUserIds != null && studentUserIds.isEmpty()) {
            dto.setClasses(List.of());
            return dto;
        }
        LambdaQueryWrapper<Score> w = ScoreQueries.regularByCourseOrCampaign(courseId, source);
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

    private ScoreDistributionDto emptyDistribution(Long courseId) {
        ScoreDistributionDto dto = new ScoreDistributionDto();
        dto.setCourseId(courseId);
        dto.setCourseName(courseInfoResolver.resolveNameByEitherId(courseId));
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
        dto.setCourseName(courseInfoResolver.resolveNameByEitherId(courseId));
        Integer s0 = 0, s6 = 0, s7 = 0, s8 = 0, s9 = 0;
        BigDecimal sum = BigDecimal.ZERO;
        Integer scored = 0;
        BigDecimal max = null, min = null;
        Integer pass = 0;
        List<BigDecimal> vals = new ArrayList<>();
        for (Score g : grades) {
            BigDecimal t = g.getTotalScore();
            if (t == null) {
                continue;
            }
            Double v = t.doubleValue();
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
            dto.setPassRate(BigDecimal.valueOf(pass).multiply(ScoreStats.HUNDRED)
                    .divide(BigDecimal.valueOf(scored), 2, RoundingMode.HALF_UP));
            dto.setStddev(ScoreStats.stddev(vals, avg));
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
        Map<Long, String> classNameMap = classNameService.toNameMap(classIds);
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
            p.setAvgScore(ScoreStats.avg(ss.stream().map(Score::getTotalScore).filter(Objects::nonNull).toList()));
            p.setPassRate(ScoreStats.passRate(ss.stream().map(Score::getTotalScore).filter(Objects::nonNull).toList()));
            p.setFailCount((int) ss.stream().filter(s -> ScoreStats.isFail(s.getTotalScore())).count());
            result.add(p);
        }
        return result;
    }
}
