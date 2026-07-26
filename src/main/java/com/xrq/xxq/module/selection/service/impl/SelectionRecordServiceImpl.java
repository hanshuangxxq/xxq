package com.xrq.xxq.module.selection.service.impl;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.xrq.xxq.common.BusinessException;
import com.xrq.xxq.module.selection.dto.SelectionRecordRequest;
import com.xrq.xxq.module.selection.dto.SelectionRecordResponse;
import com.xrq.xxq.module.selection.dto.StudentCampaignResponse;
import com.xrq.xxq.module.selection.entity.CampaignStatusEnum;
import com.xrq.xxq.module.selection.entity.RecordStatusEnum;
import com.xrq.xxq.module.selection.entity.SelectionCampaign;
import com.xrq.xxq.module.selection.entity.SelectionCampaignTimeRestriction;
import com.xrq.xxq.module.selection.entity.SelectionGroup;
import com.xrq.xxq.module.selection.entity.SelectionRecord;
import com.xrq.xxq.module.selection.mapper.SelectionCampaignMapper;
import com.xrq.xxq.module.selection.mapper.SelectionCampaignTimeRestrictionMapper;
import com.xrq.xxq.module.selection.mapper.SelectionGroupMapper;
import com.xrq.xxq.module.selection.mapper.SelectionRecordMapper;
import com.xrq.xxq.module.selection.service.SelectionRecordService;
import com.xrq.xxq.module.semester.entity.Semester;
import com.xrq.xxq.module.semester.service.SemesterService;
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
    private final SelectionCampaignMapper selectionCampaignMapper;
    private final SelectionGroupMapper selectionGroupMapper;
    private final SelectionCampaignTimeRestrictionMapper selectionCampaignTimeRestrictionMapper;
    private final StudentMapper studentMapper;
    private final SemesterService semesterService;
    private final StringRedisTemplate redisTemplate;

    @Override
    @Transactional
    public SelectionRecordResponse select(Long studentUserId, SelectionRecordRequest request) {
        SelectionCampaign campaign = selectionCampaignMapper.selectById(request.getCampaignId());
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

        Student student = studentMapper.selectOne(new LambdaQueryWrapper<Student>()
                .eq(Student::getUserId, studentUserId));
        if (student == null) {
            throw new BusinessException(403, "学生信息不存在");
        }
        if (!isAllowed(campaign, student.getGradeId(), student.getMajorId())) {
            throw new BusinessException(409, "本课程不对您的年级或专业开放");
        }

        // 组内选课上限跨活动共用：统计该生在本组下、跨所有活动的已选课程数。
        if (campaign.getGroupId() != null) {
            SelectionGroup group = selectionGroupMapper.selectById(campaign.getGroupId());
            if (group == null) {
                throw new BusinessException(409, "该活动未归属任何选课组");
            }
            List<Long> siblingCampaignIds = selectionCampaignMapper.selectList(
                    new LambdaQueryWrapper<SelectionCampaign>()
                            .eq(SelectionCampaign::getGroupId, campaign.getGroupId()))
                    .stream().map(SelectionCampaign::getId).toList();
            Long selectedInGroup = selectionRecordMapper.selectCount(new LambdaQueryWrapper<SelectionRecord>()
                    .eq(SelectionRecord::getStudentId, studentUserId)
                    .in(SelectionRecord::getCampaignId, siblingCampaignIds)
                    .eq(SelectionRecord::getStatus, RecordStatusEnum.SELECTED));
            if (selectedInGroup >= group.getMaxCourses()) {
                throw new BusinessException(409, "超过本组选课上限 " + group.getMaxCourses() + " 门");
            }
        }

        Long dupCount = selectionRecordMapper.selectCount(new LambdaQueryWrapper<SelectionRecord>()
                .eq(SelectionRecord::getCampaignId, request.getCampaignId())
                .eq(SelectionRecord::getStudentId, studentUserId)
                .eq(SelectionRecord::getStatus, RecordStatusEnum.SELECTED));
        if (dupCount > 0) {
            throw new BusinessException(409, "已选该课程");
        }

        String countKey = COUNT_KEY_PREFIX + request.getCampaignId();
        DefaultRedisScript<Long> script = new DefaultRedisScript<>(INCR_LUA, Long.class);
        Long result = redisTemplate.execute(script, List.of(countKey), String.valueOf(campaign.getCapacity()));
        if (result == null || result == -1) {
            throw new BusinessException(409, "课程已满");
        }

        try {
            SelectionRecord record = new SelectionRecord();
            record.setCampaignId(request.getCampaignId());
            record.setStudentId(studentUserId);
            record.setStatus(RecordStatusEnum.SELECTED);
            record.setSelectTime(LocalDateTime.now());
            selectionRecordMapper.insert(record);
            return toResponse(record, campaign);
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
        SelectionCampaign campaign = selectionCampaignMapper.selectById(record.getCampaignId());
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

        selectionRecordMapper.deleteById(recordId);

        String countKey = COUNT_KEY_PREFIX + record.getCampaignId();
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
        SelectionCampaign campaign = selectionCampaignMapper.selectById(campaignId);
        return records.stream()
                .map(r -> toResponse(r, campaign))
                .toList();
    }

    @Override
    public List<StudentCampaignResponse> listOpenCampaignsForStudent(Long studentUserId) {
        List<SelectionCampaign> campaigns = selectionCampaignMapper.selectList(
                new LambdaQueryWrapper<SelectionCampaign>()
                        .eq(SelectionCampaign::getStatus, CampaignStatusEnum.OPEN)
                        .orderByAsc(SelectionCampaign::getId));
        if (campaigns.isEmpty()) {
            return List.of();
        }
        return campaigns.stream()
                .map(c -> toStudentResponse(c, studentUserId))
                .toList();
    }

    @Override
    public StudentCampaignResponse getCampaignForStudent(Long campaignId, Long studentUserId) {
        SelectionCampaign campaign = selectionCampaignMapper.selectById(campaignId);
        if (campaign == null) {
            throw new BusinessException(404, "选课活动不存在");
        }
        return toStudentResponse(campaign, studentUserId);
    }

    private StudentCampaignResponse toStudentResponse(SelectionCampaign campaign, Long studentUserId) {
        StudentCampaignResponse resp = new StudentCampaignResponse();
        resp.setId(campaign.getId());
        resp.setName(campaign.getName());
        resp.setSemesterId(campaign.getSemesterId());
        Semester semester = semesterService.getById(campaign.getSemesterId());
        resp.setSemesterName(semester != null ? semester.getName() : null);
        resp.setStartWeek(campaign.getStartWeek());
        resp.setEndWeek(campaign.getEndWeek());
        resp.setStartTime(campaign.getStartTime());
        resp.setEndTime(campaign.getEndTime());
        resp.setStatus(campaign.getStatus());

        resp.setCourseId(campaign.getCourseId());
        resp.setCourseCode(campaign.getCourseCode());
        resp.setCredit(campaign.getCredit());
        resp.setCourseHour(campaign.getCourseHour());
        resp.setDescription(campaign.getDescription());
        resp.setCourseType(campaign.getCourseType() != null ? campaign.getCourseType().getDescription() : null);
        resp.setAllowedGradeIds(parseLongs(campaign.getAllowedGradeIds()));
        resp.setAllowedMajors(parseLongs(campaign.getAllowedMajors()));
        List<SelectionCampaignTimeRestriction> rels = selectionCampaignTimeRestrictionMapper.selectList(
                new LambdaQueryWrapper<SelectionCampaignTimeRestriction>()
                        .eq(SelectionCampaignTimeRestriction::getCampaignId, campaign.getId()));
        resp.setTimeRestrictionIds(rels.stream().map(SelectionCampaignTimeRestriction::getTimeRestrictionId).toList());
        resp.setCapacity(campaign.getCapacity());

        // 组上下文
        if (campaign.getGroupId() != null) {
            SelectionGroup group = selectionGroupMapper.selectById(campaign.getGroupId());
            if (group != null) {
                resp.setGroupId(group.getId());
                resp.setGroupName(group.getName());
                resp.setGroupMax(group.getMaxCourses());
                List<Long> siblingCampaignIds = selectionCampaignMapper.selectList(
                        new LambdaQueryWrapper<SelectionCampaign>()
                                .eq(SelectionCampaign::getGroupId, campaign.getGroupId()))
                        .stream().map(SelectionCampaign::getId).toList();
                Long selectedInGroup = selectionRecordMapper.selectCount(new LambdaQueryWrapper<SelectionRecord>()
                        .eq(SelectionRecord::getStudentId, studentUserId)
                        .in(SelectionRecord::getCampaignId, siblingCampaignIds)
                        .eq(SelectionRecord::getStatus, RecordStatusEnum.SELECTED));
                resp.setSelectedInGroup(selectedInGroup.intValue());
            }
        } else {
            resp.setSelectedInGroup(0);
        }

        // 实时选课统计
        Long selectedCount = selectionRecordMapper.selectCount(new LambdaQueryWrapper<SelectionRecord>()
                .eq(SelectionRecord::getCampaignId, campaign.getId())
                .eq(SelectionRecord::getStatus, RecordStatusEnum.SELECTED));
        resp.setSelectedCount(selectedCount.intValue());
        resp.setRemaining(Math.max(0, campaign.getCapacity() - selectedCount.intValue()));

        Long myCount = selectionRecordMapper.selectCount(new LambdaQueryWrapper<SelectionRecord>()
                .eq(SelectionRecord::getCampaignId, campaign.getId())
                .eq(SelectionRecord::getStudentId, studentUserId)
                .eq(SelectionRecord::getStatus, RecordStatusEnum.SELECTED));
        resp.setSelectedByMe(myCount > 0);

        return resp;
    }

    private SelectionRecordResponse toResponse(SelectionRecord record, SelectionCampaign campaign) {
        SelectionRecordResponse resp = new SelectionRecordResponse();
        resp.setId(record.getId());
        resp.setCampaignId(record.getCampaignId());
        resp.setStatus(record.getStatus());
        resp.setSelectTime(record.getSelectTime());
        resp.setDropTime(record.getDropTime());
        if (campaign != null) {
            resp.setCourseName(campaign.getName());
            resp.setCourseCode(campaign.getCourseCode());
            resp.setCredit(campaign.getCredit());
            resp.setCourseType(campaign.getCourseType() != null ? campaign.getCourseType().getDescription() : null);
        }
        return resp;
    }

    private boolean isAllowed(SelectionCampaign campaign, Long studentGradeId, Long studentMajorId) {
        List<Long> allowedGrades = parseLongs(campaign.getAllowedGradeIds());
        if (!allowedGrades.isEmpty()) {
            if (studentGradeId == null || !allowedGrades.contains(studentGradeId)) {
                return false;
            }
        }
        List<Long> allowedMajors = parseLongs(campaign.getAllowedMajors());
        if (!allowedMajors.isEmpty()) {
            if (studentMajorId == null || !allowedMajors.contains(studentMajorId)) {
                return false;
            }
        }
        return true;
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
