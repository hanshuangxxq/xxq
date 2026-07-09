package com.xrq.xxq.module.selection.service.impl;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.xrq.xxq.common.BusinessException;
import com.xrq.xxq.module.course.entity.Course;
import com.xrq.xxq.module.course.mapper.CourseMapper;
import com.xrq.xxq.module.selection.dto.SelectionCourseResponse;
import com.xrq.xxq.module.selection.dto.SelectionRecordRequest;
import com.xrq.xxq.module.selection.dto.SelectionRecordResponse;
import com.xrq.xxq.module.selection.entity.CampaignStatusEnum;
import com.xrq.xxq.module.selection.entity.RecordStatusEnum;
import com.xrq.xxq.module.selection.entity.SelectionCampaign;
import com.xrq.xxq.module.selection.entity.SelectionCourse;
import com.xrq.xxq.module.selection.entity.SelectionRecord;
import com.xrq.xxq.module.selection.mapper.SelectionCourseMapper;
import com.xrq.xxq.module.selection.mapper.SelectionRecordMapper;
import com.xrq.xxq.module.selection.service.SelectionCampaignService;
import com.xrq.xxq.module.selection.service.SelectionRecordService;

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
    private final CourseMapper courseMapper;
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
                .eq(SelectionCourse::getCampaignId, request.getCampaignId())
                .eq(SelectionCourse::getCourseId, request.getCourseId()));
        if (sc == null) {
            throw new BusinessException(404, "该课程不在可选列表中");
        }

        Long selectedCount = selectionRecordMapper.selectCount(new LambdaQueryWrapper<SelectionRecord>()
                .eq(SelectionRecord::getCampaignId, request.getCampaignId())
                .eq(SelectionRecord::getStudentId, studentUserId)
                .eq(SelectionRecord::getStatus, RecordStatusEnum.SELECTED));
        if (selectedCount >= campaign.getMaxCoursesPerStudent()) {
            throw new BusinessException(409, "超过每人选课上限 " + campaign.getMaxCoursesPerStudent() + " 门");
        }

        Long dupCount = selectionRecordMapper.selectCount(new LambdaQueryWrapper<SelectionRecord>()
                .eq(SelectionRecord::getCampaignId, request.getCampaignId())
                .eq(SelectionRecord::getStudentId, studentUserId)
                .eq(SelectionRecord::getCourseId, request.getCourseId())
                .eq(SelectionRecord::getStatus, RecordStatusEnum.SELECTED));
        if (dupCount > 0) {
            throw new BusinessException(409, "已选该课程");
        }

        String countKey = COUNT_KEY_PREFIX + request.getCampaignId() + ":" + request.getCourseId();
        DefaultRedisScript<Long> script = new DefaultRedisScript<>(INCR_LUA, Long.class);
        Long result = redisTemplate.execute(script, List.of(countKey), String.valueOf(sc.getCapacity()));
        if (result == null || result == -1) {
            throw new BusinessException(409, "课程已满");
        }

        try {
            SelectionRecord record = new SelectionRecord();
            record.setCampaignId(request.getCampaignId());
            record.setStudentId(studentUserId);
            record.setCourseId(request.getCourseId());
            record.setStatus(RecordStatusEnum.SELECTED);
            record.setSelectTime(LocalDateTime.now());
            selectionRecordMapper.insert(record);
            return toResponse(record, courseMapper.selectById(request.getCourseId()));
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

        String countKey = COUNT_KEY_PREFIX + record.getCampaignId() + ":" + record.getCourseId();
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
        List<Long> courseIds = records.stream().map(SelectionRecord::getCourseId).distinct().toList();
        Map<Long, Course> courseMap = courseMapper.selectByIds(courseIds).stream()
                .collect(Collectors.toMap(Course::getId, c -> c));
        return records.stream()
                .map(r -> toResponse(r, courseMap.get(r.getCourseId())))
                .toList();
    }

    @Override
    public List<SelectionCourseResponse> listCampaignCoursesForStudent(Long campaignId, Long studentUserId) {
        SelectionCampaign campaign = campaignService.getById(campaignId);
        if (campaign == null) {
            throw new BusinessException(404, "选课活动不存在");
        }
        List<SelectionCourse> courses = selectionCourseMapper.selectList(
                new LambdaQueryWrapper<SelectionCourse>().eq(SelectionCourse::getCampaignId, campaignId));
        if (courses.isEmpty()) {
            return List.of();
        }
        List<Long> courseIds = courses.stream().map(SelectionCourse::getCourseId).toList();
        Map<Long, Course> courseMap = courseMapper.selectByIds(courseIds).stream()
                .collect(Collectors.toMap(Course::getId, c -> c));

        List<SelectionRecord> myRecords = selectionRecordMapper.selectList(
                new LambdaQueryWrapper<SelectionRecord>()
                        .eq(SelectionRecord::getCampaignId, campaignId)
                        .eq(SelectionRecord::getStudentId, studentUserId)
                        .eq(SelectionRecord::getStatus, RecordStatusEnum.SELECTED));
        Map<Long, Long> mySelectedCourseIds = myRecords.stream()
                .collect(Collectors.toMap(SelectionRecord::getCourseId, SelectionRecord::getId, (a, b) -> a));

        return courses.stream().map(sc -> {
            Course course = courseMap.get(sc.getCourseId());
            SelectionCourseResponse resp = new SelectionCourseResponse();
            resp.setId(sc.getId());
            resp.setCampaignId(sc.getCampaignId());
            resp.setCourseId(sc.getCourseId());
            resp.setCapacity(sc.getCapacity());
            if (course != null) {
                resp.setCourseName(course.getCourseName());
                resp.setCourseCode(course.getCourseCode());
                resp.setCredit(course.getCredit());
                resp.setCourseType(course.getCourseType() != null ? course.getCourseType().getDescription() : null);
            }
            Long selectedCount = selectionRecordMapper.selectCount(new LambdaQueryWrapper<SelectionRecord>()
                    .eq(SelectionRecord::getCampaignId, campaignId)
                    .eq(SelectionRecord::getCourseId, sc.getCourseId())
                    .eq(SelectionRecord::getStatus, RecordStatusEnum.SELECTED));
            resp.setSelectedCount(selectedCount.intValue());
            resp.setRemaining(Math.max(0, sc.getCapacity() - selectedCount.intValue()));
            resp.setSelectedByMe(mySelectedCourseIds.containsKey(sc.getCourseId()));
            return resp;
        }).toList();
    }

    private SelectionRecordResponse toResponse(SelectionRecord record, Course course) {
        SelectionRecordResponse resp = new SelectionRecordResponse();
        resp.setId(record.getId());
        resp.setCampaignId(record.getCampaignId());
        resp.setCourseId(record.getCourseId());
        resp.setStatus(record.getStatus());
        resp.setSelectTime(record.getSelectTime());
        resp.setDropTime(record.getDropTime());
        if (course != null) {
            resp.setCourseName(course.getCourseName());
            resp.setCourseCode(course.getCourseCode());
            resp.setCredit(course.getCredit());
            resp.setCourseType(course.getCourseType() != null ? course.getCourseType().getDescription() : null);
        }
        return resp;
    }
}
