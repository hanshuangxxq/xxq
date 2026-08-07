package com.xrq.xxq.module.analysis.service.impl;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.xrq.xxq.common.BusinessException;
import com.xrq.xxq.module.analysis.dto.LearningProgressDto;
import com.xrq.xxq.module.analysis.dto.LearningProgressDto.CourseProgress;
import com.xrq.xxq.module.analysis.service.ProgressService;
import com.xrq.xxq.module.analysis.util.StudentScopeResolver;
import com.xrq.xxq.module.clazz.entity.ClassName;
import com.xrq.xxq.module.clazz.mapper.ClassNameMapper;
import com.xrq.xxq.module.course.mapper.CourseMapper;
import com.xrq.xxq.module.course.service.CourseInfoResolver;
import com.xrq.xxq.module.exam.entity.Exam;
import com.xrq.xxq.module.exam.entity.ExamStatusEnum;
import com.xrq.xxq.module.exam.mapper.ExamMapper;
import com.xrq.xxq.module.score.entity.Score;
import com.xrq.xxq.module.score.entity.ScoreTypeEnum;
import com.xrq.xxq.module.score.mapper.ScoreMapper;
import com.xrq.xxq.module.selection.entity.SelectionClass;
import com.xrq.xxq.module.selection.entity.SelectionClassMember;
import com.xrq.xxq.module.selection.mapper.SelectionClassMapper;
import com.xrq.xxq.module.selection.mapper.SelectionClassMemberMapper;
import com.xrq.xxq.module.semester.entity.Semester;
import com.xrq.xxq.module.semester.service.SemesterService;
import com.xrq.xxq.module.teachinfo.entity.TeachInfo;
import com.xrq.xxq.module.teachinfo.mapper.TeachInfoMapper;
import com.xrq.xxq.module.user.entity.User;
import com.xrq.xxq.module.user.entity.user.Student;
import com.xrq.xxq.module.user.entity.user.Teacher;
import com.xrq.xxq.module.user.mapper.StudentMapper;
import com.xrq.xxq.module.user.mapper.TeacherMapper;
import com.xrq.xxq.module.user.mapper.UserMapper;
import com.xrq.xxq.util.auth.AuthFacade;

import lombok.RequiredArgsConstructor;

/**
 * 学习进度服务实现：派生当前学期各课程完成度。
 * <p>
 * 学生课程来源：常规班（teach_info.className 含学生班级名）+ 公选课班（selection_class_member）。
 * 进度% = (当前周 - startWeek + 1) / (endWeek - startWeek + 1) × 100，clamp 到 [0,100]；
 * 当前周由学期起始日推算。结课判定：当前周 &gt; endWeek 或考试 COMPLETED。
 */
@Service
@RequiredArgsConstructor
public class ProgressServiceImpl implements ProgressService {

    private final StudentMapper studentMapper;
    private final UserMapper userMapper;
    private final ClassNameMapper classNameMapper;
    private final TeachInfoMapper teachInfoMapper;
    private final CourseMapper courseMapper;
    private final CourseInfoResolver courseInfoResolver;
    private final TeacherMapper teacherMapper;
    private final ExamMapper examMapper;
    private final ScoreMapper scoreMapper;
    private final SelectionClassMapper selectionClassMapper;
    private final SelectionClassMemberMapper selectionClassMemberMapper;
    private final SemesterService semesterService;
    private final StudentScopeResolver scopeResolver;

