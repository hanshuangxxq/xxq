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
import com.xrq.xxq.module.analysis.dto.ClassAnalysisDto;
import com.xrq.xxq.module.analysis.dto.ClassTrendDto;
import com.xrq.xxq.module.analysis.service.ClassAnalysisService;
import com.xrq.xxq.module.analysis.util.GpaCalculator;
import com.xrq.xxq.module.analysis.util.StudentScopeResolver;
import com.xrq.xxq.module.clazz.entity.ClassName;
import com.xrq.xxq.module.clazz.mapper.ClassNameMapper;
import com.xrq.xxq.module.course.entity.Course;
import com.xrq.xxq.module.course.mapper.CourseMapper;
import com.xrq.xxq.module.mojor.entity.Major;
import com.xrq.xxq.module.mojor.mapper.MajorMapper;
import com.xrq.xxq.module.score.entity.Score;
import com.xrq.xxq.module.score.entity.ScoreTypeEnum;
import com.xrq.xxq.module.score.mapper.ScoreMapper;
import com.xrq.xxq.module.semester.entity.Semester;
import com.xrq.xxq.module.semester.service.SemesterService;
import com.xrq.xxq.module.user.entity.user.Student;
import com.xrq.xxq.module.user.mapper.StudentMapper;

import lombok.RequiredArgsConstructor;

/**
 * 班级/专业成绩分析服务实现：按班级或专业分组聚合 + 跨学期趋势。
 * <p>权限：教务全校、院系本院（通过 {@link StudentScopeResolver} 解析可见学生）。
 */
@Service
@RequiredArgsConstructor
public class ClassAnalysisServiceImpl implements ClassAnalysisService {

    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    private final ScoreMapper scoreMapper;
    private final CourseMapper courseMapper;
    private final StudentMapper studentMapper;
    private final ClassNameMapper classNameMapper;
    private final MajorMapper majorMapper;
    private final SemesterService semesterService;
    private final StudentScopeResolver scopeResolver;

    @Override
    public List<ClassAnalysisDto> aggregate(String groupBy, Long semesterId,
                                            Long callerUserId, String callerUserType) {
        List<Long> scopedStudentIds = scopeResolver.resolveScopedStudentUserIds(callerUserType, callerUserId, null);
        if (scopedStudentIds != null && scopedStudentIds.isEmpty()) {
            return List.of();
        }
        Long semId = resolveSemesterId(semesterId);
        LambdaQueryWrapper<Score> w = new LambdaQueryWrapper<Score>()
                .eq(Score::getScoreType, ScoreTypeEnum.REGULAR);
        if (semId != null) {
            w.eq(Score::getSemesterId, semId);
        }
        if (scopedStudentIds != null) {
            w.in(Score::getStudentUserId, scopedStudentIds);
        }
        List<Score> scores = scoreMapper.selectList(w);
        if (scores.isEmpty()) {
            return List.of();
        }

        List<Long> userIds = scores.stream().map(Score::getStudentUserId).distinct().toList();
        Map<Long, String> groupMap = loadGroupMap(userIds, groupBy);
        Map<Long, Integer> creditMap = loadCreditMap(scores);

        Map<String, List<Score>> byGroup = new LinkedHashMap<>();
        for (Score s : scores) {
            String gk = groupMap.get(s.getStudentUserId());
            if (gk == null) {
                continue; // 未分配班级/专业的跳过
            }
            byGroup.computeIfAbsent(gk, k -> new ArrayList<>()).add(s);
        }
        List<ClassAnalysisDto> result = new ArrayList<>();
        for (Map.Entry<String, List<Score>> e : byGroup.entrySet()) {
            result.add(buildGroup(e.getKey(), groupBy, e.getValue(), creditMap));
        }
        result.sort(Comparator.comparing(ClassAnalysisDto::getGpa,
                Comparator.nullsLast(Comparator.reverseOrder())));
        return result;
    }

