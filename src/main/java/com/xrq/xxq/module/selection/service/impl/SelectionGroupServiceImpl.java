package com.xrq.xxq.module.selection.service.impl;

import java.util.List;
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
import com.xrq.xxq.module.selection.entity.SelectionCourse;
import com.xrq.xxq.module.selection.entity.SelectionGroup;
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

    private final SelectionCourseMapper selectionCourseMapper;
    private final SelectionCampaignMapper selectionCampaignMapper;

    @Override
    public SelectionGroupResponse create(Long campaignId, SelectionGroupCreateRequest request) {
        SelectionCampaign campaign = loadCampaignInDraft(campaignId);
        Long nameConflict = baseMapper.selectCount(new LambdaQueryWrapper<SelectionGroup>()
                .eq(SelectionGroup::getCampaignId, campaignId)
                .eq(SelectionGroup::getName, request.getName()));
        if (nameConflict > 0) {
            throw new BusinessException(409, "组名已存在");
        }
        if (request.getMaxCourses() <= 0) {
            throw new BusinessException(400, "本组选课上限必须大于0");
        }
        SelectionGroup group = new SelectionGroup();
        group.setCampaignId(campaignId);
        group.setName(request.getName());
        group.setMaxCourses(request.getMaxCourses());
        group.setSortOrder(request.getSortOrder() == null ? 0 : request.getSortOrder());
        save(group);
        return toResponse(group, 0);
    }

    @Override
    public SelectionGroupResponse update(Long campaignId, Long groupId, SelectionGroupUpdateRequest request) {
        loadCampaignInDraft(campaignId);
        SelectionGroup group = baseMapper.selectById(groupId);
        if (group == null || !group.getCampaignId().equals(campaignId)) {
            throw new BusinessException(404, "选课组不存在");
        }
        if (request.getName() != null) {
            if (!request.getName().equals(group.getName())) {
                Long nameConflict = baseMapper.selectCount(new LambdaQueryWrapper<SelectionGroup>()
                        .eq(SelectionGroup::getCampaignId, campaignId)
                        .eq(SelectionGroup::getName, request.getName()));
                if (nameConflict > 0) {
                    throw new BusinessException(409, "组名已存在");
                }
            }
            group.setName(request.getName());
        }
        if (request.getMaxCourses() != null) {
            if (request.getMaxCourses() <= 0) {
                throw new BusinessException(400, "本组选课上限必须大于0");
            }
            group.setMaxCourses(request.getMaxCourses());
        }
        if (request.getSortOrder() != null) {
            group.setSortOrder(request.getSortOrder());
        }
        updateById(group);
        Integer courseCount = countCoursesInGroup(groupId);
        return toResponse(group, courseCount);
    }

    @Override
    public List<SelectionGroupResponse> listByCampaign(Long campaignId) {
        SelectionCampaign campaign = selectionCampaignMapper.selectById(campaignId);
        if (campaign == null) {
            throw new BusinessException(404, "选课活动不存在");
        }
        List<SelectionGroup> groups = baseMapper.selectList(new LambdaQueryWrapper<SelectionGroup>()
                .eq(SelectionGroup::getCampaignId, campaignId)
                .orderByAsc(SelectionGroup::getSortOrder)
                .orderByAsc(SelectionGroup::getId));
        if (groups.isEmpty()) {
            return List.of();
        }
        return groups.stream()
                .map(g -> toResponse(g, countCoursesInGroup(g.getId())))
                .collect(Collectors.toList());
    }

    @Override
    public void delete(Long campaignId, Long groupId) {
        loadCampaignInDraft(campaignId);
        SelectionGroup group = baseMapper.selectById(groupId);
        if (group == null || !group.getCampaignId().equals(campaignId)) {
            throw new BusinessException(404, "选课组不存在");
        }
        Integer courseCount = countCoursesInGroup(groupId);
        if (courseCount > 0) {
            throw new BusinessException(409, "组内仍有课程，无法删除");
        }
        removeById(groupId);
    }

    private SelectionCampaign loadCampaignInDraft(Long campaignId) {
        SelectionCampaign campaign = selectionCampaignMapper.selectById(campaignId);
        if (campaign == null) {
            throw new BusinessException(404, "选课活动不存在");
        }
        if (campaign.getStatus() != CampaignStatusEnum.DRAFT) {
            throw new BusinessException(409, "仅草稿状态可配置选课组");
        }
        return campaign;
    }

    private Integer countCoursesInGroup(Long groupId) {
        Long count = selectionCourseMapper.selectCount(new LambdaQueryWrapper<SelectionCourse>()
                .eq(SelectionCourse::getGroupId, groupId));
        return count == null ? 0 : count.intValue();
    }

    private SelectionGroupResponse toResponse(SelectionGroup group, Integer courseCount) {
        SelectionGroupResponse resp = new SelectionGroupResponse();
        resp.setId(group.getId());
        resp.setCampaignId(group.getCampaignId());
        resp.setName(group.getName());
        resp.setMaxCourses(group.getMaxCourses());
        resp.setSortOrder(group.getSortOrder());
        resp.setCourseCount(courseCount);
        resp.setCreateTime(group.getCreateTime());
        return resp;
    }
}
