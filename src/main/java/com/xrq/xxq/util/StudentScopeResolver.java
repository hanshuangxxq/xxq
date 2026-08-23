package com.xrq.xxq.util;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.xrq.xxq.common.BusinessException;
import com.xrq.xxq.module.clazz.entity.ClassName;
import com.xrq.xxq.module.clazz.mapper.ClassNameMapper;
import com.xrq.xxq.module.course.entity.Course;
import com.xrq.xxq.module.teachinfo.entity.TeachInfo;
import com.xrq.xxq.module.teachinfo.mapper.TeachInfoMapper;
import com.xrq.xxq.module.user.entity.user.Department;
import com.xrq.xxq.module.user.entity.user.Student;
import com.xrq.xxq.module.user.entity.user.Teacher;
import com.xrq.xxq.module.user.mapper.DepartmentMapper;
import com.xrq.xxq.module.user.mapper.StudentMapper;
import com.xrq.xxq.module.user.mapper.TeacherMapper;
import com.xrq.xxq.util.auth.AuthFacade;

import lombok.RequiredArgsConstructor;

/**
 * 学生可见范围解析器：按当前角色解析可见学生 user.id 列表，供学情分析/成绩等模块复用。
 * <p>
 * 规则：教务管理员全校（返回 null 表示不过滤）；院系管理员本院班级（按 college_id）；
 * 教师按课程归属判断；其余角色抛 403。原散落在 ScoreServiceImpl/各分析服务中的重复逻辑统一抽取到此处。
 * <p>
 * 院系归属自 college 表标准化后统一为 college_id：院系管理员经 department.college_id，
 * 学生经 class_name.college_id，教师经 teacher.college_id。
 */
@Component
@RequiredArgsConstructor
public class StudentScopeResolver {

    private final StudentMapper studentMapper;
    private final ClassNameMapper classNameMapper;
    private final DepartmentMapper departmentMapper;
    private final TeacherMapper teacherMapper;
    private final TeachInfoMapper teachInfoMapper;

    /**
     * 返回当前角色可见的学生 user.id 列表。
     * <p>null = 不过滤（教务且未指定班级 = 全校）；空 list = 无可见学生。
     *
     * @param userType 当前用户类型
     * @param userId   当前用户 user.id
     * @param className 可选班级名过滤（仅教务/院系生效）
     */
    public List<Long> resolveScopedStudentUserIds(String userType, Long userId, String className) {
        if (AuthFacade.USER_TYPE_ACADEMIC_ADMIN.equals(userType)) {
            if (className == null || className.isBlank()) {
                return null;
            }
            List<Long> classIds = classNameMapper.selectList(new LambdaQueryWrapper<ClassName>()
                            .eq(ClassName::getClassName, className)).stream()
                    .map(ClassName::getId).toList();
            return studentIdsByClassIds(classIds);
        }
        if (AuthFacade.USER_TYPE_DEPARTMENT.equals(userType)) {
            Department dept = departmentMapper.findByUserId(userId);
            if (dept == null || dept.getCollegeId() == null) {
                return List.of();
            }
            List<Long> classIds = classNameMapper.selectList(new LambdaQueryWrapper<ClassName>()
                            .eq(ClassName::getCollegeId, dept.getCollegeId())).stream()
                    .map(ClassName::getId).toList();
            if (className != null && !className.isBlank()) {
                List<Long> nameIds = classNameMapper.selectList(new LambdaQueryWrapper<ClassName>()
                                .eq(ClassName::getClassName, className)).stream()
                        .map(ClassName::getId).toList();
                classIds = classIds.stream().filter(nameIds::contains).toList();
            }
            return studentIdsByClassIds(classIds);
        }
        throw new BusinessException(403, "权限不足");
    }

    /**
     * 院系校验某学生是否不在本院。
     * <p>返回 true 表示该学生不在该院系（或归属无法判定），调用方应拒绝访问；
     * 返回 false 表示属于本院，允许访问。（契约与历史调用方一致：{@code if (departmentOwnsStudent) throw 403}）
     */
    public boolean departmentOwnsStudent(Long deptUserId, Long studentUserId) {
        Department dept = departmentMapper.findByUserId(deptUserId);
        if (dept == null || dept.getCollegeId() == null) {
            return true;
        }
        Student stu = studentMapper.selectOne(
                new LambdaQueryWrapper<Student>().eq(Student::getUserId, studentUserId));
        if (stu == null || stu.getClassId() == null) {
            return true;
        }
        ClassName cn = classNameMapper.selectById(stu.getClassId());
        return cn == null || !Objects.equals(dept.getCollegeId(), cn.getCollegeId());
    }

