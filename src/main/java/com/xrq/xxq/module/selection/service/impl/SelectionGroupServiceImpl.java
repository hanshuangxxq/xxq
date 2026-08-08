package com.xrq.xxq.module.selection.service.impl;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.xrq.xxq.common.BusinessException;
import com.xrq.xxq.common.PageQuery;
import com.xrq.xxq.common.PageResult;
import com.xrq.xxq.module.selection.dto.SelectionGroupCreateRequest;
import com.xrq.xxq.module.selection.dto.SelectionGroupResponse;
import com.xrq.xxq.module.selection.dto.SelectionGroupUpdateRequest;
import com.xrq.xxq.module.selection.entity.CampaignStatusEnum;
import com.xrq.xxq.module.selection.entity.SelectionCampaign;
import com.xrq.xxq.module.selection.entity.SelectionGroup;
import com.xrq.xxq.module.selection.mapper.SelectionCampaignMapper;
import com.xrq.xxq.module.selection.mapper.SelectionGroupMapper;
import com.xrq.xxq.module.selection.service.SelectionGroupService;
import com.xrq.xxq.util.ParamValidator;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class SelectionGroupServiceImpl
        extends ServiceImpl<SelectionGroupMapper, SelectionGroup>
        implements SelectionGroupService {

    private final SelectionCampaignMapper selectionCampaignMapper;

    @Override
    public SelectionGroupResponse create(SelectionGroupCreateRequest request) {
        Long nameConflict = baseMapper.selectCount(new LambdaQueryWrapper<SelectionGroup>()
                .eq(SelectionGroup::getName, request.getName()));
        if (nameConflict > 0) {
            throw new BusinessException(409, "组名已存在");
        }
        ParamValidator.requirePositive(request.getMaxCourses(), "本组选课上限");
        SelectionGroup group = new SelectionGroup();
        group.setName(request.getName());
        group.setMaxCourses(request.getMaxCourses());
        save(group);
        return toResponse(group, 0);
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
            ParamValidator.requirePositive(request.getMaxCourses(), "本组选课上限");
            if (!request.getMaxCourses().equals(group.getMaxCourses())) {
                Long openCount = countOpenCampaignsInGroup(groupId);
                if (openCount > 0) {
                    throw new BusinessException(409, "组已被开放中的活动绑定，不可修改选课上限");
                }
            }
            group.setMaxCourses(request.getMaxCourses());
        }
        updateById(group);
        Integer campaignCount = countCampaignsInGroup(groupId);
        return toResponse(group, campaignCount);
    }

    @Override
    public SelectionGroupResponse getDetail(Long groupId) {
        SelectionGroup group = baseMapper.selectById(groupId);
        if (group == null) {
            throw new BusinessException(404, "选课组不存在");
        }
        Integer campaignCount = countCampaignsInGroup(groupId);
        return toResponse(group, campaignCount);
    }

    @Override
    public PageResult<SelectionGroupResponse> listAll(PageQuery pageQuery) {
        LambdaQueryWrapper<SelectionGroup> wrapper = new LambdaQueryWrapper<SelectionGroup>()
                .orderByAsc(SelectionGroup::getId);
        Page<SelectionGroup> page = baseMapper.selectPage(pageQuery.toPage(), wrapper);
        List<SelectionGroup> groups = page.getRecords();
        if (groups.isEmpty()) {
            return PageResult.of(page, List.of());
        }
        List<Long> groupIds = groups.stream().map(SelectionGroup::getId).toList();
        Map<Long, Integer> campaignCountMap = countCampaignsByGroup(groupIds);
        List<SelectionGroupResponse> records = groups.stream()
                .map(g -> toResponse(g, campaignCountMap.getOrDefault(g.getId(), 0)))
                .collect(Collectors.toList());
        return PageResult.of(page, records);
    }

    @Override
    public void delete(Long groupId) {
        SelectionGroup group = baseMapper.selectById(groupId);
        if (group == null) {
            throw new BusinessException(404, "选课组不存在");
        }
        Long boundCount = selectionCampaignMapper.selectCount(new LambdaQueryWrapper<SelectionCampaign>()
                .eq(SelectionCampaign::getGroupId, groupId));
        if (boundCount > 0) {
            throw new BusinessException(409, "请先清除绑定到本组的活动后再删除");
        }
        removeById(groupId);
    }

    private Long countOpenCampaignsInGroup(Long groupId) {
        return selectionCampaignMapper.selectCount(new LambdaQueryWrapper<SelectionCampaign>()
                .eq(SelectionCampaign::getGroupId, groupId)
                .eq(SelectionCampaign::getStatus, CampaignStatusEnum.OPEN));
    }

    private Integer countCampaignsInGroup(Long groupId) {
        Long count = selectionCampaignMapper.selectCount(new LambdaQueryWrapper<SelectionCampaign>()
                .eq(SelectionCampaign::getGroupId, groupId));
        return count == null ? 0 : count.intValue();
    }

    private Map<Long, Integer> countCampaignsByGroup(List<Long> groupIds) {
        if (groupIds.isEmpty()) {
            return Collections.emptyMap();
        }
        List<SelectionCampaign> all = selectionCampaignMapper.selectList(
                new LambdaQueryWrapper<SelectionCampaign>()
                        .in(SelectionCampaign::getGroupId, groupIds));
        return all.stream().collect(Collectors.groupingBy(
                SelectionCampaign::getGroupId,
                Collectors.summingInt(c -> 1)));
    }

    private SelectionGroupResponse toResponse(SelectionGroup group, Integer campaignCount) {
        SelectionGroupResponse resp = new SelectionGroupResponse();
        resp.setId(group.getId());
        resp.setName(group.getName());
        resp.setMaxCourses(group.getMaxCourses());
        resp.setCampaignCount(campaignCount);
        resp.setCreateTime(group.getCreateTime());
        return resp;
    }
}
