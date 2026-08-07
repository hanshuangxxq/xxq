package com.xrq.xxq.module.analysis.util;

import java.util.List;

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
 * 学生可见范围解析器：按当前角色解析可见学生 user.id 列表，供学情分析各服务复用。
 * <p>
 * 规则：教务管理员全校（返回 null 表示不过滤）；院系管理员本院班级；教师按课程归属判断；
 * 其余角色抛 403。逻辑与 {@code ScoreServiceImpl.resolveScopedStudentUserIds} 对齐，
 * 抽取到此处避免在多个分析服务中重复。
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
            Department dept = departmentMapper.selectOne(
                    new LambdaQueryWrapper<Department>().eq(Department::getUserId, userId));
            if (dept == null) {
                return List.of();
            }
            List<Long> classIds = classNameMapper.selectList(new LambdaQueryWrapper<ClassName>()
                            .eq(ClassName::getCollege, dept.getDepartmentName())).stream()
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

    /** 院系校验某学生是否在本院（学生班级 college == 院系 departmentName）。 */
    public boolean departmentOwnsStudent(Long deptUserId, Long studentUserId) {
        Department dept = departmentMapper.selectOne(
                new LambdaQueryWrapper<Department>().eq(Department::getUserId, deptUserId));
        if (dept == null) {
            return false;
        }
        Student stu = studentMapper.selectOne(
                new LambdaQueryWrapper<Student>().eq(Student::getUserId, studentUserId));
        if (stu == null || stu.getClassId() == null) {
            return false;
        }
        ClassName cn = classNameMapper.selectById(stu.getClassId());
        return cn != null && dept.getDepartmentName().equals(cn.getCollege());
    }

    /** 教师是否能访问某课程（存在该教师该课程的 teach_info）。 */
    public boolean teacherCanAccessCourse(Long teacherUserId, Long courseId, String source) {
        Teacher t = teacherMapper.selectOne(
                new LambdaQueryWrapper<Teacher>().eq(Teacher::getUserId, teacherUserId));
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
