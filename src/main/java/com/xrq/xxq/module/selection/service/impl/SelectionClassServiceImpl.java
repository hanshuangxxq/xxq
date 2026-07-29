package com.xrq.xxq.module.selection.service.impl;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.xrq.xxq.common.BusinessException;
import com.xrq.xxq.module.clazz.entity.ClassName;
import com.xrq.xxq.module.clazz.mapper.ClassNameMapper;
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
    private final TeachInfoMapper teachInfoMapper;
    private final TeacherMapper teacherMapper;
    private final DraftCacheManager draftCacheManager;
    private final ClassScheduleCacheManager classScheduleCacheManager;

    @Override
    @Transactional
    public void finalize(Long campaignId) {
        SelectionCampaign campaign = selectionCampaignMapper.selectById(campaignId);
        if (campaign == null) {
            return;
        }

        // 清理旧的分班结果与对应的 teach_info，同步清理草稿箱中残留的选课班草稿
        List<SelectionClass> existing = selectionClassMapper.selectList(
                new LambdaQueryWrapper<SelectionClass>().eq(SelectionClass::getCampaignId, campaignId));
        if (!existing.isEmpty()) {
            List<Long> oldClassIds = existing.stream().map(SelectionClass::getId).toList();
            selectionClassMemberMapper.delete(new LambdaQueryWrapper<SelectionClassMember>()
                    .in(SelectionClassMember::getClassId, oldClassIds));
            List<Long> oldTeachInfoIds = existing.stream()
                    .map(SelectionClass::getTeachInfoId)
                    .filter(Objects::nonNull)
                    .toList();
            if (!oldTeachInfoIds.isEmpty()) {
                List<TeachInfo> oldTeachInfos = teachInfoMapper.selectByIds(oldTeachInfoIds);
                for (TeachInfo oldTi : oldTeachInfos) {
                    if (oldTi.getClassName() != null && !oldTi.getClassName().isBlank()) {
                        draftCacheManager.removeByClassName(oldTi.getClassName());
                    }
                }
                teachInfoMapper.deleteByIds(oldTeachInfoIds);
            }
            selectionClassMapper.delete(new LambdaQueryWrapper<SelectionClass>()
                    .eq(SelectionClass::getCampaignId, campaignId));
        }

        // 一个活动 = 一门课，按 selectTime 升序切班
        List<SelectionRecord> records = selectionRecordMapper.selectList(
                new LambdaQueryWrapper<SelectionRecord>()
                        .eq(SelectionRecord::getCampaignId, campaignId)
                        .eq(SelectionRecord::getStatus, RecordStatusEnum.SELECTED)
                        .orderByAsc(SelectionRecord::getSelectTime));
        if (records.isEmpty()) {
            return;
        }

        int capacity = campaign.getCapacity() == null || campaign.getCapacity() <= 0
                ? Integer.MAX_VALUE : campaign.getCapacity();
        int classNo = 1;
        int idx = 0;
        List<TeachInfo> createdTeachInfos = new ArrayList<>();
        while (idx < records.size()) {
            int end = Math.min(idx + capacity, records.size());
            List<SelectionRecord> chunk = records.subList(idx, end);

            // 1. 创建 teach_info 记录（courseId 用 campaign 衍生的 course.id）
            TeachInfo ti = new TeachInfo();
            ti.setCourseId(campaign.getCourseId());
            ti.setTeacherId(null);
            ti.setClassName(campaign.getName() + "-" + classNo);
            ti.setTimeId(null);
            ti.setLocalId(null);
            ti.setDayOfWeek(null);
            ti.setStartWeek(campaign.getStartWeek());
            ti.setEndWeek(campaign.getEndWeek());
            ti.setSemesterId(campaign.getSemesterId());
            teachInfoMapper.insert(ti);
            createdTeachInfos.add(ti);

            // 2. 创建 selection_class
            SelectionClass selectionClass = new SelectionClass();
            selectionClass.setCampaignId(campaignId);
            selectionClass.setClassNo(classNo);
            selectionClass.setStudentCount(chunk.size());
            selectionClass.setTeachInfoId(ti.getId());
            selectionClassMapper.insert(selectionClass);

            for (SelectionRecord r : chunk) {
                SelectionClassMember member = new SelectionClassMember();
                member.setClassId(selectionClass.getId());
                member.setStudentId(r.getStudentId());
                member.setRecordId(r.getId());
                selectionClassMemberMapper.insert(member);
            }

            idx = end;
            classNo++;
        }

        // 将选课班 TeachInfo 推入排课草稿箱，让排课流程能消费它们
        if (!createdTeachInfos.isEmpty()) {
            draftCacheManager.addDrafts(createdTeachInfos);
        }

        // 分班改变了 teach_info 数据，清空课表缓存避免脏读
        classScheduleCacheManager.clearAll();
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
            Teacher teacher = teacherMapper.selectById(teacherId);
            if (teacher == null) {
                throw new BusinessException(404, "教师不存在");
            }
            if (teacher.getUserId() != null) {
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
        // 教师变更可能影响课表，清空缓存避免脏读
        classScheduleCacheManager.clearAll();

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
        String courseName = campaign != null ? campaign.getName() : null;

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
        Map<Long, String> teacherUserNameMap = teacherUserIds.isEmpty() ? Map.of()
                : userMapper.selectByIds(teacherUserIds).stream()
                        .collect(Collectors.toMap(User::getId, User::getName, (a, b) -> a));

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
