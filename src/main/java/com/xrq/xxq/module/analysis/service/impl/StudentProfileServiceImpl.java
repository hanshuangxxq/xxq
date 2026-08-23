package com.xrq.xxq.module.analysis.service.impl;

import java.math.BigDecimal;
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

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.xrq.xxq.common.BusinessException;
import com.xrq.xxq.module.analysis.dto.StudentProfileDto;
import com.xrq.xxq.module.analysis.dto.StudentProfileDto.SemesterGpaTrend;
import com.xrq.xxq.module.analysis.dto.StudentProfileDto.SubjectPerformance;
import com.xrq.xxq.module.analysis.service.StudentProfileService;
import com.xrq.xxq.module.analysis.util.CreditSource;
import com.xrq.xxq.module.analysis.util.GpaCalculator;
import com.xrq.xxq.module.analysis.util.ScoreStats;
import com.xrq.xxq.util.StudentScopeResolver;
import com.xrq.xxq.module.clazz.entity.ClassName;
import com.xrq.xxq.module.clazz.mapper.ClassNameMapper;
import com.xrq.xxq.module.course.mapper.CourseMapper;
import com.xrq.xxq.module.course.service.CourseInfoResolver;
import com.xrq.xxq.module.mojor.entity.Major;
import com.xrq.xxq.module.mojor.mapper.MajorMapper;
import com.xrq.xxq.module.score.entity.Score;
import com.xrq.xxq.module.score.entity.ScoreTypeEnum;
import com.xrq.xxq.module.score.mapper.ScoreMapper;
import com.xrq.xxq.module.semester.entity.Semester;
import com.xrq.xxq.module.semester.service.SemesterService;
import com.xrq.xxq.module.user.entity.User;
import com.xrq.xxq.module.user.entity.user.Student;
import com.xrq.xxq.module.user.mapper.StudentMapper;
import com.xrq.xxq.module.user.mapper.UserMapper;
import com.xrq.xxq.util.auth.AuthFacade;

import lombok.RequiredArgsConstructor;

/**
 * 学生个人画像服务实现：聚合成绩、学分、绩点、趋势与班级排名。
 */
@Service
@RequiredArgsConstructor
public class StudentProfileServiceImpl implements StudentProfileService {

    private final StudentMapper studentMapper;
    private final UserMapper userMapper;
    private final ClassNameMapper classNameMapper;
    private final MajorMapper majorMapper;
    private final CourseMapper courseMapper;
    private final CourseInfoResolver courseInfoResolver;
    private final ScoreMapper scoreMapper;
    private final SemesterService semesterService;
    private final StudentScopeResolver scopeResolver;

    @Override
    public StudentProfileDto getProfile(Long studentUserId, Long callerUserId, String callerUserType) {
        assertCanView(studentUserId, callerUserId, callerUserType);

        Student stu = studentMapper.selectOne(
                new LambdaQueryWrapper<Student>().eq(Student::getUserId, studentUserId));
        if (stu == null) {
            throw new BusinessException(404, "学生不存在");
        }
        User user = userMapper.selectById(studentUserId);
        ClassName cn = stu.getClassId() == null ? null : classNameMapper.selectById(stu.getClassId());
        Major major = stu.getMajorId() == null ? null : majorMapper.selectById(stu.getMajorId());

        // 全部 REGULAR 成绩
        List<Score> all = scoreMapper.selectList(new LambdaQueryWrapper<Score>()
                .eq(Score::getStudentUserId, studentUserId)
                .eq(Score::getScoreType, ScoreTypeEnum.REGULAR));
        CreditSource creditSource = loadCreditSource(all);

        StudentProfileDto dto = new StudentProfileDto();
        dto.setStudentUserId(studentUserId);
        dto.setStudentName(user != null ? user.getName() : null);
        dto.setStudentNo(stu.getStudentNo());
        dto.setClassName(cn != null ? cn.getClassName() : null);
        dto.setMajorName(major != null ? major.getMajorName() : null);
        dto.setEnrollmentYear(stu.getEnrollmentYear());

        Semester current = semesterService.getCurrent();
        if (current != null) {
            dto.setSemesterId(current.getId());
            dto.setSemesterName(current.getName());
        }

        // 累计 / 本学期 GPA
        dto.setCumulativeGpa(GpaCalculator.weightedGpa(all, creditSource));
        List<Score> semScores = current == null ? List.of()
                : all.stream().filter(s -> Objects.equals(s.getSemesterId(), current.getId())).toList();
        dto.setSemesterGpa(GpaCalculator.weightedGpa(semScores, creditSource));

        // 学分与挂科
        dto.setTotalCredits(sumCredits(all, creditSource, false));
        dto.setEarnedCredits(sumCredits(all, creditSource, true));
        dto.setFailCount((int) all.stream().filter(s -> ScoreStats.isFail(s.getTotalScore())).count());
        dto.setSemesterFailCount((int) semScores.stream().filter(s -> ScoreStats.isFail(s.getTotalScore())).count());

        dto.setLevelDistribution(levelDistribution(all));
        dto.setSemesterTrend(buildTrend(all, creditSource));
        dto.setSubjects(buildSubjects(semScores, creditSource));
        fillClassRank(dto, stu, all);

        return dto;
    }