    /** 院系管理员所属 college_id（无匹配/未分配返回 null）。 */
    public Long deptCollegeId(Long deptUserId) {
        Department dept = departmentMapper.findByUserId(deptUserId);
        return dept == null ? null : dept.getCollegeId();
    }

    /** 教师所属 college_id（无匹配/未分配返回 null）。 */
    public Long teacherCollegeId(Long teacherUserId) {
        Teacher t = teacherMapper.findByUserId(teacherUserId);
        return t == null ? null : t.getCollegeId();
    }

    /** 学生所属 college_id（经 class_name.college_id；无班级/无匹配返回 null）。 */
    public Long studentCollegeId(Long studentUserId) {
        Student stu = studentMapper.selectOne(
                new LambdaQueryWrapper<Student>().eq(Student::getUserId, studentUserId));
        if (stu == null || stu.getClassId() == null) {
            return null;
        }
        ClassName cn = classNameMapper.selectById(stu.getClassId());
        return cn == null ? null : cn.getCollegeId();
    }

    /**
     * 批量解析 studentUserId -> college_id（student + class_name 各一次批查）。
     * <p>
     * 供列表接口的院系可见性过滤使用，替代在循环中逐条调用 {@link #departmentOwnsStudent}
     * 或 {@link #studentCollegeId}（每条的 2~3 次单表查询）。学生不存在/无班级/班级无院系时
     * 该学生不出现在返回 Map 中（视为归属无法判定，过滤方应按不可见处理）。
     */
    public Map<Long, Long> studentCollegeIdMap(Collection<Long> studentUserIds) {
        if (studentUserIds == null || studentUserIds.isEmpty()) {
            return Map.of();
        }
        List<Student> students = studentMapper.selectList(
                new LambdaQueryWrapper<Student>().in(Student::getUserId, studentUserIds));
        if (students.isEmpty()) {
            return Map.of();
        }
        List<Long> classIds = students.stream().map(Student::getClassId)
                .filter(Objects::nonNull).distinct().toList();
        Map<Long, Long> collegeByClassId = classIds.isEmpty()
                ? Map.of()
                : classNameMapper.selectByIds(classIds).stream()
                        .filter(cn -> cn.getCollegeId() != null)
                        .collect(Collectors.toMap(ClassName::getId, ClassName::getCollegeId, (a, b) -> a));
        Map<Long, Long> map = new HashMap<>();
        for (Student s : students) {
            Long collegeId = s.getClassId() == null ? null : collegeByClassId.get(s.getClassId());
            if (collegeId != null) {
                map.put(s.getUserId(), collegeId);
            }
        }
        return map;
    }

    /** 教师是否能访问某课程（存在该教师该课程的 teach_info）。 */
    public boolean teacherCanAccessCourse(Long teacherUserId, Long courseId, String source) {
        Teacher t = teacherMapper.findByUserId(teacherUserId);
        if (t == null) {
            return false;
        }
        LambdaQueryWrapper<TeachInfo> w = new LambdaQueryWrapper<TeachInfo>()
                .eq(TeachInfo::getTeacherId, t.getId());
        // source=SELECTION_CAMPAIGN 时 courseId 实为 campaignId，按 campaign_id 校验授课关系
        if (Course.SOURCE_SELECTION_CAMPAIGN.equals(source)) {
            w.eq(TeachInfo::getCampaignId, courseId);
        } else {
            w.eq(TeachInfo::getCourseId, courseId);
        }
        Long count = teachInfoMapper.selectCount(w);
        return count != null && count > 0;
    }

    private List<Long> studentIdsByClassIds(List<Long> classIds) {
        if (classIds.isEmpty()) {
            return List.of();
        }
        return studentMapper.selectList(new LambdaQueryWrapper<Student>().in(Student::getClassId, classIds))
                .stream().map(Student::getUserId).toList();
    }
}
