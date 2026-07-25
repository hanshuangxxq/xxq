package com.xrq.xxq.module.selection.service;

import java.util.List;

import com.baomidou.mybatisplus.spring.service.IService;
import com.xrq.xxq.module.selection.dto.CampaignGroupBindingRequest;
import com.xrq.xxq.module.selection.dto.SelectionGroupCreateRequest;
import com.xrq.xxq.module.selection.dto.SelectionGroupResponse;
import com.xrq.xxq.module.selection.dto.SelectionGroupUpdateRequest;
import com.xrq.xxq.module.selection.entity.SelectionGroup;

/**
 * 选课组服务：管理独立的选课组实体，以及选课活动与选课组的绑定关系。
 * <p>
 * 绑定约束：一个选课组可被多个选课活动绑定，但一个选课活动只能绑定一个选课组。
 * 该约束在服务层实现，不在数据库层加唯一约束。
 * <p>
 * 组内选课上限（{@code maxCourses}）跨所有绑定该组的活动共用：学生在该组下、跨所有活动
 * 已选课程总数不能超过 {@code maxCourses}。不同选课组之间独立计数。
 */
public interface SelectionGroupService extends IService<SelectionGroup> {

    SelectionGroupResponse create(SelectionGroupCreateRequest request);

    SelectionGroupResponse update(Long groupId, SelectionGroupUpdateRequest request);

    SelectionGroupResponse getDetail(Long groupId);

    List<SelectionGroupResponse> listAll();

    void delete(Long groupId);

    List<SelectionGroupResponse> listByCampaign(Long campaignId);

    void bindToCampaign(Long campaignId, CampaignGroupBindingRequest request);

    void unbindFromCampaign(Long campaignId, Long groupId);
}
