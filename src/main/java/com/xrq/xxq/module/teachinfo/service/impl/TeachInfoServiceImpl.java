package com.xrq.xxq.module.teachinfo.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.xrq.xxq.module.teachinfo.cache.ClassScheduleCacheManager;
import com.xrq.xxq.module.course.dto.ClassCourseDto;
import com.xrq.xxq.module.course.dto.CourseDto;
import com.xrq.xxq.module.course.dto.ElectiveCourseDto;
import com.xrq.xxq.module.course.dto.PracticeCourseDto;
import com.xrq.xxq.module.course.dto.PublicCourseDto;
import com.xrq.xxq.module.course.dto.RequiredCourseDto;
import com.xrq.xxq.module.course.dto.UserCourseDto;
import com.xrq.xxq.module.course.dto.WeekScheduleDto;
import com.xrq.xxq.common.BusinessException;
import com.xrq.xxq.module.clazz.entity.ClassName;
import com.xrq.xxq.module.clazz.util.ClassNameUtil;
import com.xrq.xxq.module.college.mapper.CollegeMapper;
import com.xrq.xxq.module.course.entity.Course;
import com.xrq.xxq.module.course.entity.CurseEnum;
import com.xrq.xxq.module.exam.entity.Exam;
import com.xrq.xxq.module.exam.mapper.ExamMapper;
import com.xrq.xxq.module.local.entity.Local;
import com.xrq.xxq.module.selection.entity.SelectionCampaign;
import com.xrq.xxq.module.selection.entity.SelectionClass;
import com.xrq.xxq.module.selection.mapper.SelectionCampaignMapper;
import com.xrq.xxq.module.score.entity.Score;
import com.xrq.xxq.module.score.mapper.ScoreMapper;
import com.xrq.xxq.module.semester.entity.Semester;
import com.xrq.xxq.module.semester.mapper.SemesterMapper;
import com.xrq.xxq.module.teachinfo.entity.TeachInfo;
import com.xrq.xxq.module.time.entity.Time;
import com.xrq.xxq.module.time.mapper.TimeMapper;
import com.xrq.xxq.module.clazz.mapper.ClassNameMapper;
import com.xrq.xxq.module.course.mapper.CourseMapper;
import com.xrq.xxq.module.local.mapper.LocalMapper;
import com.xrq.xxq.module.teachinfo.mapper.TeachInfoMapper;
import com.xrq.xxq.module.semester.service.SemesterService;
import com.xrq.xxq.module.teachinfo.service.TeachInfoService;
import com.xrq.xxq.module.user.entity.user.Department;
import com.xrq.xxq.module.user.entity.user.Student;
import com.xrq.xxq.module.user.entity.user.Teacher;
import com.xrq.xxq.module.user.mapper.DepartmentMapper;
import com.xrq.xxq.module.user.mapper.StudentMapper;
import com.xrq.xxq.module.user.mapper.TeacherMapper;
import com.xrq.xxq.util.ReferenceValidator;
import com.xrq.xxq.util.StudentEnrollmentResolver;
import com.xrq.xxq.util.TeacherNameResolver;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.io.Serializable;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import static java.util.function.Function.identity;

@Service
@RequiredArgsConstructor
public class TeachInfoServiceImpl extends ServiceImpl<TeachInfoMapper, TeachInfo> implements TeachInfoService {

    private final TeachInfoMapper teachInfoMapper;
    private final CourseMapper courseMapper;
    private final TeacherMapper teacherMapper;
    private final ClassNameMapper classNameMapper;
    private final StudentMapper studentMapper;
    private final DepartmentMapper departmentMapper;
    private final LocalMapper localMapper;
    private final TimeMapper timeMapper;
    private final ClassScheduleCacheManager cacheManager;
    private final SemesterService semesterService;
    private final SelectionCampaignMapper selectionCampaignMapper;
    private final ExamMapper examMapper;
    private final ScoreMapper scoreMapper;
    private final ReferenceValidator referenceValidator;
    private final SemesterMapper semesterMapper;
    private final StudentEnrollmentResolver enrollmentResolver;
    private final TeacherNameResolver teacherNameResolver;
    private final CollegeMapper collegeMapper;

    @Override
    public CourseDto getDetailById(Long id, Long userId, String userType) {
        TeachInfo info = teachInfoMapper.selectById(id);
        if (info == null) {
            return null;
        }
        if (!isInScope(info, userId, userType)) {
            return null;
        }
        return assembleDto(List.of(info)).getFirst();
    }

