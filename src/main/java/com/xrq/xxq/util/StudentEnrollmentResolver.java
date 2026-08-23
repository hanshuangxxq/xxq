package com.xrq.xxq.util;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.springframework.stereotype.Component;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.xrq.xxq.common.BusinessException;
import com.xrq.xxq.module.clazz.entity.ClassName;
import com.xrq.xxq.module.clazz.mapper.ClassNameMapper;
import com.xrq.xxq.module.clazz.util.ClassNameUtil;
import com.xrq.xxq.module.selection.entity.SelectionClass;
import com.xrq.xxq.module.selection.entity.SelectionClassMember;
import com.xrq.xxq.module.selection.mapper.SelectionClassMapper;
import com.xrq.xxq.module.selection.mapper.SelectionClassMemberMapper;
import com.xrq.xxq.module.teachinfo.entity.TeachInfo;
import com.xrq.xxq.module.teachinfo.mapper.TeachInfoMapper;
import com.xrq.xxq.module.user.entity.user.Student;
import com.xrq.xxq.module.user.mapper.StudentMapper;

import lombok.RequiredArgsConstructor;

/**
 * 学生选课/班级名册与授课安排的关联解析器。
 * <p>
 * 「授课安排 → 学生名单」「学生 → 可见授课安排」的解析逻辑原散落在 Score / TeachingEvaluation /
 * Progress / TeachInfo 等服务中重复实现，各自注入 SelectionClass/SelectionClassMember 等 Mapper。
 * 本组件集中持有这些 Mapper，统一对外提供解析能力，消除重复并收敛注入点。
 * <p>
 * 解析规则：公选课班走 selection_class_member；常规班（含合班）走 class_name 名册（FIND_IN_SET）。
 * 班级名以逗号分隔的无空格 CSV 存储（与 teach_info 各处 FIND_IN_SET 用法一致）。
 */
@Component
@RequiredArgsConstructor
public class StudentEnrollmentResolver {

    private final TeachInfoMapper teachInfoMapper;
    private final StudentMapper studentMapper;
    private final ClassNameMapper classNameMapper;
    private final SelectionClassMapper selectionClassMapper;
    private final SelectionClassMemberMapper selectionClassMemberMapper;

    /**
     * 授课安排的学生 user.id 名单。
     * <p>公选课班走选课成员；常规班走班级名册。合班且指定 scopeClassName（考试排考班级）时，
     * 仅返回该班级学生，避免把未参加考试的班级学生纳入名单。
     */
    public List<Long> rosterUserIds(Long teachInfoId, String scopeClassName) {
        TeachInfo info = teachInfoMapper.selectById(teachInfoId);
        if (info == null) {
            return List.of();
        }
        // 公选课班：直接取选课成员（scopeClassName 不适用，每条授课安排对应一个选课班）
        List<SelectionClass> selClasses = selectionClassMapper.selectList(
                new LambdaQueryWrapper<SelectionClass>().eq(SelectionClass::getTeachInfoId, teachInfoId));
        if (!selClasses.isEmpty()) {
            List<Long> classIds = selClasses.stream().map(SelectionClass::getId).toList();
            return selectionClassMemberMapper.selectList(
                            new LambdaQueryWrapper<SelectionClassMember>().in(SelectionClassMember::getClassId, classIds))
                    .stream().map(SelectionClassMember::getStudentId).distinct().toList();
        }
        // 常规班：按班级名册
        List<String> classNames = ClassNameUtil.splitClassNames(info.getClassName());
        if (classNames.isEmpty()) {
            return List.of();
        }
        if (scopeClassName != null && !scopeClassName.isBlank()) {
            if (!classNames.contains(scopeClassName)) {
                throw new BusinessException(400, "考试排考班级不在该授课安排的合班范围内");
            }
            classNames = List.of(scopeClassName);
        }
        List<Long> classIds = classNameMapper.selectList(
                        new LambdaQueryWrapper<ClassName>().in(ClassName::getClassName, classNames)).stream()
                .map(ClassName::getId).toList();
        if (classIds.isEmpty()) {
            return List.of();
        }
        return studentMapper.selectList(
                        new LambdaQueryWrapper<Student>().in(Student::getClassId, classIds)).stream()
                .map(Student::getUserId).distinct().toList();
    }

