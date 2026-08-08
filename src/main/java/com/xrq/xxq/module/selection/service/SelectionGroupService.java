package com.xrq.xxq.module.selection.service;

import com.baomidou.mybatisplus.spring.service.IService;
import com.xrq.xxq.common.PageQuery;
import com.xrq.xxq.common.PageResult;
import com.xrq.xxq.module.selection.dto.SelectionGroupCreateRequest;
import com.xrq.xxq.module.selection.dto.SelectionGroupResponse;
import com.xrq.xxq.module.selection.dto.SelectionGroupUpdateRequest;
import com.xrq.xxq.module.selection.entity.SelectionGroup;

/**
 * 选课组服务：管理独立的选课组实体。
 * <p>
 * 选课活动与选课组的绑定关系通过 {@link com.xrq.xxq.module.selection.service.SelectionCampaignService}
 * 的 create/update 接口携带 {@code groupId} 字段完成，本服务不再单独提供绑定管理方法。
 * <p>
 * 组内选课上限（{@code maxCourses}）跨所有绑定该组的活动共用：学生在该组下、跨所有活动
 * 已选课程总数不能超过 {@code maxCourses}。不同选课组之间独立计数。
 */
public interface SelectionGroupService extends IService<SelectionGroup> {

    SelectionGroupResponse create(SelectionGroupCreateRequest request);

    SelectionGroupResponse update(Long groupId, SelectionGroupUpdateRequest request);

    SelectionGroupResponse getDetail(Long groupId);

    PageResult<SelectionGroupResponse> listAll(PageQuery pageQuery);

    void delete(Long groupId);
}