    @Override
    public ClassTrendDto trend(String groupBy, String groupKey,
                               Long callerUserId, String callerUserType) {
        ClassTrendDto dto = new ClassTrendDto();
        dto.setGroupKey(groupKey);
        dto.setGroupType(groupBy);
        if (groupKey == null || groupKey.isBlank()) {
            dto.setPoints(List.of());
            return dto;
        }
        List<Long> scopedStudentIds = scopeResolver.resolveScopedStudentUserIds(callerUserType, callerUserId, null);
        if (scopedStudentIds != null && scopedStudentIds.isEmpty()) {
            dto.setPoints(List.of());
            return dto;
        }
        LambdaQueryWrapper<Student> sw = new LambdaQueryWrapper<>();
        if (scopedStudentIds != null) {
            sw.in(Student::getUserId, scopedStudentIds);
        }
        List<Student> students = studentMapper.selectList(sw);
        Map<Long, String> groupMap = loadGroupMap(
                students.stream().map(Student::getUserId).toList(), groupBy);
        List<Long> groupUserIds = students.stream()
                .filter(s -> groupKey.equals(groupMap.get(s.getUserId())))
                .map(Student::getUserId).toList();
        if (groupUserIds.isEmpty()) {
            dto.setPoints(List.of());
            return dto;
        }
        List<Score> scores = scoreMapper.selectList(new LambdaQueryWrapper<Score>()
                .in(Score::getStudentUserId, groupUserIds)
                .eq(Score::getScoreType, ScoreTypeEnum.REGULAR));
        Map<Long, Integer> creditMap = loadCreditMap(scores);
        Map<Long, List<Score>> bySem = scores.stream()
                .filter(s -> s.getSemesterId() != null)
                .collect(Collectors.groupingBy(Score::getSemesterId));
        Map<Long, Semester> semMap = bySem.isEmpty() ? Map.of()
                : semesterService.listByIds(bySem.keySet()).stream()
                        .collect(Collectors.toMap(Semester::getId, s -> s, (a, b) -> a));

        List<ClassTrendDto.SemesterPoint> points = new ArrayList<>();
        for (Map.Entry<Long, List<Score>> e : bySem.entrySet()) {
            ClassTrendDto.SemesterPoint p = new ClassTrendDto.SemesterPoint();
            p.setSemesterId(e.getKey());
            Semester sem = semMap.get(e.getKey());
            p.setSemesterName(sem != null ? sem.getName() : null);
            p.setAvgScore(avgOf(e.getValue()));
            p.setGpa(GpaCalculator.weightedGpa(e.getValue(), creditMap));
            p.setPassRate(passRateOf(e.getValue()));
            p.setStudentCount((int) e.getValue().stream().map(Score::getStudentUserId).distinct().count());
            points.add(p);
        }
        points.sort(Comparator.comparing(ClassTrendDto.SemesterPoint::getSemesterId));
        dto.setPoints(points);
        return dto;
    }

    // ==================== 内部计算 ====================

    private Long resolveSemesterId(Long semesterId) {
        if (semesterId != null) {
            return semesterId;
        }
        Semester current = semesterService.getCurrent();
        return current != null ? current.getId() : null;
    }

    /** userId -&gt; 组名（班级名或专业名）；无法确定的用户不进入映射。 */
    private Map<Long, String> loadGroupMap(List<Long> userIds, String groupBy) {
        if (userIds.isEmpty()) {
            return Map.of();
        }
        List<Student> students = studentMapper.selectList(
                new LambdaQueryWrapper<Student>().in(Student::getUserId, userIds));
        if ("major".equals(groupBy)) {
            Set<Long> majorIds = students.stream().map(Student::getMajorId)
                    .filter(Objects::nonNull).collect(Collectors.toSet());
            Map<Long, String> majorName = majorIds.isEmpty() ? Map.of()
                    : majorMapper.selectByIds(majorIds).stream()
                            .collect(Collectors.toMap(Major::getId, Major::getMajorName, (a, b) -> a));
            return students.stream().filter(s -> s.getMajorId() != null)
                    .collect(Collectors.toMap(Student::getUserId,
                            s -> majorName.getOrDefault(s.getMajorId(), "未知专业"), (a, b) -> a));
        }
        Set<Long> classIds = students.stream().map(Student::getClassId)
                .filter(Objects::nonNull).collect(Collectors.toSet());
        Map<Long, String> className = classIds.isEmpty() ? Map.of()
                : classNameMapper.selectByIds(classIds).stream()
                        .collect(Collectors.toMap(ClassName::getId, ClassName::getClassName, (a, b) -> a));
        return students.stream().filter(s -> s.getClassId() != null)
                .collect(Collectors.toMap(Student::getUserId,
                        s -> className.getOrDefault(s.getClassId(), "未知班级"), (a, b) -> a));
    }

    private Map<Long, Integer> loadCreditMap(List<Score> scores) {
        Set<Long> ids = scores.stream().map(Score::getCourseId).filter(Objects::nonNull).collect(Collectors.toSet());
        if (ids.isEmpty()) {
            return Map.of();
        }
        return courseMapper.selectByIds(ids).stream()
                .filter(c -> c.getCredit() != null)
                .collect(Collectors.toMap(Course::getId, Course::getCredit, (a, b) -> a));
    }

    private ClassAnalysisDto buildGroup(String groupKey, String groupType, List<Score> scores, Map<Long, Integer> creditMap) {
        ClassAnalysisDto dto = new ClassAnalysisDto();
        dto.setGroupKey(groupKey);
        dto.setGroupType(groupType);
        dto.setStudentCount((int) scores.stream().map(Score::getStudentUserId).distinct().count());
        dto.setScoreCount(scores.size());
        dto.setGpa(GpaCalculator.weightedGpa(scores, creditMap));
        dto.setAvgScore(avgOf(scores));
        dto.setPassRate(passRateOf(scores));
        dto.setFailCount((int) scores.stream().filter(this::isFail).count());
        dto.setLevelDistribution(levelDistribution(scores));
        return dto;
    }

    private Map<String, Integer> levelDistribution(List<Score> scores) {
        Map<String, Integer> dist = new LinkedHashMap<>();
        dist.put("优", 0);
        dist.put("良", 0);
        dist.put("中", 0);
        dist.put("及格", 0);
        dist.put("不及格", 0);
        for (Score s : scores) {
            String lv = s.getScoreLevel();
            if (lv != null && dist.containsKey(lv)) {
                dist.merge(lv, 1, Integer::sum);
            }
        }
        return dist;
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
}
