package com.xrq.xxq.module.selection.service.impl;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.xrq.xxq.common.BusinessException;
import com.xrq.xxq.module.selection.dto.SelectionGroupCreateRequest;
import com.xrq.xxq.module.selection.dto.SelectionGroupResponse;
import com.xrq.xxq.module.selection.dto.SelectionGroupUpdateRequest;
import com.xrq.xxq.module.selection.entity.CampaignStatusEnum;
import com.xrq.xxq.module.selection.entity.SelectionCampaign;
import com.xrq.xxq.module.selection.entity.SelectionCampaignGroup;
import com.xrq.xxq.module.selection.entity.SelectionCourse;
import com.xrq.xxq.module.selection.entity.SelectionGroup;
import com.xrq.xxq.module.selection.mapper.SelectionCampaignGroupMapper;
import com.xrq.xxq.module.selection.mapper.SelectionCampaignMapper;
import com.xrq.xxq.module.selection.mapper.SelectionCourseMapper;
import com.xrq.xxq.module.selection.mapper.SelectionGroupMapper;
import com.xrq.xxq.module.selection.service.SelectionGroupService;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class SelectionGroupServiceImpl
        extends ServiceImpl<SelectionGroupMapper, SelectionGroup>
        implements SelectionGroupService {

    private final SelectionCampaignGroupMapper selectionCampaignGroupMapper;
    private final SelectionCampaignMapper selectionCampaignMapper;
    private final SelectionCourseMapper selectionCourseMapper;

    @Override
    public SelectionGroupResponse create(SelectionGroupCreateRequest request) {
        Long nameConflict = baseMapper.selectCount(new LambdaQueryWrapper<SelectionGroup>()
                .eq(SelectionGroup::getName, request.getName()));
        if (nameConflict > 0) {
            throw new BusinessException(409, "组名已存在");
        }
        if (request.getMaxCourses() <= 0) {
            throw new BusinessException(400, "本组选课上限必须大于0");
        }
        SelectionGroup group = new SelectionGroup();
        group.setName(request.getName());
        group.setMaxCourses(request.getMaxCourses());
        save(group);
        return toResponse(group, 0, 0);
    }

    @Override
    public SelectionGroupResponse update(Long groupId, SelectionGroupUpdateRequest request) {
        SelectionGroup group = baseMapper.selectById(groupId);
        if (group == null) {
            throw new BusinessException(404, "选课组不存在");
        }
        if (request.getName() != null && !request.getName().equals(group.getName())) {
            Long nameConflict = baseMapper.selectCount(new LambdaQueryWrapper<SelectionGroup>()
                    .eq(SelectionGroup::getName, request.getName())
                    .ne(SelectionGroup::getId, groupId));
            if (nameConflict > 0) {
                throw new BusinessException(409, "组名已存在");
            }
            group.setName(request.getName());
        }
        if (request.getMaxCourses() != null) {
            if (request.getMaxCourses() <= 0) {
                throw new BusinessException(400, "本组选课上限必须大于0");
            }
            if (!request.getMaxCourses().equals(group.getMaxCourses())) {
                Long openCount = countOpenBindings(groupId);
                if (openCount > 0) {
                    throw new BusinessException(409, "组已被开放中的活动绑定，不可修改选课上限");
                }
            }
            group.setMaxCourses(request.getMaxCourses());
        }
        updateById(group);
        Integer courseCount = countAllCoursesInGroup(groupId);
        Integer boundCount = countBoundCampaigns(groupId);
        return toResponse(group, courseCount, boundCount);
    }

    @Override
    public SelectionGroupResponse getDetail(Long groupId) {
        SelectionGroup group = baseMapper.selectById(groupId);
        if (group == null) {
            throw new BusinessException(404, "选课组不存在");
        }
        Integer courseCount = countAllCoursesInGroup(groupId);
        Integer boundCount = countBoundCampaigns(groupId);
        return toResponse(group, courseCount, boundCount);
    }

    @Override
    public List<SelectionGroupResponse> listAll() {
        List<SelectionGroup> groups = baseMapper.selectList(
                new LambdaQueryWrapper<SelectionGroup>().orderByAsc(SelectionGroup::getId));
        if (groups.isEmpty()) {
            return List.of();
        }
        List<Long> groupIds = groups.stream().map(SelectionGroup::getId).toList();
        Map<Long, Integer> courseCountMap = countAllCoursesByGroup(groupIds);
        Map<Long, Integer> boundCountMap = countBoundCampaignsByGroup(groupIds);
        return groups.stream()
                .map(g -> toResponse(g,
                        courseCountMap.getOrDefault(g.getId(), 0),
                        boundCountMap.getOrDefault(g.getId(), 0)))
                .collect(Collectors.toList());
    }

    @Override
    public void delete(Long groupId) {
        SelectionGroup group = baseMapper.selectById(groupId);
        if (group == null) {
            throw new BusinessException(404, "选课组不存在");
        }
        Long boundCount = selectionCampaignGroupMapper.selectCount(new LambdaQueryWrapper<SelectionCampaignGroup>()
                .eq(SelectionCampaignGroup::getGroupId, groupId));
        if (boundCount > 0) {
            throw new BusinessException(409, "请先从所有活动中清除选课组绑定后再删除");
        }
        removeById(groupId);
    }

    private Long countOpenBindings(Long groupId) {
        List<SelectionCampaignGroup> bindings = selectionCampaignGroupMapper.selectList(
                new LambdaQueryWrapper<SelectionCampaignGroup>()
                        .eq(SelectionCampaignGroup::getGroupId, groupId));
        if (bindings.isEmpty()) {
            return 0L;
        }
        List<Long> campaignIds = bindings.stream().map(SelectionCampaignGroup::getCampaignId).toList();
        return selectionCampaignMapper.selectCount(new LambdaQueryWrapper<SelectionCampaign>()
                .in(SelectionCampaign::getId, campaignIds)
                .eq(SelectionCampaign::getStatus, CampaignStatusEnum.OPEN));
    }

    private Integer countAllCoursesInGroup(Long groupId) {
        Long count = selectionCourseMapper.selectCount(new LambdaQueryWrapper<SelectionCourse>()
                .eq(SelectionCourse::getGroupId, groupId));
        return count == null ? 0 : count.intValue();
    }

    private Integer countBoundCampaigns(Long groupId) {
        Long count = selectionCampaignGroupMapper.selectCount(new LambdaQueryWrapper<SelectionCampaignGroup>()
                .eq(SelectionCampaignGroup::getGroupId, groupId));
        return count == null ? 0 : count.intValue();
    }

    private Map<Long, Integer> countAllCoursesByGroup(List<Long> groupIds) {
        if (groupIds.isEmpty()) {
            return Collections.emptyMap();
        }
        List<SelectionCourse> all = selectionCourseMapper.selectList(new LambdaQueryWrapper<SelectionCourse>()
                .in(SelectionCourse::getGroupId, groupIds));
        return all.stream().collect(Collectors.groupingBy(
                SelectionCourse::getGroupId,
                Collectors.summingInt(c -> 1)));
    }

    private Map<Long, Integer> countBoundCampaignsByGroup(List<Long> groupIds) {
        if (groupIds.isEmpty()) {
            return Collections.emptyMap();
        }
        List<SelectionCampaignGroup> all = selectionCampaignGroupMapper.selectList(
                new LambdaQueryWrapper<SelectionCampaignGroup>()
                        .in(SelectionCampaignGroup::getGroupId, groupIds));
        return all.stream().collect(Collectors.groupingBy(
                SelectionCampaignGroup::getGroupId,
                Collectors.summingInt(b -> 1)));
    }

    private SelectionGroupResponse toResponse(SelectionGroup group, Integer courseCount,
                                              Integer boundCampaignCount) {
        SelectionGroupResponse resp = new SelectionGroupResponse();
        resp.setId(group.getId());
        resp.setName(group.getName());
        resp.setMaxCourses(group.getMaxCourses());
        resp.setCourseCount(courseCount);
        resp.setBoundCampaignCount(boundCampaignCount);
        resp.setCreateTime(group.getCreateTime());
        return resp;
    }
}
