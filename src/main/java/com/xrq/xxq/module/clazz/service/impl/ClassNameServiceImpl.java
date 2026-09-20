package com.xrq.xxq.module.clazz.service.impl;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.xrq.xxq.common.BusinessException;
import com.xrq.xxq.module.clazz.entity.ClassName;
import com.xrq.xxq.module.clazz.mapper.ClassNameMapper;
import com.xrq.xxq.module.clazz.service.ClassNameService;
import com.xrq.xxq.module.mojor.entity.Major;
import com.xrq.xxq.module.mojor.mapper.MajorMapper;
import com.xrq.xxq.module.user.entity.user.Student;
import com.xrq.xxq.module.user.mapper.StudentMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 班级服务实现：院系归属全部经 {@code major.college_id} 两跳推导，本表不存 college_id。
 */
@Service
@RequiredArgsConstructor
public class ClassNameServiceImpl extends ServiceImpl<ClassNameMapper, ClassName> implements ClassNameService {
    private final ClassNameMapper classNameMapper;
    private final MajorMapper majorMapper;
    private final StudentMapper studentMapper;

    @Override
    public Map<Long, String> toNameMap(Collection<Long> ids) {
        List<Long> clean = cleanIds(ids);
        if (clean.isEmpty()) {
            // 空查找 map 用 HashMap:Map.of() 的 get(null) 会抛 NPE,而调用方会传可空的 class_id
            return new HashMap<>();
        }
        return listByIds(clean).stream()
                .collect(Collectors.toMap(ClassName::getId, ClassName::getClassName, (a, b) -> a));
    }

    @Override
    public Long majorIdOf(Long classId) {
        // HashMap 而非 Map.of()：空查找 map 见 [[project_map_of_null_npe]]
        return classId == null ? null : toMajorIdMap(List.of(classId)).get(classId);
    }

    @Override
    public Map<Long, Long> toMajorIdMap(Collection<Long> classIds) {
        List<Long> ids = cleanIds(classIds);
        if (ids.isEmpty()) {
            return new HashMap<>();
        }
        return listByIds(ids).stream()
                .filter(cn -> cn.getMajorId() != null)
                .collect(Collectors.toMap(ClassName::getId, ClassName::getMajorId, (a, b) -> a));
    }

    @Override
    public Long collegeIdOf(Long classId) {
        return classId == null ? null : toCollegeIdMap(List.of(classId)).get(classId);
    }

    @Override
    public Map<Long, Long> toCollegeIdMap(Collection<Long> classIds) {
        Map<Long, Long> majorByClass = toMajorIdMap(classIds);
        if (majorByClass.isEmpty()) {
            return new HashMap<>();
        }
        Map<Long, Long> collegeByMajor = collegeIdByMajorIds(majorByClass.values());
        if (collegeByMajor.isEmpty()) {
            return new HashMap<>();
        }
        Map<Long, Long> collegeByClass = new HashMap<>();
        majorByClass.forEach((classId, majorId) -> {
            Long collegeId = collegeByMajor.get(majorId);
            if (collegeId != null) {
                collegeByClass.put(classId, collegeId);
            }
        });
        return collegeByClass;
    }

    @Override
    public Map<String, Long> toCollegeIdMapByClassName(Collection<String> classNames) {
        if (classNames == null || classNames.isEmpty()) {
            return new HashMap<>();
        }
        List<ClassName> classes = list(new LambdaQueryWrapper<ClassName>()
                .in(ClassName::getClassName, classNames));
        if (classes.isEmpty()) {
            return new HashMap<>();
        }
        // 同名班级理论上唯一，但 DB 未加唯一约束（约束走应用层），重复名取先出现的一行
        Map<Long, Long> collegeByClass = toCollegeIdMap(
                classes.stream().map(ClassName::getId).toList());
        return classes.stream()
                .filter(cn -> collegeByClass.containsKey(cn.getId()))
                .collect(Collectors.toMap(ClassName::getClassName,
                        cn -> collegeByClass.get(cn.getId()), (a, b) -> a));
    }

    @Override
    public List<Long> classIdsByMajorIds(Collection<Long> majorIds) {
        if (majorIds == null || majorIds.isEmpty()) {
            return List.of();
        }
        return lambdaQuery().select(ClassName::getId)
                .in(ClassName::getMajorId, majorIds)
                .list().stream()
                .map(ClassName::getId)
                .toList();
    }

    @Override
    public List<Long> classIdsByCollegeId(Long collegeId) {
        if (collegeId == null) {
            return List.of();
        }
        // 先按 college_id 查出该院系的专业，再按 major_id 查班级。
        // 注意别复用 collegeIdByMajorIds——那是「按 majorId 反查 collegeId」的反方向，
        // 传错会把 collegeId 当 majorId 查，结果错得毫无征兆。
        List<Long> majorIds = majorMapper.selectList(new LambdaQueryWrapper<Major>()
                        .select(Major::getId)
                        .eq(Major::getCollegeId, collegeId)).stream()
                .map(Major::getId)
                .toList();
        return classIdsByMajorIds(majorIds);
    }

    @Override
    public List<String> classNamesByCollegeId(Long collegeId) {
        List<Long> classIds = classIdsByCollegeId(collegeId);
        if (classIds.isEmpty()) {
            return List.of();
        }
        return listByIds(classIds).stream().map(ClassName::getClassName).toList();
    }

    @Override
    @Transactional
    public void deleteById(Long id) {
        if (getById(id) == null) {
            throw new BusinessException(404, "班级不存在");
        }
        // 学生归属全靠班级：删班级 = 这些学生的专业与院系一起失联，必须先安置学生
        Long refs = studentMapper.selectCount(
                new LambdaQueryWrapper<Student>().eq(Student::getClassId, id));
        if (refs != null && refs > 0) {
            throw new BusinessException(409, "该班级下仍有 " + refs + " 名学生，无法删除");
        }
        removeById(id);
    }

    /** 批量解析 majorId -> collegeId（未挂院系的不进 Map）。 */
    private Map<Long, Long> collegeIdByMajorIds(Collection<Long> majorIds) {
        List<Long> ids = cleanIds(majorIds);
        if (ids.isEmpty()) {
            return new HashMap<>();
        }
        return majorMapper.selectList(new LambdaQueryWrapper<Major>()
                        .select(Major::getId, Major::getCollegeId)
                        .in(Major::getId, ids)).stream()
                .filter(m -> m.getCollegeId() != null)
                .collect(Collectors.toMap(Major::getId, Major::getCollegeId, (a, b) -> a));
    }

    /** 去掉 null 并去重，避免 IN (null) 污染 SQL。 */
    private List<Long> cleanIds(Collection<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        return ids.stream().filter(Objects::nonNull).distinct().toList();
    }
}
