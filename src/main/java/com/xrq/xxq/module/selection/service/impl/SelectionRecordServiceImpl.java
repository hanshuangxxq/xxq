package com.xrq.xxq.module.selection.service.impl;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.xrq.xxq.common.BusinessException;
import com.xrq.xxq.module.selection.dto.SelectionCourseResponse;
import com.xrq.xxq.module.selection.dto.SelectionRecordRequest;
import com.xrq.xxq.module.selection.dto.SelectionRecordResponse;
import com.xrq.xxq.module.selection.dto.StudentCourseGroupResponse;
import com.xrq.xxq.module.selection.entity.CampaignStatusEnum;
import com.xrq.xxq.module.selection.entity.RecordStatusEnum;
import com.xrq.xxq.module.selection.entity.SelectionCampaign;
import com.xrq.xxq.module.selection.entity.SelectionCampaignGroup;
import com.xrq.xxq.module.selection.entity.SelectionCourse;
import com.xrq.xxq.module.selection.entity.SelectionCourseTimeRestriction;
import com.xrq.xxq.module.selection.entity.SelectionGroup;
import com.xrq.xxq.module.selection.entity.SelectionRecord;
import com.xrq.xxq.module.selection.mapper.SelectionCampaignGroupMapper;
import com.xrq.xxq.module.selection.mapper.SelectionCourseMapper;
import com.xrq.xxq.module.selection.mapper.SelectionCourseTimeRestrictionMapper;
import com.xrq.xxq.module.selection.mapper.SelectionGroupMapper;
import com.xrq.xxq.module.selection.mapper.SelectionRecordMapper;
import com.xrq.xxq.module.selection.service.SelectionCampaignService;
import com.xrq.xxq.module.selection.service.SelectionRecordService;
import com.xrq.xxq.module.user.entity.user.Student;
import com.xrq.xxq.module.user.mapper.StudentMapper;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class SelectionRecordServiceImpl implements SelectionRecordService {

    private static final String COUNT_KEY_PREFIX = "selection:count:";
    private static final String INCR_LUA =
            "local cur = redis.call('INCR', KEYS[1]) " +
            "if cur > tonumber(ARGV[1]) then " +
            "  redis.call('DECR', KEYS[1]) " +
            "  return -1 " +
            "end " +
            "return cur";

    private final SelectionRecordMapper selectionRecordMapper;
    private final SelectionCourseMapper selectionCourseMapper;
    private final SelectionGroupMapper selectionGroupMapper;
    private final SelectionCampaignGroupMapper selectionCampaignGroupMapper;
    private final SelectionCourseTimeRestrictionMapper selectionCourseTimeRestrictionMapper;
    private final StudentMapper studentMapper;
    private final SelectionCampaignService campaignService;
    private final StringRedisTemplate redisTemplate;

    @Override
    @Transactional
    public SelectionRecordResponse select(Long studentUserId, SelectionRecordRequest request) {
        SelectionCampaign campaign = campaignService.getById(request.getCampaignId());
        if (campaign == null) {
            throw new BusinessException(404, "选课活动不存在");
        }
        if (campaign.getStatus() != CampaignStatusEnum.OPEN) {
            throw new BusinessException(409, "活动未开放");
        }
        LocalDateTime now = LocalDateTime.now();
        if (now.isBefore(campaign.getStartTime()) || now.isAfter(campaign.getEndTime())) {
            throw new BusinessException(409, "不在选课时间窗口内");
        }

        SelectionCourse sc = selectionCourseMapper.selectOne(new LambdaQueryWrapper<SelectionCourse>()
                .eq(SelectionCourse::getId, request.getSelectionCourseId())
                .eq(SelectionCourse::getCampaignId, request.getCampaignId()));
        if (sc == null) {
            throw new BusinessException(404, "该课程不在可选列表中");
        }

        Student student = studentMapper.selectOne(new LambdaQueryWrapper<Student>()
                .eq(Student::getUserId, studentUserId));
        if (student == null) {
            throw new BusinessException(403, "学生信息不存在");
        }
        if (!isAllowed(sc, student.getGradeId(), student.getMajorId())) {
            throw new BusinessException(409, "本课程不对您的年级或专业开放");
        }

        SelectionGroup group = selectionGroupMapper.selectById(sc.getGroupId());
        if (group == null) {
            throw new BusinessException(409, "该课程未归属任何选课组");
        }
        // 组内选课上限跨活动共用：统计该生在本组下、跨所有活动的已选课程数。
        // 不同选课组之间独立计数。
        List<Long> groupSelectionCourseIds = selectionCourseMapper.selectList(new LambdaQueryWrapper<SelectionCourse>()
                .eq(SelectionCourse::getGroupId, sc.getGroupId()))
                .stream().map(SelectionCourse::getId).toList();
        Long selectedInGroup = selectionRecordMapper.selectCount(new LambdaQueryWrapper<SelectionRecord>()
                .eq(SelectionRecord::getStudentId, studentUserId)
                .in(SelectionRecord::getSelectionCourseId, groupSelectionCourseIds)
                .eq(SelectionRecord::getStatus, RecordStatusEnum.SELECTED));
        if (selectedInGroup >= group.getMaxCourses()) {
            throw new BusinessException(409, "超过本组选课上限 " + group.getMaxCourses() + " 门");
        }

        Long dupCount = selectionRecordMapper.selectCount(new LambdaQueryWrapper<SelectionRecord>()
                .eq(SelectionRecord::getCampaignId, request.getCampaignId())
                .eq(SelectionRecord::getStudentId, studentUserId)
                .eq(SelectionRecord::getSelectionCourseId, request.getSelectionCourseId())
                .eq(SelectionRecord::getStatus, RecordStatusEnum.SELECTED));
        if (dupCount > 0) {
            throw new BusinessException(409, "已选该课程");
        }

        String countKey = COUNT_KEY_PREFIX + request.getCampaignId() + ":" + request.getSelectionCourseId();
        DefaultRedisScript<Long> script = new DefaultRedisScript<>(INCR_LUA, Long.class);
        Long result = redisTemplate.execute(script, List.of(countKey), String.valueOf(sc.getCapacity()));
        if (result == null || result == -1) {
            throw new BusinessException(409, "课程已满");
        }

        try {
            SelectionRecord record = new SelectionRecord();
            record.setCampaignId(request.getCampaignId());
            record.setStudentId(studentUserId);
            record.setSelectionCourseId(request.getSelectionCourseId());
            record.setStatus(RecordStatusEnum.SELECTED);
            record.setSelectTime(LocalDateTime.now());
            selectionRecordMapper.insert(record);
            return toResponse(record, sc);
        } catch (Exception e) {
            redisTemplate.opsForValue().decrement(countKey);
            throw e;
        }
    }

    @Override
    @Transactional
    public void drop(Long studentUserId, Long recordId) {
        SelectionRecord record = selectionRecordMapper.selectById(recordId);
        if (record == null) {
            throw new BusinessException(404, "选课记录不存在");
        }
        if (!record.getStudentId().equals(studentUserId)) {
            throw new BusinessException(403, "无权操作他人选课记录");
        }
        if (record.getStatus() != RecordStatusEnum.SELECTED) {
            throw new BusinessException(409, "该记录已退选");
        }
        SelectionCampaign campaign = campaignService.getById(record.getCampaignId());
        if (campaign == null) {
            throw new BusinessException(404, "选课活动不存在");
        }
        if (campaign.getStatus() != CampaignStatusEnum.OPEN) {
            throw new BusinessException(409, "活动未开放，不可退选");
        }
        LocalDateTime now = LocalDateTime.now();
        if (now.isBefore(campaign.getStartTime()) || now.isAfter(campaign.getEndTime())) {
            throw new BusinessException(409, "不在选课时间窗口内");
        }

        record.setStatus(RecordStatusEnum.DROPPED);
        record.setDropTime(LocalDateTime.now());
        selectionRecordMapper.updateById(record);

        String countKey = COUNT_KEY_PREFIX + record.getCampaignId() + ":" + record.getSelectionCourseId();
        redisTemplate.opsForValue().decrement(countKey);
    }

    @Override
    public List<SelectionRecordResponse> listMy(Long studentUserId, Long campaignId) {
        List<SelectionRecord> records = selectionRecordMapper.selectList(
                new LambdaQueryWrapper<SelectionRecord>()
                        .eq(SelectionRecord::getStudentId, studentUserId)
                        .eq(SelectionRecord::getCampaignId, campaignId)
                        .orderByDesc(SelectionRecord::getSelectTime));
        if (records.isEmpty()) {
            return List.of();
        }
        List<Long> scIds = records.stream().map(SelectionRecord::getSelectionCourseId).distinct().toList();
        Map<Long, SelectionCourse> scMap = selectionCourseMapper.selectByIds(scIds).stream()
                .collect(Collectors.toMap(SelectionCourse::getId, c -> c));
        return records.stream()
                .map(r -> toResponse(r, scMap.get(r.getSelectionCourseId())))
                .toList();
    }

    @Override
    public List<StudentCourseGroupResponse> listCampaignCoursesForStudent(Long campaignId, Long studentUserId) {
        SelectionCampaign campaign = campaignService.getById(campaignId);
        if (campaign == null) {
            throw new BusinessException(404, "选课活动不存在");
        }
        Student student = studentMapper.selectOne(new LambdaQueryWrapper<Student>()
                .eq(Student::getUserId, studentUserId));
        if (student == null) {
            throw new BusinessException(403, "学生信息不存在");
        }
        Long myGradeId = student.getGradeId();
        Long myMajorId = student.getMajorId();

        List<SelectionCampaignGroup> bindings = selectionCampaignGroupMapper.selectList(
                new LambdaQueryWrapper<SelectionCampaignGroup>()
                        .eq(SelectionCampaignGroup::getCampaignId, campaignId)
                        .orderByAsc(SelectionCampaignGroup::getGroupId));
        if (bindings.isEmpty()) {
            return List.of();
        }
        List<Long> groupIds = bindings.stream().map(SelectionCampaignGroup::getGroupId).toList();
        Map<Long, SelectionGroup> groupMap = selectionGroupMapper.selectBatchIds(groupIds).stream()
                .collect(Collectors.toMap(SelectionGroup::getId, g -> g));
        List<SelectionGroup> groups = bindings.stream()
                .map(b -> groupMap.get(b.getGroupId()))
                .filter(g -> g != null)
                .toList();
        if (groups.isEmpty()) {
            return List.of();
        }

        List<SelectionCourse> courses = selectionCourseMapper.selectList(
                new LambdaQueryWrapper<SelectionCourse>().eq(SelectionCourse::getCampaignId, campaignId));
        Map<Long, List<SelectionCourse>> coursesByGroup = courses.stream()
                .collect(Collectors.groupingBy(SelectionCourse::getGroupId));

        // 一次性加载所有 selection_course 的 TimeRestriction 关联
        Map<Long, List<Long>> trMap = loadTimeRestrictions(courses.stream().map(SelectionCourse::getId).toList());

        List<SelectionRecord> allRecords = selectionRecordMapper.selectList(
                new LambdaQueryWrapper<SelectionRecord>()
                        .eq(SelectionRecord::getCampaignId, campaignId)
                        .eq(SelectionRecord::getStatus, RecordStatusEnum.SELECTED));
        Map<Long, Long> courseSelectedCount = allRecords.stream()
                .collect(Collectors.groupingBy(SelectionRecord::getSelectionCourseId, Collectors.counting()));
        Set<Long> mySelectedScIds = allRecords.stream()
                .filter(r -> r.getStudentId().equals(studentUserId))
                .map(SelectionRecord::getSelectionCourseId)
                .collect(Collectors.toSet());

        return groups.stream().map(g -> {
            StudentCourseGroupResponse groupResp = new StudentCourseGroupResponse();
            groupResp.setGroupId(g.getId());
            groupResp.setGroupName(g.getName());
            groupResp.setGroupMax(g.getMaxCourses());
            List<SelectionCourse> groupCourses = coursesByGroup.getOrDefault(g.getId(), List.of()).stream()
                    .filter(sc -> isAllowed(sc, myGradeId, myMajorId))
                    .toList();
            int selectedInGroup = (int) groupCourses.stream()
                    .map(SelectionCourse::getId)
                    .filter(mySelectedScIds::contains)
                    .count();
            groupResp.setSelectedInGroup(selectedInGroup);
            groupResp.setCourses(groupCourses.stream().map(sc -> {
                SelectionCourseResponse resp = new SelectionCourseResponse();
                resp.setId(sc.getId());
                resp.setCampaignId(sc.getCampaignId());
                resp.setCourseId(sc.getCourseId());
                resp.setCourseName(sc.getCourseName());
                resp.setCourseCode(sc.getCourseCode());
                resp.setCredit(sc.getCredit());
                resp.setCourseHour(sc.getCourseHour());
                resp.setDescription(sc.getDescription());
                resp.setCourseType(sc.getCourseType() != null ? sc.getCourseType().getDescription() : null);
                resp.setAllowedGradeIds(parseLongs(sc.getAllowedGradeIds()));
                resp.setAllowedMajors(parseLongs(sc.getAllowedMajors()));
                resp.setTimeRestrictionIds(trMap.getOrDefault(sc.getId(), List.of()));
                resp.setGroupId(g.getId());
                resp.setGroupName(g.getName());
                resp.setCapacity(sc.getCapacity());
                int cnt = courseSelectedCount.getOrDefault(sc.getId(), 0L).intValue();
                resp.setSelectedCount(cnt);
                resp.setRemaining(Math.max(0, sc.getCapacity() - cnt));
                resp.setSelectedByMe(mySelectedScIds.contains(sc.getId()));
                return resp;
            }).toList());
            return groupResp;
        }).toList();
    }

    private SelectionRecordResponse toResponse(SelectionRecord record, SelectionCourse sc) {
        SelectionRecordResponse resp = new SelectionRecordResponse();
        resp.setId(record.getId());
        resp.setCampaignId(record.getCampaignId());
        resp.setSelectionCourseId(record.getSelectionCourseId());
        resp.setStatus(record.getStatus());
        resp.setSelectTime(record.getSelectTime());
        resp.setDropTime(record.getDropTime());
        if (sc != null) {
            resp.setCourseName(sc.getCourseName());
            resp.setCourseCode(sc.getCourseCode());
            resp.setCredit(sc.getCredit());
            resp.setCourseType(sc.getCourseType() != null ? sc.getCourseType().getDescription() : null);
        }
        return resp;
    }

    private boolean isAllowed(SelectionCourse sc, Long studentGradeId, Long studentMajorId) {
        List<Long> allowedGrades = parseLongs(sc.getAllowedGradeIds());
        if (!allowedGrades.isEmpty()) {
            if (studentGradeId == null || !allowedGrades.contains(studentGradeId)) {
                return false;
            }
        }
        List<Long> allowedMajors = parseLongs(sc.getAllowedMajors());
        if (!allowedMajors.isEmpty()) {
            if (studentMajorId == null || !allowedMajors.contains(studentMajorId)) {
                return false;
            }
        }
        return true;
    }

    private Map<Long, List<Long>> loadTimeRestrictions(List<Long> selectionCourseIds) {
        if (selectionCourseIds.isEmpty()) {
            return Map.of();
        }
        List<SelectionCourseTimeRestriction> rels = selectionCourseTimeRestrictionMapper.selectList(
                new LambdaQueryWrapper<SelectionCourseTimeRestriction>()
                        .in(SelectionCourseTimeRestriction::getSelectionCourseId, selectionCourseIds));
        return rels.stream().collect(Collectors.groupingBy(
                SelectionCourseTimeRestriction::getSelectionCourseId,
                Collectors.mapping(SelectionCourseTimeRestriction::getTimeRestrictionId, Collectors.toList())));
    }

    private List<Long> parseLongs(String stored) {
        if (stored == null || stored.isBlank()) {
            return List.of();
        }
        return Arrays.stream(stored.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .map(Long::valueOf)
                .toList();
    }
}