    @Override
    public UserCourseDto listByUserScope(Long userId, String userType, Long teacherId, Long courseId, Integer week) {
        List<CourseDto> cached = cacheManager.getUserScope(userType, userId, teacherId, courseId, week);
        if (cached != null) {
            return new UserCourseDto(mondayOfWeek(week), cached);
        }

        LambdaQueryWrapper<TeachInfo> wrapper = resolveScopeCondition(userId, userType);
        if (wrapper == null) {
            return new UserCourseDto(null, List.of());
        }

        if (teacherId != null) {
            wrapper.eq(TeachInfo::getTeacherId, teacherId);
        }
        if (courseId != null) {
            wrapper.eq(TeachInfo::getCourseId, courseId);
        }
        if (week != null) {
            wrapper.le(TeachInfo::getStartWeek, week)
                   .ge(TeachInfo::getEndWeek, week);
        }

        List<TeachInfo> list = teachInfoMapper.selectList(wrapper);
        List<CourseDto> courses = assembleDto(list);
        cacheManager.putUserScope(userType, userId, teacherId, courseId, week, courses);
        return new UserCourseDto(mondayOfWeek(week), courses);
    }

    @Override
    public List<ClassCourseDto> listClassCourses(Long userId) {
        List<ClassCourseDto> cached = cacheManager.getClassCourses(userId);
        if (cached != null) {
            return cached;
        }

        Student student = studentMapper.selectOne(
                new LambdaQueryWrapper<Student>().eq(Student::getUserId, userId));
        if (student == null || student.getClassId() == null) {
            return List.of();
        }
        ClassName cls = classNameMapper.selectById(student.getClassId());
        if (cls == null) {
            return List.of();
        }

        List<TeachInfo> list = teachInfoMapper.selectList(
                new LambdaQueryWrapper<TeachInfo>()
                        .apply("FIND_IN_SET({0}, class_name) > 0", cls.getClassName()));
        if (list.isEmpty()) {
            return List.of();
        }

        List<Long> courseIds = list.stream().map(TeachInfo::getCourseId).distinct().toList();
        Map<Long, String> courseNameMap = courseMapper.selectByIds(courseIds).stream()
                .collect(Collectors.toMap(Course::getId, Course::getCourseName, (a, b) -> a));

        List<ClassCourseDto> result = courseIds.stream()
                .map(id -> new ClassCourseDto(courseNameMap.getOrDefault(id, "")))
                .toList();
        cacheManager.putClassCourses(userId, result);
        return result;
    }

    @Override
    public WeekScheduleDto getWeekSchedule(Long userId, Integer week) {
        // 复用 listByUserScope 的查询逻辑（已合并选课班课程），按周次过滤后按星期分组
        UserCourseDto userCourse = listByUserScope(userId, "student", null, null, week);
        return buildWeekSchedule(week, userCourse.getMondayDate(), userCourse.getCourses());
    }

    // ── 增/改/删时淘汰相关缓存 ──

    private void validateReferences(TeachInfo entity) {
        referenceValidator.requireCourseRef(entity.getCourseId(), entity.getCampaignId());
        referenceValidator.requireExists(teacherMapper, entity.getTeacherId(), "教师");
        referenceValidator.requireExists(timeMapper, entity.getTimeId(), "时间");
        referenceValidator.requireExists(localMapper, entity.getLocalId(), "地点");
        referenceValidator.requireExists(semesterMapper, entity.getSemesterId(), "学期");
    }

    @Override
    public boolean save(TeachInfo entity) {
        validateReferences(entity);
        boolean ok = super.save(entity);
        if (ok) {
            cacheManager.evictByClassNames(entity.getClassName());
        }
        return ok;
    }

    @Override
    public boolean updateById(TeachInfo entity) {
        validateReferences(entity);
        TeachInfo old = teachInfoMapper.selectById(entity.getId());
        boolean ok = super.updateById(entity);
        if (ok) {
            if (old != null) {
                cacheManager.evictByClassNames(old.getClassName());
            }
            if (entity.getClassName() != null && !entity.getClassName().equals(old != null ? old.getClassName() : null)) {
                cacheManager.evictByClassNames(entity.getClassName());
            }
        }
        return ok;
    }

