package com.xrq.xxq.module.selection.service.impl;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.xrq.xxq.common.BusinessException;
import com.xrq.xxq.module.clazz.entity.ClassName;
import com.xrq.xxq.module.clazz.mapper.ClassNameMapper;
import com.xrq.xxq.module.course.entity.Course;
import com.xrq.xxq.module.course.mapper.CourseMapper;
import com.xrq.xxq.module.selection.dto.SelectionClassResponse;
import com.xrq.xxq.module.selection.dto.StudentSelectionDto;
import com.xrq.xxq.module.selection.entity.CampaignStatusEnum;
import com.xrq.xxq.module.selection.entity.RecordStatusEnum;
import com.xrq.xxq.module.selection.entity.SelectionCampaign;
import com.xrq.xxq.module.selection.entity.SelectionClass;
import com.xrq.xxq.module.selection.entity.SelectionClassMember;
import com.xrq.xxq.module.selection.entity.SelectionRecord;
import com.xrq.xxq.module.selection.mapper.SelectionCampaignMapper;
import com.xrq.xxq.module.selection.mapper.SelectionClassMapper;
import com.xrq.xxq.module.selection.mapper.SelectionClassMemberMapper;
import com.xrq.xxq.module.selection.mapper.SelectionRecordMapper;
import com.xrq.xxq.module.selection.service.SelectionClassService;
import com.xrq.xxq.module.scheduling.cache.DraftCacheManager;
import com.xrq.xxq.module.teachinfo.cache.ClassScheduleCacheManager;
import com.xrq.xxq.module.teachinfo.entity.TeachInfo;
import com.xrq.xxq.module.teachinfo.mapper.TeachInfoMapper;
import com.xrq.xxq.module.user.entity.User;
import com.xrq.xxq.module.user.mapper.UserMapper;
import com.xrq.xxq.module.user.entity.user.Student;
import com.xrq.xxq.module.user.entity.user.Teacher;
import com.xrq.xxq.module.user.mapper.StudentMapper;
import com.xrq.xxq.module.user.mapper.TeacherMapper;
import com.xrq.xxq.util.ReferenceValidator;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class SelectionClassServiceImpl implements SelectionClassService {

    private final SelectionClassMapper selectionClassMapper;
    private final SelectionClassMemberMapper selectionClassMemberMapper;
    private final SelectionRecordMapper selectionRecordMapper;
    private final SelectionCampaignMapper selectionCampaignMapper;
    private final StudentMapper studentMapper;
    private final UserMapper userMapper;
    private final ClassNameMapper classNameMapper;
    private final CourseMapper courseMapper;
    private final TeachInfoMapper teachInfoMapper;
    private final TeacherMapper teacherMapper;
    private final DraftCacheManager draftCacheManager;
    private final ClassScheduleCacheManager classScheduleCacheManager;
    private final ReferenceValidator referenceValidator;

    @Override
    @Transactional
    public void finalize(Long campaignId) {
        SelectionCampaign campaign = selectionCampaignMapper.selectById(campaignId);
        if (campaign == null) {
            return;
        }

        // 按新 selectTime 升序 + 容量切，得到新分班（每班 SelectionRecord 列表）
        List<SelectionRecord> records = selectionRecordMapper.selectList(
                new LambdaQueryWrapper<SelectionRecord>()
                        .eq(SelectionRecord::getCampaignId, campaignId)
                        .eq(SelectionRecord::getStatus, RecordStatusEnum.SELECTED)
                        .orderByAsc(SelectionRecord::getSelectTime));
        int capacity = campaign.getCapacity() == null || campaign.getCapacity() <= 0
                ? Integer.MAX_VALUE : campaign.getCapacity();
        List<List<SelectionRecord>> newBatches = new ArrayList<>();
        int idx = 0;
        while (idx < records.size()) {
            int end = Math.min(idx + capacity, records.size());
            newBatches.add(new ArrayList<>(records.subList(idx, end)));
            idx = end;
        }

        String courseName = campaign.getCourseName() != null ? campaign.getCourseName() : "活动";

        // 现有分班：按 classNo 索引
        List<SelectionClass> existingClasses = selectionClassMapper.selectList(
                new LambdaQueryWrapper<SelectionClass>().eq(SelectionClass::getCampaignId, campaignId));
        Map<Integer, SelectionClass> existingByClassNo = existingClasses.stream()
                .collect(Collectors.toMap(SelectionClass::getClassNo, c -> c, (a, b) -> a));
        // 现有 member：classId -> List<member>
        List<Long> oldClassIds = existingClasses.stream().map(SelectionClass::getId).toList();
        Map<Long, List<SelectionClassMember>> existingMembersByClassId = oldClassIds.isEmpty()
                ? Map.of()
                : selectionClassMemberMapper.selectList(new LambdaQueryWrapper<SelectionClassMember>()
                        .in(SelectionClassMember::getClassId, oldClassIds)).stream()
                        .collect(Collectors.groupingBy(SelectionClassMember::getClassId));
        // 现有 teach_info：teachInfoId -> TeachInfo（删除多余班时清草稿用）
        List<Long> oldTeachInfoIds = existingClasses.stream()
                .map(SelectionClass::getTeachInfoId).filter(Objects::nonNull).toList();
        Map<Long, TeachInfo> existingTiMap = oldTeachInfoIds.isEmpty()
                ? Map.of()
                : teachInfoMapper.selectByIds(oldTeachInfoIds).stream()
                        .collect(Collectors.toMap(TeachInfo::getId, t -> t, (a, b) -> a));

        int newClassCount = newBatches.size();
        List<TeachInfo> createdTeachInfos = new ArrayList<>();

        // 处理新分班（classNo 1..newClassCount）：增量 diff
        int classNo = 1;
        for (List<SelectionRecord> batch : newBatches) {
            SelectionClass sc = existingByClassNo.get(classNo);
            Set<Long> newStudentIds = batch.stream()
                    .map(SelectionRecord::getStudentId).collect(Collectors.toSet());
            if (sc == null) {
                // 唯一性预检 (campaign_id, class_no)
                Long classDup = selectionClassMapper.selectCount(new LambdaQueryWrapper<SelectionClass>()
                        .eq(SelectionClass::getCampaignId, campaignId)
                        .eq(SelectionClass::getClassNo, classNo));
                if (classDup > 0) {
                    throw new BusinessException(409, "选课班编号已存在(活动=" + campaignId + ", 班号=" + classNo + ")");
                }
                // 新班：建 teach_info（教师/时段/教室为 null）+ selection_class + member
                TeachInfo ti = new TeachInfo();
                ti.setCampaignId(campaign.getId());
                ti.setClassName(courseName + "-" + classNo);
                ti.setStartWeek(campaign.getStartWeek());
                ti.setEndWeek(campaign.getEndWeek());
                ti.setSemesterId(campaign.getSemesterId());
                teachInfoMapper.insert(ti);
                createdTeachInfos.add(ti);
                sc = new SelectionClass();
                sc.setCampaignId(campaignId);
                sc.setClassNo(classNo);
                sc.setStudentCount(batch.size());
                sc.setTeachInfoId(ti.getId());
                selectionClassMapper.insert(sc);
                for (SelectionRecord r : batch) {
                    insertMember(sc.getId(), r);
                }
            } else {
                // 共有班：保留 teach_info（含已分配教师 + 排课时段/教室）+ selection_class，仅增删变化的 member
                List<SelectionClassMember> existingMembers = existingMembersByClassId.getOrDefault(sc.getId(), List.of());
                Set<Long> existingStudentIds = existingMembers.stream()
                        .map(SelectionClassMember::getStudentId).collect(Collectors.toSet());
                for (SelectionClassMember m : existingMembers) {
                    if (!newStudentIds.contains(m.getStudentId())) {
                        selectionClassMemberMapper.deleteById(m.getId());
                    }
                }
                Map<Long, SelectionRecord> recordByStudent = batch.stream()
                        .collect(Collectors.toMap(SelectionRecord::getStudentId, r -> r, (a, b) -> a));
                for (Long newStudentId : newStudentIds) {
                    if (!existingStudentIds.contains(newStudentId)) {
                        insertMember(sc.getId(), recordByStudent.get(newStudentId));
                    }
                }
                if (sc.getStudentCount() == null || sc.getStudentCount() != batch.size()) {
                    sc.setStudentCount(batch.size());
                    selectionClassMapper.updateById(sc);
                }
            }
            classNo++;
        }

        // 删除多余的旧班（classNo > newClassCount）：删 member + teach_info + selection_class + 草稿
        for (SelectionClass sc : existingClasses) {
            if (sc.getClassNo() != null && sc.getClassNo() > newClassCount) {
                selectionClassMemberMapper.delete(new LambdaQueryWrapper<SelectionClassMember>()
                        .eq(SelectionClassMember::getClassId, sc.getId()));
                if (sc.getTeachInfoId() != null) {
                    TeachInfo oldTi = existingTiMap.get(sc.getTeachInfoId());
                    if (oldTi != null && oldTi.getClassName() != null && !oldTi.getClassName().isBlank()) {
                        draftCacheManager.removeByClassName(oldTi.getClassName());
                    }
                    teachInfoMapper.deleteById(sc.getTeachInfoId());
                }
                selectionClassMapper.deleteById(sc.getId());
            }
        }

        // 新增班推草稿箱，让排课流程能消费
        if (!createdTeachInfos.isEmpty()) {
            draftCacheManager.addDrafts(createdTeachInfos);
        }

        // 精细化清缓存：所有班 class 维度 + 成员 user 维度（替代 clearAll 全局清空）
        StringBuilder classNames = new StringBuilder();
        for (int n = 1; n <= newClassCount; n++) {
            if (n > 1) classNames.append(",");
            classNames.append(courseName).append("-").append(n);
        }
        classScheduleCacheManager.evictByClassNames(classNames.toString());
        for (SelectionRecord r : records) {
            if (r.getStudentId() != null) {
                classScheduleCacheManager.evictUserScope(r.getStudentId());
            }
        }
    }

    /** 插入选课班成员：唯一性预检 (class_id, student_id) + 外键存在性校验。 */
    private void insertMember(Long classId, SelectionRecord r) {
        // 唯一性预检 (class_id, student_id)
        Long memberDup = selectionClassMemberMapper.selectCount(new LambdaQueryWrapper<SelectionClassMember>()
                .eq(SelectionClassMember::getClassId, classId)
                .eq(SelectionClassMember::getStudentId, r.getStudentId()));
        if (memberDup > 0) {
            throw new BusinessException(409, "该学生已在本选课班中");
        }
        // 外键存在性
        referenceValidator.requireExists(selectionClassMapper, classId, "选课班");
        referenceValidator.requireExists(userMapper, r.getStudentId(), "用户");
        referenceValidator.requireExists(selectionRecordMapper, r.getId(), "选课记录");
        SelectionClassMember member = new SelectionClassMember();
        member.setClassId(classId);
        member.setStudentId(r.getStudentId());
        member.setRecordId(r.getId());
        selectionClassMemberMapper.insert(member);
    }

    @Override
    @Transactional
    public SelectionClassResponse assignTeacher(Long campaignId, Long classId, Long teacherId) {
        SelectionCampaign campaign = selectionCampaignMapper.selectById(campaignId);
        if (campaign == null) {
            throw new BusinessException(404, "选课活动不存在");
        }
        if (campaign.getStatus() != CampaignStatusEnum.FINALIZED) {
            throw new BusinessException(409, "仅已分班的活动可分配教师");
        }
        SelectionClass selectionClass = selectionClassMapper.selectById(classId);
        if (selectionClass == null || !campaignId.equals(selectionClass.getCampaignId())) {
            throw new BusinessException(404, "选课班不存在或不属于该活动");
        }
        if (selectionClass.getTeachInfoId() == null) {
            throw new BusinessException(409, "选课班未关联教学信息");
        }
        TeachInfo ti = teachInfoMapper.selectById(selectionClass.getTeachInfoId());
        if (ti == null) {
            throw new BusinessException(404, "教学信息不存在");
        }

        String teacherName = null;
        if (teacherId != null) {
            referenceValidator.requireExists(teacherMapper, teacherId, "教师");
            Teacher teacher = teacherMapper.selectById(teacherId);
            if (teacher != null && teacher.getUserId() != null) {
                User teacherUser = userMapper.selectById(teacher.getUserId());
                teacherName = teacherUser != null ? teacherUser.getName() : null;
            }
        }

        // MyBatis Plus updateById 默认 NOT_NULL 策略会跳过 null 字段，
        // 取消分配（teacherId=null）时不会写入 DB，必须用 LambdaUpdateWrapper 显式 set。
        teachInfoMapper.update(null,
                new LambdaUpdateWrapper<TeachInfo>()
                        .eq(TeachInfo::getId, ti.getId())
                        .set(TeachInfo::getTeacherId, teacherId));

        // 同步草稿箱中对应草稿的教师字段（草稿可能已被排课消费，updateTeacher 内部静默跳过）
        draftCacheManager.updateTeacher(ti.getId(), teacherId, teacherName);
        // 精细化清缓存：该班 class 维度 + 教师/成员 user 维度（替代 clearAll 全局清空）
        classScheduleCacheManager.evictByClassNames(ti.getClassName());
        if (teacherId != null) {
            Teacher t = teacherMapper.selectById(teacherId);
            if (t != null && t.getUserId() != null) {
                classScheduleCacheManager.evictUserScope(t.getUserId());
            }
        }
        List<SelectionClassMember> members = selectionClassMemberMapper.selectList(
                new LambdaQueryWrapper<SelectionClassMember>().eq(SelectionClassMember::getClassId, classId));
        for (SelectionClassMember m : members) {
            if (m.getStudentId() != null) {
                classScheduleCacheManager.evictUserScope(m.getStudentId());
            }
        }

        return listByCampaign(campaignId).stream()
                .filter(r -> classId.equals(r.getClassId()))
                .findFirst()
                .orElseThrow(() -> new BusinessException(500, "分配后未找到选课班"));
    }

    @Override
    public List<SelectionClassResponse> listByCampaign(Long campaignId) {
        List<SelectionClass> classes = selectionClassMapper.selectList(
                new LambdaQueryWrapper<SelectionClass>().eq(SelectionClass::getCampaignId, campaignId));
        if (classes.isEmpty()) {
            return List.of();
        }

        SelectionCampaign campaign = selectionCampaignMapper.selectById(campaignId);
        String courseName = campaign != null ? campaign.getCourseName() : null;

        List<Long> classIds = classes.stream().map(SelectionClass::getId).toList();
        List<SelectionClassMember> allMembers = selectionClassMemberMapper.selectList(
                new LambdaQueryWrapper<SelectionClassMember>().in(SelectionClassMember::getClassId, classIds));
        Map<Long, List<SelectionClassMember>> membersByClass = allMembers.stream()
                .collect(Collectors.groupingBy(SelectionClassMember::getClassId));

        List<Long> studentIds = allMembers.stream().map(SelectionClassMember::getStudentId).distinct().toList();
        Map<Long, Student> studentMap = studentIds.isEmpty() ? Map.of()
                : studentMapper.selectList(new LambdaQueryWrapper<Student>().in(Student::getUserId, studentIds))
                        .stream().collect(Collectors.toMap(Student::getUserId, s -> s));
        Map<Long, User> userMap = studentIds.isEmpty() ? Map.of()
                : userMapper.selectByIds(studentIds).stream()
                        .collect(Collectors.toMap(User::getId, u -> u));
        List<Long> classIdFromStudent = studentMap.values().stream()
                .map(Student::getClassId).filter(Objects::nonNull).distinct().toList();
        Map<Long, ClassName> classNameMap = classIdFromStudent.isEmpty() ? Map.of()
                : classNameMapper.selectByIds(classIdFromStudent).stream()
                        .collect(Collectors.toMap(ClassName::getId, c -> c));

        // 教师信息：teachInfoId -> TeachInfo -> teacherId -> Teacher -> userId -> User.name
        List<Long> teachInfoIds = classes.stream()
                .map(SelectionClass::getTeachInfoId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        Map<Long, TeachInfo> teachInfoMap = teachInfoIds.isEmpty() ? Map.of()
                : teachInfoMapper.selectByIds(teachInfoIds).stream()
                        .collect(Collectors.toMap(TeachInfo::getId, t -> t));
        List<Long> teacherIds = teachInfoMap.values().stream()
                .map(TeachInfo::getTeacherId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        Map<Long, Teacher> teacherMap = teacherIds.isEmpty() ? Map.of()
                : teacherMapper.selectByIds(teacherIds).stream()
                        .collect(Collectors.toMap(Teacher::getId, t -> t));
        List<Long> teacherUserIds = teacherMap.values().stream()
                .map(Teacher::getUserId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        Map<Long, String> teacherUserNameMap = userMapper.toNameMap(teacherUserIds);

        return classes.stream().map(c -> {
            SelectionClassResponse resp = new SelectionClassResponse();
            resp.setClassId(c.getId());
            resp.setCourseName(courseName);
            resp.setClassNo(c.getClassNo());
            resp.setStudentCount(c.getStudentCount());

            TeachInfo ti = c.getTeachInfoId() != null ? teachInfoMap.get(c.getTeachInfoId()) : null;
            Long teacherId = ti != null ? ti.getTeacherId() : null;
            String teacherName = null;
            if (teacherId != null) {
                Teacher teacher = teacherMap.get(teacherId);
                if (teacher != null && teacher.getUserId() != null) {
                    teacherName = teacherUserNameMap.get(teacher.getUserId());
                }
            }
            resp.setTeacherId(teacherId);
            resp.setTeacherName(teacherName);

            List<SelectionClassMember> members = membersByClass.getOrDefault(c.getId(), List.of());
            List<StudentSelectionDto> dtos = members.stream().map(m -> {
                StudentSelectionDto dto = new StudentSelectionDto();
                dto.setStudentId(m.getStudentId());
                Student student = studentMap.get(m.getStudentId());
                if (student != null) {
                    dto.setStudentNo(student.getStudentNo());
                    ClassName cn = student.getClassId() != null ? classNameMap.get(student.getClassId()) : null;
                    dto.setClassName(cn != null ? cn.getClassName() : null);
                }
                User user = userMap.get(m.getStudentId());
                if (user != null) {
                    dto.setStudentName(user.getName());
                }
                return dto;
            }).toList();
            resp.setMembers(dtos);
            return resp;
        }).toList();
    }
}