    // ==================== 鉴权 ====================

    private void assertCanView(Long studentUserId, Long callerUserId, String callerUserType) {
        if (AuthFacade.USER_TYPE_STUDENT.equals(callerUserType)) {
            if (!studentUserId.equals(callerUserId)) {
                throw new BusinessException(403, "权限不足");
            }
            return;
        }
        if (AuthFacade.USER_TYPE_ACADEMIC_ADMIN.equals(callerUserType)) {
            return;
        }
        if (AuthFacade.USER_TYPE_DEPARTMENT.equals(callerUserType)) {
            if (scopeResolver.departmentOwnsStudent (callerUserId, studentUserId)) {
                throw new BusinessException(403, "权限不足");
            }
            return;
        }
        throw new BusinessException(403, "权限不足");
    }

    // ==================== 计算 ====================

    /** 学分来源：常规课/公选课分表解析，避免 id 空间重叠串台。 */
    private CreditSource loadCreditSource(List<Score> scores) {
        List<Long> courseIds = scores.stream().map(Score::getCourseId).filter(Objects::nonNull).distinct().toList();
        List<Long> campaignIds = scores.stream().map(Score::getCampaignId).filter(Objects::nonNull).distinct().toList();
        Map<Long, Integer> cc = new HashMap<>();
        courseInfoResolver.resolveCourses(courseIds).forEach((id, info) -> {
            if (info.getCredit() != null) {
                cc.put(id, info.getCredit());
            }
        });
        Map<Long, Integer> pc = new HashMap<>();
        courseInfoResolver.resolveCampaigns(campaignIds).forEach((id, info) -> {
            if (info.getCredit() != null) {
                pc.put(id, info.getCredit());
            }
        });
        return new CreditSource(cc, pc);
    }