    @Override
    public LearningProgressDto getProgress(Long studentUserId, Long callerUserId, String callerUserType) {
        assertCanView(studentUserId, callerUserId, callerUserType);

        Student stu = studentMapper.selectOne(
                new LambdaQueryWrapper<Student>().eq(Student::getUserId, studentUserId));
        if (stu == null) {
            throw new BusinessException(404, "学生不存在");
        }
        User user = userMapper.selectById(studentUserId);
        ClassName cn = stu.getClassId() == null ? null : classNameMapper.selectById(stu.getClassId());

        LearningProgressDto dto = new LearningProgressDto();
        dto.setStudentUserId(studentUserId);
        dto.setStudentName(user != null ? user.getName() : null);

        Semester current = semesterService.getCurrent();
        if (current == null) {
            dto.setCourses(List.of());
            return dto;
        }
        dto.setSemesterName(current.getName());
        dto.setCurrentWeek(computeCurrentWeek(current));

        List<TeachInfo> infos = resolveStudentTeachInfos(studentUserId, cn, current.getId());
        if (infos.isEmpty()) {
            dto.setCourses(List.of());
            return dto;
        }

        List<Long> courseIds = infos.stream().map(TeachInfo::getCourseId).filter(Objects::nonNull).distinct().toList();
        List<Long> campaignIds = infos.stream().map(TeachInfo::getCampaignId).filter(Objects::nonNull).distinct().toList();
        Map<Long, CourseInfoResolver.CourseInfo> byCourse = courseInfoResolver.resolveCourses(courseIds);
        Map<Long, CourseInfoResolver.CourseInfo> byCampaign = courseInfoResolver.resolveCampaigns(campaignIds);
        List<Long> teacherIds = infos.stream().map(TeachInfo::getTeacherId).filter(Objects::nonNull).distinct().toList();
        Map<Long, Long> teacherUserId = teacherIds.isEmpty() ? Map.of()
                : teacherMapper.selectByIds(teacherIds).stream()
                        .collect(Collectors.toMap(Teacher::getId, Teacher::getUserId, (a, b) -> a));
        List<Long> teacherUserIds = new ArrayList<>(teacherUserId.values());
        Map<Long, String> userNameMap = teacherUserIds.isEmpty() ? Map.of()
                : userMapper.selectByIds(teacherUserIds).stream()
                        .collect(Collectors.toMap(User::getId, User::getName, (a, b) -> a));
        List<Long> teachInfoIds = infos.stream().map(TeachInfo::getId).toList();
        Map<Long, Exam> examMap = examMapper.selectList(new LambdaQueryWrapper<Exam>()
                        .in(Exam::getTeachInfoId, teachInfoIds)
                        .eq(Exam::getSemesterId, current.getId()))
                .stream().collect(Collectors.toMap(Exam::getTeachInfoId, e -> e, (a, b) -> a));
        Map<Long, Score> scoreMap = scoreMapper.selectList(new LambdaQueryWrapper<Score>()
                        .in(Score::getTeachInfoId, teachInfoIds)
                        .eq(Score::getStudentUserId, studentUserId)
                        .eq(Score::getScoreType, ScoreTypeEnum.REGULAR))
                .stream().collect(Collectors.toMap(Score::getTeachInfoId, s -> s, (a, b) -> a));

        Integer currentWeek = dto.getCurrentWeek();
        List<CourseProgress> courses = new ArrayList<>();
        for (TeachInfo info : infos) {
            CourseProgress cp = new CourseProgress();
            cp.setTeachInfoId(info.getId());
            cp.setCourseId(info.getCourseId());
            CourseInfoResolver.CourseInfo ci = info.getCampaignId() != null
                    ? byCampaign.get(info.getCampaignId())
                    : byCourse.get(info.getCourseId());
            cp.setCourseName(ci != null ? ci.getCourseName() : null);
            Long tuid = teacherUserId.get(info.getTeacherId());
            cp.setTeacherName(tuid != null ? userNameMap.get(tuid) : null);
            cp.setStartWeek(info.getStartWeek());
            cp.setEndWeek(info.getEndWeek());
            cp.setProgressPercent(computeProgress(currentWeek, info.getStartWeek(), info.getEndWeek()));

            Exam exam = examMap.get(info.getId());
            boolean examCompleted = exam != null && exam.getStatus() == ExamStatusEnum.COMPLETED;
            cp.setExamStatus(exam == null ? "无考试" : (examCompleted ? "已完成" : "已排考"));

            Score sc = scoreMap.get(info.getId());
            cp.setScoreEntered(sc != null && sc.getTotalScore() != null);
            cp.setTotalScore(sc != null ? sc.getTotalScore() : null);

            boolean ended = (currentWeek != null && info.getEndWeek() != null && currentWeek > info.getEndWeek())
                    || examCompleted;
            cp.setStatus(ended ? "已结课" : "进行中");
            courses.add(cp);
        }
        dto.setCourses(courses);
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
            if (!scopeResolver.departmentOwnsStudent(callerUserId, studentUserId)) {
                throw new BusinessException(403, "权限不足");
            }
            return;
        }
        throw new BusinessException(403, "权限不足");
    }

    // ==================== 内部计算 ====================

    /** 当前周 = 学期起始周 + 自学期起始日经过的整周数。 */
    private Integer computeCurrentWeek(Semester sem) {
        if (sem == null || sem.getStartDate() == null || sem.getStartWeek() == null) {
            return null;
        }
        long weeks = ChronoUnit.WEEKS.between(sem.getStartDate(), LocalDate.now());
        return sem.getStartWeek() + (int) weeks;
    }

    /** 学生当前学期的授课安排：班级名册匹配 + 公选课班成员，按 id 去重。 */
    private List<TeachInfo> resolveStudentTeachInfos(Long studentUserId, ClassName cn, Long semesterId) {
        List<TeachInfo> semInfos = teachInfoMapper.selectList(new LambdaQueryWrapper<TeachInfo>()
                .eq(TeachInfo::getSemesterId, semesterId));
        String studentClassName = cn != null ? cn.getClassName() : null;

        Map<Long, TeachInfo> merged = new LinkedHashMap<>();
        // 常规班：teach_info.className（CSV 合班）含学生班级名
        if (studentClassName != null) {
            for (TeachInfo i : semInfos) {
                if (splitClassNames(i.getClassName()).contains(studentClassName)) {
                    merged.put(i.getId(), i);
                }
            }
        }
        // 公选课班：selection_class_member -> selection_class -> teach_info
        List<SelectionClassMember> memberships = selectionClassMemberMapper.selectList(
                new LambdaQueryWrapper<SelectionClassMember>().eq(SelectionClassMember::getStudentId, studentUserId));
        List<Long> selClassIds = memberships.stream().map(SelectionClassMember::getClassId)
                .filter(Objects::nonNull).distinct().toList();
        if (!selClassIds.isEmpty()) {
            Set<Long> selTeachInfoIds = selectionClassMapper.selectByIds(selClassIds).stream()
                    .map(SelectionClass::getTeachInfoId).filter(Objects::nonNull).collect(Collectors.toSet());
            for (TeachInfo i : semInfos) {
                if (selTeachInfoIds.contains(i.getId())) {
                    merged.put(i.getId(), i);
                }
            }
        }
        return new ArrayList<>(merged.values());
    }

    private Integer computeProgress(Integer currentWeek, Integer startWeek, Integer endWeek) {
        if (startWeek == null || endWeek == null || endWeek < startWeek) {
            return null;
        }
        if (currentWeek == null) {
            return null;
        }
        if (currentWeek < startWeek) {
            return 0;
        }
        if (currentWeek > endWeek) {
            return 100;
        }
        int total = endWeek - startWeek + 1;
        int done = currentWeek - startWeek + 1;
        return Math.min(100, Math.max(0, (int) Math.round(done * 100.0 / total)));
    }

    private List<String> splitClassNames(String csv) {
        if (csv == null || csv.isBlank()) {
            return List.of();
        }
        return Arrays.stream(csv.split(",")).map(String::strip).filter(s -> !s.isEmpty()).distinct().toList();
    }
}
