package com.xrq.xxq.module.selection.service.impl;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.xrq.xxq.module.clazz.entity.ClassName;
import com.xrq.xxq.module.clazz.mapper.ClassNameMapper;
import com.xrq.xxq.module.selection.dto.SelectionClassResponse;
import com.xrq.xxq.module.selection.dto.StudentSelectionDto;
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
import com.xrq.xxq.module.teachinfo.entity.TeachInfo;
import com.xrq.xxq.module.teachinfo.mapper.TeachInfoMapper;
import com.xrq.xxq.module.user.entity.User;
import com.xrq.xxq.module.user.mapper.UserMapper;
import com.xrq.xxq.module.user.entity.user.Student;
import com.xrq.xxq.module.user.mapper.StudentMapper;

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

    @Override
    @Transactional
    public void finalize(Long campaignId) {
        SelectionCampaign campaign = selectionCampaignMapper.selectById(campaignId);
        if (campaign == null) {
            return;
        }

        // 清理旧的分班结果与对应的 teach_info
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
                teachInfoMapper.deleteBatchIds(oldTeachInfoIds);
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

        return classes.stream().map(c -> {
            SelectionClassResponse resp = new SelectionClassResponse();
            resp.setClassId(c.getId());
            resp.setCourseName(courseName);
            resp.setClassNo(c.getClassNo());
            resp.setStudentCount(c.getStudentCount());

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