    @Override
    public boolean removeById(Serializable id) {
        TeachInfo old = teachInfoMapper.selectById(id);
        if (old == null) {
            return false;
        }
        // 校验下游引用：有考试/成绩/选课班关联时不允许删除，避免孤儿数据
        Long examCount = examMapper.selectCount(new LambdaQueryWrapper<Exam>().eq(Exam::getTeachInfoId, id));
        if (examCount != null && examCount > 0) {
            throw new BusinessException(409, "该授课安排已关联考试，无法删除");
        }
        Long scoreCount = scoreMapper.selectCount(new LambdaQueryWrapper<Score>().eq(Score::getTeachInfoId, id));
        if (scoreCount != null && scoreCount > 0) {
            throw new BusinessException(409, "该授课安排已录入成绩，无法删除");
        }
        if (enrollmentResolver.hasSelectionClass((Long) id)) {
            throw new BusinessException(409, "该授课安排关联选课班，无法删除");
        }
        boolean ok = super.removeById(id);
        if (ok) {
            cacheManager.evictByClassNames(old.getClassName());
        }
        return ok;
    }

    private WeekScheduleDto buildWeekSchedule(Integer week, LocalDate mondayDate, List<CourseDto> courses) {
        Map<String, List<CourseDto>> byDay = new LinkedHashMap<>();
        String[] dayLabels = {"周一", "周二", "周三", "周四", "周五", "周六", "周日"};
        for (int i = 1; i <= 7; i++) {
            final int dow = i;
            List<CourseDto> dayCourses = courses.stream()
                    .filter(c -> c.getDayOfWeek() != null && c.getDayOfWeek() == dow)
                    .toList();
            if (!dayCourses.isEmpty()) {
                byDay.put(dayLabels[i - 1], dayCourses);
            }
        }
        return new WeekScheduleDto(week, mondayDate, byDay);
    }

    private LambdaQueryWrapper<TeachInfo> resolveScopeCondition(Long userId, String userType) {
        LambdaQueryWrapper<TeachInfo> wrapper = new LambdaQueryWrapper<>();

        switch (userType) {
            case "student" -> {
                Student student = studentMapper.selectOne(
                        new LambdaQueryWrapper<Student>().eq(Student::getUserId, userId));
                if (student == null || student.getClassId() == null) {
                    return null;
                }
                ClassName cls = classNameMapper.selectById(student.getClassId());
                if (cls == null) {
                    return null;
                }
                // 学生加入的选课班对应的 teachInfoId（选课班 className 是虚拟名，FIND_IN_SET 查不到）
                List<Long> selectionTeachInfoIds = enrollmentResolver.selectionTeachInfoIds(userId);
                if (selectionTeachInfoIds.isEmpty()) {
                    wrapper.apply("FIND_IN_SET({0}, class_name) > 0", cls.getClassName());
                } else {
                    wrapper.and(w -> w
                            .apply("FIND_IN_SET({0}, class_name) > 0", cls.getClassName())
                            .or().in(TeachInfo::getId, selectionTeachInfoIds));
                }
            }
            case "teacher" -> {
                Teacher teacher = teacherMapper.findByUserId(userId);
                if (teacher == null) {
                    return null;
                }
                wrapper.eq(TeachInfo::getTeacherId, teacher.getId());
            }
            case "department" -> {
                Department dept = departmentMapper.findByUserId(userId);
                if (dept == null) {
                    return null;
                }
                List<String> classNames = classNameMapper.selectList(
                                new LambdaQueryWrapper<ClassName>()
                                        .eq(ClassName::getCollegeId, dept.getCollegeId()))
                        .stream()
                        .map(ClassName::getClassName)
                        .toList();
                if (classNames.isEmpty()) {
                    return null;
                }
                wrapper.and(w -> {
                    String first = classNames.getFirst();
                    w.apply("FIND_IN_SET({0}, class_name) > 0", first);
                    for (int i = 1; i < classNames.size(); i++) {
                        w.or().apply("FIND_IN_SET({0}, class_name) > 0", classNames.get(i));
                    }
                });
            }
            case "academic_admin" -> {
                // 教务管理员，无过滤条件
            }
            default -> {
                return null;
            }
        }

        return wrapper;
    }

    private boolean isInScope(TeachInfo info, Long userId, String userType) {
        LambdaQueryWrapper<TeachInfo> scope = resolveScopeCondition(userId, userType);
        if (scope == null) {
            return false;
        }
        scope.eq(TeachInfo::getId, info.getId());
        return teachInfoMapper.selectCount(scope) > 0;
    }

