package com.xrq.xxq.module.selection.service;

import java.util.List;

import com.baomidou.mybatisplus.spring.service.IService;
import com.xrq.xxq.module.selection.dto.SelectionGroupCreateRequest;
import com.xrq.xxq.module.selection.dto.SelectionGroupResponse;
import com.xrq.xxq.module.selection.dto.SelectionGroupUpdateRequest;
import com.xrq.xxq.module.selection.entity.SelectionGroup;

public interface SelectionGroupService extends IService<SelectionGroup> {

    SelectionGroupResponse create(Long campaignId, SelectionGroupCreateRequest request);

    SelectionGroupResponse update(Long campaignId, Long groupId, SelectionGroupUpdateRequest request);

    List<SelectionGroupResponse> listByCampaign(Long campaignId);

    void delete(Long campaignId, Long groupId);
}
