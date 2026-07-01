package com.xrq.xxq.module.course.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.xrq.xxq.module.course.dto.ClassCourseDto;
import com.xrq.xxq.module.course.dto.CourseDto;
import com.xrq.xxq.module.course.entity.ClassName;
import com.xrq.xxq.module.course.entity.Course;
import com.xrq.xxq.module.course.entity.Local;
import com.xrq.xxq.module.course.entity.TeachInfo;
import com.xrq.xxq.module.course.mapper.ClassNameMapper;
import com.xrq.xxq.module.course.mapper.CourseMapper;
import com.xrq.xxq.module.course.mapper.LocalMapper;
import com.xrq.xxq.module.course.mapper.TeachInfoMapper;
import com.xrq.xxq.module.course.service.TeachInfoService;
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
    public List<CourseDto> listByUserScope(Long userId, String userType, Long teacherId, Long courseId) {
        LambdaQueryWrapper<TeachInfo> wrapper = resolveScopeCondition(userId, userType);
        if (wrapper == null) {
            return List.of();
        }

        if (teacherId != null) {
            wrapper.eq(TeachInfo::getTeacherId, teacherId);
        }
        if (courseId != null) {
            wrapper.eq(TeachInfo::getCourseId, courseId);
        }

        List<TeachInfo> list = teachInfoMapper.selectList(wrapper);
        return assembleDto(list);
    }

    @Override
    public List<ClassCourseDto> listClassCourses(Long userId) {
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
                new LambdaQueryWrapper<TeachInfo>().eq(TeachInfo::getClassName, cls.getClassName()));
        if (list.isEmpty()) {
            return List.of();
        }

        List<Long> courseIds = list.stream().map(TeachInfo::getCourseId).distinct().toList();
        List<Long> teacherIds = list.stream().map(TeachInfo::getTeacherId).distinct().toList();
        List<Long> localIds = list.stream().map(TeachInfo::getLocalId).distinct().toList();

        Map<Long, String> courseNameMap = courseMapper.selectByIds(courseIds).stream()
                .collect(Collectors.toMap(Course::getId, Course::getCourseName, (a, b) -> a));
        Map<Long, Long> teacherUserIdMap = teacherMapper.selectByIds(teacherIds).stream()
                .collect(Collectors.toMap(Teacher::getId, Teacher::getUserId, (a, b) -> a));
        List<Long> userIds = teacherUserIdMap.values().stream().distinct().toList();
        Map<Long, String> userNameMap = userIds.isEmpty()
                ? Map.of()
                : userMapper.selectByIds(userIds).stream()
                        .collect(Collectors.toMap(User::getId, User::getName, (a, b) -> a));
        Map<Long, Local> localMap = localMapper.selectByIds(localIds).stream()
                .collect(Collectors.toMap(Local::getId, identity(), (a, b) -> a));

        return list.stream().map(info -> {
            ClassCourseDto dto = new ClassCourseDto();
            dto.setCourseName(courseNameMap.getOrDefault(info.getCourseId(), ""));
            Long teacherUserId = teacherUserIdMap.get(info.getTeacherId());
            if (teacherUserId != null) {
                dto.setTeacherName(userNameMap.getOrDefault(teacherUserId, ""));
            }
            dto.setDayOfWeek(info.getDayOfWeek());
            dto.setWeek(info.getWeek());
            dto.setTimeId(info.getTimeId());
            Local local = localMap.get(info.getLocalId());
            if (local != null) {
                dto.setBuilding(local.getBuilding());
                dto.setClassroom(local.getClassRoom());
            }
            return dto;
        }).toList();
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
                wrapper.eq(TeachInfo::getClassName, cls.getClassName());
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
                wrapper.in(TeachInfo::getClassName, classNames);
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

    private List<CourseDto> assembleDto(List<TeachInfo> list) {
        if (list.isEmpty()) {
            return List.of();
        }

        List<Long> courseIds = list.stream().map(TeachInfo::getCourseId).distinct().toList();
        List<Long> teacherIds = list.stream().map(TeachInfo::getTeacherId).distinct().toList();
        List<String> classNames = list.stream().map(TeachInfo::getClassName).distinct().toList();
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
            ClassName cls = classMap.get(info.getClassName());
            if (cls != null) {
                resp.setCollege(cls.getCollege());
            }

            resp.setDayOfWeek(info.getDayOfWeek());
            resp.setWeek(info.getWeek());
            resp.setTimeId(info.getTimeId());

            Local local = localMap.get(info.getLocalId());
            if (local != null) {
                resp.setBuilding(local.getBuilding());
                resp.setClassroom(local.getClassRoom());
            }

            return resp;
        }).toList();
    }
}
