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

    void delete(Long id);

    void open(Long id);

    void close(Long id);

    void finalizeCampaign(Long id);
}
