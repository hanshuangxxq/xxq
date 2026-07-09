package com.xrq.xxq.module.teachinfo.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.xrq.xxq.module.teachinfo.cache.ClassScheduleCacheManager;
import com.xrq.xxq.module.course.dto.ClassCourseDto;
import com.xrq.xxq.module.course.dto.CourseDto;
import com.xrq.xxq.module.course.dto.UserCourseDto;
import com.xrq.xxq.module.course.dto.WeekScheduleDto;
import com.xrq.xxq.module.clazz.entity.ClassName;
import com.xrq.xxq.module.course.entity.Course;
import com.xrq.xxq.module.local.entity.Local;
import com.xrq.xxq.module.semester.entity.Semester;
import com.xrq.xxq.module.teachinfo.entity.TeachInfo;
import com.xrq.xxq.module.clazz.mapper.ClassNameMapper;
import com.xrq.xxq.module.course.mapper.CourseMapper;
import com.xrq.xxq.module.local.mapper.LocalMapper;
import com.xrq.xxq.module.teachinfo.mapper.TeachInfoMapper;
import com.xrq.xxq.module.semester.service.SemesterService;
import com.xrq.xxq.module.teachinfo.service.TeachInfoService;
import com.xrq.xxq.module.user.entity.User;
import com.xrq.xxq.module.user.entity.user.Department;
import com.xrq.xxq.module.user.entity.user.Student;
import com.xrq.xxq.module.user.entity.user.Teacher;
import com.xrq.xxq.module.user.mapper.DepartmentMapper;
import com.xrq.xxq.module.user.mapper.StudentMapper;
import com.xrq.xxq.module.user.mapper.TeacherMapper;
import com.xrq.xxq.module.user.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.io.Serializable;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static java.util.function.Function.identity;

@Service
@RequiredArgsConstructor
public class TeachInfoServiceImpl extends ServiceImpl<TeachInfoMapper, TeachInfo> implements TeachInfoService {

    private final TeachInfoMapper teachInfoMapper;
    private final CourseMapper courseMapper;
    private final TeacherMapper teacherMapper;
    private final UserMapper userMapper;
    private final ClassNameMapper classNameMapper;
    private final StudentMapper studentMapper;
    private final DepartmentMapper departmentMapper;
    private final LocalMapper localMapper;
    private final ClassScheduleCacheManager cacheManager;
    private final SemesterService semesterService;

    @Override
    public CourseDto getDetailById(Long id, Long userId, String userType) {
        TeachInfo info = teachInfoMapper.selectById(id);
        if (info == null) {
            return null;
        }
        if (!isInScope(info, userId, userType)) {
            return null;
        }
        return assembleDto(List.of(info), userType).getFirst();
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
        List<CourseDto> courses = assembleDto(list, userType);
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
    public WeekScheduleDto getWeekSchedule(String className, Integer week) {
        List<CourseDto> cached = cacheManager.get(className, week);
        if (cached != null) {
            return buildWeekSchedule(week, mondayOfWeek(week), cached);
        }

        List<TeachInfo> list = teachInfoMapper.selectList(
                new LambdaQueryWrapper<TeachInfo>()
                        .apply("FIND_IN_SET({0}, class_name) > 0", className)
                        .le(TeachInfo::getStartWeek, week)
                        .ge(TeachInfo::getEndWeek, week));

        List<CourseDto> courses = assembleDto(list, "student");
        cacheManager.put(className, week, courses);
        return buildWeekSchedule(week, mondayOfWeek(week), courses);
    }

    // ── 增/改/删时淘汰相关缓存 ──

    @Override
    public boolean save(TeachInfo entity) {
        boolean ok = super.save(entity);
        if (ok) {
            cacheManager.evictByClassNames(entity.getClassName());
        }
        return ok;
    }

    @Override
    public boolean updateById(TeachInfo entity) {
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
        boolean ok = super.removeById(id);
        if (ok && old != null) {
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
                wrapper.apply("FIND_IN_SET({0}, class_name) > 0", cls.getClassName());
            }
            case "teacher" -> {
                Teacher teacher = teacherMapper.selectOne(
                        new LambdaQueryWrapper<Teacher>().eq(Teacher::getUserId, userId));
                if (teacher == null) {
                    return null;
                }
                wrapper.eq(TeachInfo::getTeacherId, teacher.getId());
            }
            case "department" -> {
                Department dept = departmentMapper.selectOne(
                        new LambdaQueryWrapper<Department>().eq(Department::getUserId, userId));
                if (dept == null) {
                    return null;
                }
                List<String> classNames = classNameMapper.selectList(
                                new LambdaQueryWrapper<ClassName>()
                                        .eq(ClassName::getCollege, dept.getDepartmentName()))
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

    private List<CourseDto> assembleDto(List<TeachInfo> list, String userType) {
        if (list.isEmpty()) {
            return List.of();
        }

        List<Long> courseIds = list.stream().map(TeachInfo::getCourseId).distinct().toList();
        List<Long> teacherIds = list.stream().map(TeachInfo::getTeacherId).distinct().toList();
        List<String> classNames = list.stream()
                .map(TeachInfo::getClassName)
                .distinct()
                .flatMap(raw -> java.util.Arrays.stream(raw.split(",")))
                .map(String::strip)
                .distinct()
                .toList();
        List<Long> localIds = list.stream().map(TeachInfo::getLocalId).distinct().toList();

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

        List<Long> userIds = teacherMap.values().stream().map(Teacher::getUserId).toList();
        Map<Long, User> userMap = userIds.isEmpty()
                ? Map.of()
                : userMapper.selectByIds(userIds).stream()
                        .collect(Collectors.toMap(User::getId, identity(), (a, b) -> a));

        return list.stream().map(info -> {
            CourseDto resp = new CourseDto();

            Course course = courseMap.get(info.getCourseId());
            if (course != null) {
                resp.setCourseName(course.getCourseName());
                resp.setCredit(course.getCredit());
                resp.setCourseHour(course.getCourseHour());
                resp.setCourseType(course.getCourseType() != null ? course.getCourseType().getDescription() : null);
            }

            Teacher teacher = teacherMap.get(info.getTeacherId());
            if (teacher != null) {
                resp.setDepartment(teacher.getDepartment());
                User user = userMap.get(teacher.getUserId());
                if (user != null) {
                    resp.setTeacherName(user.getName());
                }
            }

            resp.setClassName(info.getClassName());
            resp.setCollege(resolveCollege(info.getClassName(), classMap));

            if (!"teacher".equals(userType)) {
                resp.setDayOfWeek(info.getDayOfWeek());
                resp.setStartWeek(info.getStartWeek());
                resp.setEndWeek(info.getEndWeek());
                resp.setTimeId(info.getTimeId());

                Local local = localMap.get(info.getLocalId());
                if (local != null) {
                    resp.setBuilding(local.getBuilding());
                    resp.setClassroom(local.getClassRoom());
                }
            }

            return resp;
        }).toList();
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
    private String resolveCollege(String className, Map<String, ClassName> classMap) {
        if (className == null || className.isBlank()) {
            return null;
        }
        var colleges = new java.util.LinkedHashSet<String>();
        for (String part : className.split(",")) {
            String trimmed = part.strip();
            if (!trimmed.isEmpty()) {
                ClassName cls = classMap.get(trimmed);
                if (cls != null && cls.getCollege() != null && !cls.getCollege().isEmpty()) {
                    colleges.add(cls.getCollege());
                }
            }
        }
        return colleges.isEmpty() ? null : String.join(",", colleges);
    }
}
