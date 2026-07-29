package com.xrq.xxq.module.selection.service;

import java.util.List;

import com.baomidou.mybatisplus.spring.service.IService;
import com.xrq.xxq.module.selection.dto.CampaignCreateRequest;
import com.xrq.xxq.module.selection.dto.CampaignResponse;
import com.xrq.xxq.module.selection.dto.CampaignUpdateRequest;
import com.xrq.xxq.module.selection.entity.SelectionCampaign;

public interface SelectionCampaignService extends IService<SelectionCampaign> {

    CampaignResponse create(CampaignCreateRequest request);

    CampaignResponse update(Long id, CampaignUpdateRequest request);

    CampaignResponse getDetail(Long id);

    List<CampaignResponse> listAll();

    /**
     * 列出可绑定到指定选课组的选课活动。
     * <p>
     * 一个活动只能绑定一个选课组，因此返回结果排除已绑定到其它选课组的活动，
     * 但保留未绑定任何组的活动以及已绑定到本组的活动（便于前端展示解绑入口）。
     */
    List<CampaignResponse> listBindableForGroup(Long groupId);

    void delete(Long id);

    /**
     * 按 courseId 级联删除对应的选课活动。
     * 用于删除 course 表记录时联动清理：不校验 DRAFT 状态，不删除衍生 Course（由调用方删除）。
     * 若无对应活动，静默返回。
     */
    void deleteByCourseId(Long courseId);

    void open(Long id);

    void close(Long id);

    void finalizeCampaign(Long id);
}