    @Override
    public List<Long> listMyTeachInfoIds(Long studentUserId) {
        java.util.List<Long> regular = new java.util.ArrayList<>();
        Student student = studentMapper.selectOne(
                new LambdaQueryWrapper<Student>().eq(Student::getUserId, studentUserId));
        if (student != null && student.getClassId() != null) {
            ClassName cls = classNameMapper.selectById(student.getClassId());
            if (cls != null) {
                teachInfoMapper.selectList(new LambdaQueryWrapper<TeachInfo>()
                                .apply("FIND_IN_SET({0}, class_name) > 0", cls.getClassName()))
                        .forEach(t -> regular.add(t.getId()));
            }
        }
        java.util.List<Long> selection = enrollmentResolver.selectionTeachInfoIds(studentUserId);
        java.util.List<Long> all = new java.util.ArrayList<>(regular.size() + selection.size());
        all.addAll(regular);
        all.addAll(selection);
        return all.stream().distinct().toList();
    }

    private List<CourseDto> assembleDto(List<TeachInfo> list) {
        if (list.isEmpty()) {
            return List.of();
        }

        List<Long> courseIds = list.stream().map(TeachInfo::getCourseId).distinct().toList();
        List<Long> teacherIds = list.stream().map(TeachInfo::getTeacherId).distinct().toList();
        List<String> classNames = list.stream()
                .map(TeachInfo::getClassName)
                .distinct()
                .flatMap(raw -> ClassNameUtil.splitClassNames(raw).stream())
                .distinct()
                .toList();
        List<Long> localIds = list.stream().map(TeachInfo::getLocalId).distinct().toList();
        List<Long> timeIds = list.stream().map(TeachInfo::getTimeId).filter(Objects::nonNull).distinct().toList();

        Map<Long, Course> courseMap = courseMapper.selectByIds(courseIds).stream()
                .collect(Collectors.toMap(Course::getId, identity(), (a, b) -> a));
        Map<Long, Teacher> teacherMap = teacherMapper.selectByIds(teacherIds).stream()
                .collect(Collectors.toMap(Teacher::getId, identity(), (a, b) -> a));
        Map<String, ClassName> classMap = classNameMapper.selectList(
                        new LambdaQueryWrapper<ClassName>().in(ClassName::getClassName, classNames))
                .stream()
                .collect(Collectors.toMap(ClassName::getClassName, identity(), (a, b) -> a));
        Map<Long, Local> localMap = localMapper.selectByIds(localIds).stream()
                .collect(Collectors.toMap(Local::getId, identity(), (a, b) -> a));
        Map<Long, Time> timeMap = timeIds.isEmpty()
                ? Map.of()
                : timeMapper.selectByIds(timeIds).stream()
                        .collect(Collectors.toMap(Time::getId, identity(), (a, b) -> a));

        Map<Long, String> teacherNameMap = teacherNameResolver.namesForTeachers(teacherMap.values());

        // 院系名称解析：教师 collegeId + 班级 collegeId -> college.name（用于展示 department/college）
        List<Long> collegeIds = java.util.stream.Stream.concat(
                teacherMap.values().stream().map(Teacher::getCollegeId),
                classMap.values().stream().map(ClassName::getCollegeId))
                .filter(Objects::nonNull).distinct().toList();
        Map<Long, String> collegeNameMap = collegeMapper.toNameMap(collegeIds);

        // 选课班及其活动：teachInfoId -> SelectionClass，campaignId -> SelectionCampaign
        // 用于公选课（PUBLIC）富化选课活动专属字段，前端无需再调 selection 接口合并
        List<Long> teachInfoIds = list.stream().map(TeachInfo::getId).filter(Objects::nonNull).distinct().toList();
        Map<Long, SelectionClass> selectionClassByTeachInfoId = enrollmentResolver.selectionClassByTeachInfoIds(teachInfoIds);
        List<Long> campaignIds = list.stream()
                .map(TeachInfo::getCampaignId).filter(Objects::nonNull).distinct().toList();
        Map<Long, SelectionCampaign> campaignMap = campaignIds.isEmpty()
                ? Map.of()
                : selectionCampaignMapper.selectByIds(campaignIds).stream()
                        .collect(Collectors.toMap(SelectionCampaign::getId, identity(), (a, b) -> a));

        return list.stream().map(info -> {
            // 公选课走 campaign（courseId 为 null），常规课走 course
            SelectionCampaign campaign = info.getCampaignId() != null
                    ? campaignMap.get(info.getCampaignId()) : null;
            Course course = campaign == null ? courseMap.get(info.getCourseId()) : null;
            CurseEnum courseType = campaign != null
                    ? campaign.getCourseType()
                    : (course != null ? course.getCourseType() : null);
            CourseDto resp = newCourseDto(courseType);
            resp.setId(info.getId());

            if (campaign != null) {
                resp.setCourseName(campaign.getCourseName());
                resp.setCredit(campaign.getCredit());
                resp.setCourseHour(campaign.getCourseHour());
                resp.setCourseType(courseType != null ? courseType.getDescription() : null);
            } else if (course != null) {
                resp.setCourseName(course.getCourseName());
                resp.setCredit(course.getCredit());
                resp.setCourseHour(course.getCourseHour());
                resp.setCourseType(courseType != null ? courseType.getDescription() : null);
            }

            Teacher teacher = teacherMap.get(info.getTeacherId());
            if (teacher != null) {
                resp.setDepartment(collegeNameMap.get(teacher.getCollegeId()));
                resp.setTeacherName(teacherNameMap.get(info.getTeacherId()));
            }

            resp.setClassName(info.getClassName());
            resp.setCollege(resolveCollege(info.getClassName(), classMap, collegeNameMap));

            resp.setDayOfWeek(info.getDayOfWeek());
            resp.setStartWeek(info.getStartWeek());
            resp.setEndWeek(info.getEndWeek());
            resp.setTimeId(info.getTimeId());

            Time time = timeMap.get(info.getTimeId());
            if (time != null) {
                resp.setStartPeriod(time.getStartPeriod());
                resp.setEndPeriod(time.getEndPeriod());
            }

            Local local = localMap.get(info.getLocalId());
            if (local != null) {
                resp.setBuilding(local.getBuilding());
                resp.setClassroom(local.getClassRoom());
            }

            // 公选课富化选课活动专属字段
            if (resp instanceof PublicCourseDto publicCourse) {
                SelectionClass sc = selectionClassByTeachInfoId.get(info.getId());
                if (sc != null) {
                    publicCourse.setClassNo(sc.getClassNo());
                    publicCourse.setSelectedCount(sc.getStudentCount());
                }
                if (campaign != null) {
                    publicCourse.setCampaignId(campaign.getId());
                    publicCourse.setCampaignStatus(campaign.getStatus() != null
                            ? campaign.getStatus().getCode() : null);
                    publicCourse.setCapacity(campaign.getCapacity());
                }
            }

            return resp;
        }).toList();
    }

