package com.xrq.xxq.module.selection.service.impl;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.xrq.xxq.common.BusinessException;
import com.xrq.xxq.module.course.entity.Course;
import com.xrq.xxq.module.course.mapper.CourseMapper;
import com.xrq.xxq.module.selection.dto.SelectionCourseAddRequest;
import com.xrq.xxq.module.selection.dto.SelectionCourseResponse;
import com.xrq.xxq.module.selection.entity.CampaignStatusEnum;
import com.xrq.xxq.module.selection.entity.SelectionCampaign;
import com.xrq.xxq.module.selection.entity.SelectionCourse;
import com.xrq.xxq.module.selection.entity.SelectionCourseTimeRestriction;
import com.xrq.xxq.module.selection.entity.SelectionGroup;
import com.xrq.xxq.module.selection.mapper.SelectionCampaignMapper;
import com.xrq.xxq.module.selection.mapper.SelectionCourseMapper;
import com.xrq.xxq.module.selection.mapper.SelectionCourseTimeRestrictionMapper;
import com.xrq.xxq.module.selection.mapper.SelectionGroupMapper;
import com.xrq.xxq.module.selection.service.SelectionCourseService;
import com.xrq.xxq.module.time.entity.TimeRestriction;
import com.xrq.xxq.module.time.mapper.TimeRestrictionMapper;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class SelectionCourseServiceImpl implements SelectionCourseService {

    private static final String SOURCE_SELECTION_COURSE = "SELECTION_COURSE";

    private final SelectionCourseMapper selectionCourseMapper;
    private final SelectionGroupMapper selectionGroupMapper;
    private final SelectionCourseTimeRestrictionMapper selectionCourseTimeRestrictionMapper;
    private final TimeRestrictionMapper timeRestrictionMapper;
    private final CourseMapper courseMapper;
    private final SelectionCampaignMapper selectionCampaignMapper;

    @Override
    @Transactional
    public SelectionCourse add(Long campaignId, SelectionCourseAddRequest request) {
        SelectionCampaign campaign = selectionCampaignMapper.selectById(campaignId);
        if (campaign == null) {
            throw new BusinessException(404, "选课活动不存在");
        }
        if (campaign.getStatus() != CampaignStatusEnum.DRAFT) {
            throw new BusinessException(409, "仅草稿状态可配置可选课程");
        }
        SelectionGroup group = selectionGroupMapper.selectById(request.getGroupId());
        if (group == null || !group.getCampaignId().equals(campaignId)) {
            throw new BusinessException(400, "选课组不存在或不属于本活动");
        }
        if (request.getCapacity() == null || request.getCapacity() <= 0) {
            throw new BusinessException(400, "容量必须大于0");
        }
        if (request.getCredit() == null || request.getCredit() < 0) {
            throw new BusinessException(400, "学分必须 >= 0");
        }
        if (request.getCourseName() == null || request.getCourseName().isBlank()) {
            throw new BusinessException(400, "课程名称不能为空");
        }
        if (request.getCourseCode() == null || request.getCourseCode().isBlank()) {
            throw new BusinessException(400, "课程编号不能为空");
        }
        Long exists = selectionCourseMapper.selectCount(new LambdaQueryWrapper<SelectionCourse>()
                .eq(SelectionCourse::getCampaignId, campaignId)
                .eq(SelectionCourse::getCourseCode, request.getCourseCode()));
        if (exists > 0) {
            throw new BusinessException(409, "该课程编号已在本活动中存在");
        }

        // 1. 在 course 表插入衍生记录（source = SELECTION_COURSE）
        Course derivedCourse = new Course();
        derivedCourse.setCourseName(request.getCourseName());
        derivedCourse.setCourseCode("SEL-" + campaignId + "-" + request.getCourseCode());
        derivedCourse.setCredit(request.getCredit());
        derivedCourse.setCourseHour(request.getCourseHour());
        derivedCourse.setDescription(request.getDescription());
        derivedCourse.setCourseType(request.getCourseType());
        derivedCourse.setSource(SOURCE_SELECTION_COURSE);
        courseMapper.insert(derivedCourse);

        // 2. 插入 selection_course 记录
        SelectionCourse sc = new SelectionCourse();
        sc.setCampaignId(campaignId);
        sc.setGroupId(request.getGroupId());
        sc.setCourseId(derivedCourse.getId());
        sc.setCourseName(request.getCourseName());
        sc.setCourseCode(request.getCourseCode());
        sc.setCredit(request.getCredit());
        sc.setCourseHour(request.getCourseHour());
        sc.setDescription(request.getDescription());
        sc.setCourseType(request.getCourseType());
        sc.setAllowedGradeIds(joinLongs(request.getAllowedGradeIds()));
        sc.setAllowedMajors(joinLongs(request.getAllowedMajors()));
        sc.setCapacity(request.getCapacity());
        selectionCourseMapper.insert(sc);

        // 3. 校验并绑定 TimeRestriction（RESERVED 类型，courseId 指向衍生 course）
        if (request.getTimeRestrictionIds() != null && !request.getTimeRestrictionIds().isEmpty()) {
            for (Long trId : request.getTimeRestrictionIds()) {
                TimeRestriction tr = timeRestrictionMapper.selectById(trId);
                if (tr == null) {
                    throw new BusinessException(400, "时段限制 " + trId + " 不存在");
                }
                if (!"RESERVED".equals(tr.getRestrictionType())) {
                    throw new BusinessException(400, "时段限制 " + trId + " 必须为 RESERVED 类型");
                }
                if (tr.getCourseId() != null && !tr.getCourseId().equals(derivedCourse.getId())) {
                    throw new BusinessException(400, "时段限制 " + trId + " 已预留给其他课程");
                }
                if (tr.getCourseId() == null) {
                    tr.setCourseId(derivedCourse.getId());
                    timeRestrictionMapper.updateById(tr);
                }
                SelectionCourseTimeRestriction rel = new SelectionCourseTimeRestriction();
                rel.setSelectionCourseId(sc.getId());
                rel.setTimeRestrictionId(trId);
                selectionCourseTimeRestrictionMapper.insert(rel);
            }
        }

        return sc;
    }

    @Override
    public List<SelectionCourseResponse> listByCampaign(Long campaignId) {
        SelectionCampaign campaign = selectionCampaignMapper.selectById(campaignId);
        if (campaign == null) {
            throw new BusinessException(404, "选课活动不存在");
        }
        List<SelectionCourse> courses = selectionCourseMapper.selectList(
                new LambdaQueryWrapper<SelectionCourse>().eq(SelectionCourse::getCampaignId, campaignId));
        if (courses.isEmpty()) {
            return List.of();
        }
        Map<Long, SelectionGroup> groupMap = selectionGroupMapper.selectList(
                new LambdaQueryWrapper<SelectionGroup>().eq(SelectionGroup::getCampaignId, campaignId))
                .stream().collect(Collectors.toMap(SelectionGroup::getId, g -> g));
        Map<Long, List<Long>> trMap = loadTimeRestrictions(courses.stream().map(SelectionCourse::getId).toList());
        return courses.stream()
                .map(sc -> toResponse(sc, groupMap.get(sc.getGroupId()),
                        trMap.getOrDefault(sc.getId(), List.of())))
                .toList();
    }

    @Override
    @Transactional
    public void remove(Long campaignId, Long selectionCourseId) {
        SelectionCampaign campaign = selectionCampaignMapper.selectById(campaignId);
        if (campaign == null) {
            throw new BusinessException(404, "选课活动不存在");
        }
        if (campaign.getStatus() != CampaignStatusEnum.DRAFT) {
            throw new BusinessException(409, "仅草稿状态可移除可选课程");
        }
        SelectionCourse sc = selectionCourseMapper.selectOne(new LambdaQueryWrapper<SelectionCourse>()
                .eq(SelectionCourse::getId, selectionCourseId)
                .eq(SelectionCourse::getCampaignId, campaignId));
        if (sc == null) {
            throw new BusinessException(404, "可选课程不存在");
        }

        // 级联清理：关联表 -> TimeRestriction -> 衍生 course -> selection_course
        selectionCourseTimeRestrictionMapper.delete(new LambdaQueryWrapper<SelectionCourseTimeRestriction>()
                .eq(SelectionCourseTimeRestriction::getSelectionCourseId, selectionCourseId));

        List<TimeRestriction> trList = timeRestrictionMapper.selectList(new LambdaQueryWrapper<TimeRestriction>()
                .eq(TimeRestriction::getCourseId, sc.getCourseId()));
        for (TimeRestriction tr : trList) {
            timeRestrictionMapper.deleteById(tr.getId());
        }

        selectionCourseMapper.deleteById(selectionCourseId);
        courseMapper.deleteById(sc.getCourseId());
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

    private SelectionCourseResponse toResponse(SelectionCourse sc, SelectionGroup group, List<Long> trIds) {
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
        resp.setTimeRestrictionIds(trIds);
        if (group != null) {
            resp.setGroupId(group.getId());
            resp.setGroupName(group.getName());
        }
        resp.setCapacity(sc.getCapacity());
        resp.setSelectedCount(0);
        resp.setRemaining(sc.getCapacity());
        resp.setSelectedByMe(false);
        return resp;
    }

    private String joinLongs(List<Long> vals) {
        if (vals == null || vals.isEmpty()) {
            return null;
        }
        String joined = vals.stream().filter(java.util.Objects::nonNull)
                .map(String::valueOf)
                .collect(Collectors.joining(","));
        return joined.isEmpty() ? null : joined;
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