    /**
     * 学生是否选修该授课安排。
     * <p>公选课班判断 selection_class_member 命中；常规班判断学生所在班级名出现在授课安排的合班 CSV 中。
     */
    public Boolean isEnrolled(Long teachInfoId, Long studentUserId) {
        TeachInfo info = teachInfoMapper.selectById(teachInfoId);
        if (info == null) {
            return false;
        }
        List<SelectionClass> selClasses = selectionClassMapper.selectList(
                new LambdaQueryWrapper<SelectionClass>().eq(SelectionClass::getTeachInfoId, teachInfoId));
        if (!selClasses.isEmpty()) {
            List<Long> classIds = selClasses.stream().map(SelectionClass::getId).toList();
            Long count = selectionClassMemberMapper.selectCount(new LambdaQueryWrapper<SelectionClassMember>()
                    .in(SelectionClassMember::getClassId, classIds)
                    .eq(SelectionClassMember::getStudentId, studentUserId));
            return count != null && count > 0;
        }
        Student stu = studentMapper.selectOne(
                new LambdaQueryWrapper<Student>().eq(Student::getUserId, studentUserId));
        if (stu == null || stu.getClassId() == null) {
            return false;
        }
        ClassName cn = classNameMapper.selectById(stu.getClassId());
        return cn != null && ClassNameUtil.splitClassNames(info.getClassName()).contains(cn.getClassName());
    }

    /**
     * 学生可见的授课安排（常规班 + 公选课班）。
     * <p>常规班按学生所在班级名 FIND_IN_SET；公选课班按 selection_class_member 反查。
     * semesterId 非空时仅返回该学期的授课安排，为 null 时返回全部（供 ExamService 等不分学期的场景）。
     */
    public List<TeachInfo> studentTeachInfos(Long studentUserId, Long semesterId) {
        String studentClassName = resolveStudentClassName(studentUserId);
        Map<Long, TeachInfo> merged = new LinkedHashMap<>();
        // 常规班
        if (studentClassName != null) {
            LambdaQueryWrapper<TeachInfo> w = new LambdaQueryWrapper<TeachInfo>()
                    .apply("FIND_IN_SET({0}, class_name) > 0", studentClassName);
            if (semesterId != null) {
                w.eq(TeachInfo::getSemesterId, semesterId);
            }
            teachInfoMapper.selectList(w).forEach(t -> merged.put(t.getId(), t));
        }
        // 公选课班
        List<Long> selTeachInfoIds = selectionTeachInfoIds(studentUserId);
        if (!selTeachInfoIds.isEmpty()) {
            LambdaQueryWrapper<TeachInfo> w = new LambdaQueryWrapper<TeachInfo>()
                    .in(TeachInfo::getId, selTeachInfoIds);
            if (semesterId != null) {
                w.eq(TeachInfo::getSemesterId, semesterId);
            }
            teachInfoMapper.selectList(w).forEach(t -> merged.put(t.getId(), t));
        }
        return new ArrayList<>(merged.values());
    }

    /** 学生加入的公选课班对应的 teachInfoId 列表（不含常规班）。 */
    public List<Long> selectionTeachInfoIds(Long studentUserId) {
        List<SelectionClassMember> members = selectionClassMemberMapper.selectList(
                new LambdaQueryWrapper<SelectionClassMember>().eq(SelectionClassMember::getStudentId, studentUserId));
        if (members.isEmpty()) {
            return List.of();
        }
        List<Long> selectionClassIds = members.stream()
                .map(SelectionClassMember::getClassId).filter(Objects::nonNull).distinct().toList();
        return selectionClassMapper.selectByIds(selectionClassIds).stream()
                .map(SelectionClass::getTeachInfoId).filter(Objects::nonNull).toList();
    }

    /** 按 teachInfoId 批量解析 SelectionClass（供公选课视图富化）。 */
    public Map<Long, SelectionClass> selectionClassByTeachInfoIds(Collection<Long> teachInfoIds) {
        List<Long> ids = teachInfoIds == null ? List.of()
                : teachInfoIds.stream().filter(Objects::nonNull).distinct().toList();
        if (ids.isEmpty()) {
            return Map.of();
        }
        return selectionClassMapper.selectList(
                        new LambdaQueryWrapper<SelectionClass>().in(SelectionClass::getTeachInfoId, ids)).stream()
                .collect(java.util.stream.Collectors.toMap(SelectionClass::getTeachInfoId, sc -> sc, (a, b) -> a));
    }

    /** 该授课安排是否关联了选课班（删授课安排前的下游引用检查）。 */
    public Boolean hasSelectionClass(Long teachInfoId) {
        Long count = selectionClassMapper.selectCount(
                new LambdaQueryWrapper<SelectionClass>().eq(SelectionClass::getTeachInfoId, teachInfoId));
        return count != null && count > 0;
    }

    /** 解析学生所在班级名（Student.classId -> ClassName.className），无则 null。 */
    private String resolveStudentClassName(Long studentUserId) {
        Student stu = studentMapper.selectOne(
                new LambdaQueryWrapper<Student>().eq(Student::getUserId, studentUserId));
        if (stu == null || stu.getClassId() == null) {
            return null;
        }
        ClassName cn = classNameMapper.selectById(stu.getClassId());
        return cn != null ? cn.getClassName() : null;
    }
}