    /** 学分求和：按课程去重（碰撞安全键）；passedOnly=true 时仅计及格课程。 */
    private Integer sumCredits(List<Score> scores, CreditSource creditSource, Boolean passedOnly) {
        Integer sum = 0;
        Set<String> seen = new HashSet<>();
        for (Score s : scores) {
            String key = CreditSource.keyOf(s);
            if (key == null || !seen.add(key)) {
                continue;
            }
            Integer c = creditSource.creditOf(s);
            if (c == null) {
                continue;
            }
            if (passedOnly && ScoreStats.isFail(s.getTotalScore())) {
                continue;
            }
            sum += c;
        }
        return sum;
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

    private List<SemesterGpaTrend> buildTrend(List<Score> all, CreditSource creditSource) {
        Map<Long, List<Score>> bySem = all.stream()
                .filter(s -> s.getSemesterId() != null)
                .collect(Collectors.groupingBy(Score::getSemesterId));
        if (bySem.isEmpty()) {
            return List.of();
        }
        Map<Long, String> semNameMap = semesterService.toNameMap(bySem.keySet());
        List<SemesterGpaTrend> trend = new ArrayList<>();
        for (Map.Entry<Long, List<Score>> e : bySem.entrySet()) {
            SemesterGpaTrend t = new SemesterGpaTrend();
            t.setSemesterId(e.getKey());
            t.setSemesterName(semNameMap.get(e.getKey()));
            t.setGpa(GpaCalculator.weightedGpa(e.getValue(), creditSource));
            t.setAvgScore(ScoreStats.avg(e.getValue().stream().map(Score::getTotalScore).filter(Objects::nonNull).toList()));
            t.setFailCount((int) e.getValue().stream().filter(s -> ScoreStats.isFail(s.getTotalScore())).count());
            trend.add(t);
        }
        trend.sort(Comparator.comparing(SemesterGpaTrend::getSemesterId));
        return trend;
    }

    private List<SubjectPerformance> buildSubjects(List<Score> semScores, CreditSource creditSource) {
        if (semScores.isEmpty()) {
            return List.of();
        }
        List<Long> courseIds = semScores.stream().map(Score::getCourseId).filter(Objects::nonNull).distinct().toList();
        List<Long> campaignIds = semScores.stream().map(Score::getCampaignId).filter(Objects::nonNull).distinct().toList();
        Map<Long, CourseInfoResolver.CourseInfo> byCourse = courseInfoResolver.resolveCourses(courseIds);
        Map<Long, CourseInfoResolver.CourseInfo> byCampaign = courseInfoResolver.resolveCampaigns(campaignIds);
        return semScores.stream().map(s -> {
            CourseInfoResolver.CourseInfo info = s.getCampaignId() != null
                    ? byCampaign.get(s.getCampaignId())
                    : byCourse.get(s.getCourseId());
            SubjectPerformance p = new SubjectPerformance();
            p.setCourseId(s.getCourseId());
            p.setCourseName(info != null ? info.getCourseName() : null);
            p.setCourseType(info != null ? info.courseTypeDesc() : null);
            p.setCredit(creditSource.creditOf(s));
            p.setTotalScore(s.getTotalScore());
            p.setScoreLevel(s.getScoreLevel());
            p.setGradePoint(GpaCalculator.gradePoint(s.getTotalScore()));
            return p;
        }).toList();
    }

    /** 班级 GPA 排名：按同班同学累计 GPA 降序，本人名次 = 高于本人者数 + 1。 */
    private void fillClassRank(StudentProfileDto dto, Student stu, List<Score> myScores) {
        if (stu.getClassId() == null) {
            return;
        }
        BigDecimal myGpa = dto.getCumulativeGpa();
        List<Long> classmateUserIds = studentMapper.selectList(
                        new LambdaQueryWrapper<Student>().eq(Student::getClassId, stu.getClassId()))
                .stream().map(Student::getUserId).filter(uid -> !uid.equals(dto.getStudentUserId())).toList();
        if (classmateUserIds.isEmpty()) {
            if (myGpa != null) {
                dto.setClassRank(1);
                dto.setClassSize(1);
            }
            return;
        }
        List<Score> matesScores = scoreMapper.selectList(new LambdaQueryWrapper<Score>()
                .in(Score::getStudentUserId, classmateUserIds)
                .eq(Score::getScoreType, ScoreTypeEnum.REGULAR));
        CreditSource creditSource = loadCreditSource(matesScores);
        Map<Long, List<Score>> byMate = matesScores.stream()
                .collect(Collectors.groupingBy(Score::getStudentUserId));
        Integer ahead = 0;
        Integer scoredMates = 0;
        for (List<Score> ms : byMate.values()) {
            BigDecimal g = GpaCalculator.weightedGpa(ms, creditSource);
            if (g == null) {
                continue;
            }
            scoredMates++;
            if (myGpa == null || g.compareTo(myGpa) > 0) {
                ahead++;
            }
        }
        dto.setClassSize(scoredMates + (myGpa != null ? 1 : 0));
        if (myGpa != null) {
            dto.setClassRank(ahead + 1);
        }
    }
}