    /**
     * 按课程性质创建对应子类视图。类型为 null 时默认必修（保守处理，保证总有具体实现可实例化）。
     */
    private CourseDto newCourseDto(CurseEnum courseType) {
        if (courseType == null) {
            return new RequiredCourseDto();
        }
        return switch (courseType) {
            case REQUIRE -> new RequiredCourseDto();
            case ELECTIVE -> new ElectiveCourseDto();
            case PUBLIC -> new PublicCourseDto();
            case PRACTICE -> new PracticeCourseDto();
        };
    }

    /** 根据当前学期的 startDate 计算第 week 周的周一日期。week 为 null 返回 null。 */
    private LocalDate mondayOfWeek(Integer week) {
        if (week == null) {
            return null;
        }
        Semester semester = semesterService.getCurrent();
        if (semester == null || semester.getStartDate() == null) {
            return null;
        }
        int startWeek = semester.getStartWeek() != null ? semester.getStartWeek() : 1;
        return semester.getStartDate().plusWeeks(week - startWeek);
    }

    /** 合班时拆分班级名，逐个查院系后去重拼接。单班直接返回对应院系。 */
    private String resolveCollege(String className, Map<String, ClassName> classMap, Map<Long, String> collegeNameMap) {
        var colleges = new java.util.LinkedHashSet<String>();
        for (String trimmed : ClassNameUtil.splitClassNames(className)) {
            ClassName cls = classMap.get(trimmed);
            if (cls != null && cls.getCollegeId() != null) {
                String name = collegeNameMap.get(cls.getCollegeId());
                if (name != null && !name.isEmpty()) {
                    colleges.add(name);
                }
            }
        }
        return colleges.isEmpty() ? null : String.join(",", colleges);
    }
}
