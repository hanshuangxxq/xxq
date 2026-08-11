package com.xrq.xxq.module.practice.graduation.service;

import java.util.List;

import com.xrq.xxq.common.PageQuery;
import com.xrq.xxq.common.PageResult;
import com.xrq.xxq.module.practice.graduation.dto.CampaignCreateRequest;
import com.xrq.xxq.module.practice.graduation.dto.CampaignResponse;
import com.xrq.xxq.module.practice.graduation.dto.CampaignUpdateRequest;
import com.xrq.xxq.module.practice.graduation.entity.CampaignStatusEnum;

/**
 * 毕设活动管理（§4：创建/配置/状态）。
 */
public interface GraduationCampaignService {

    /** 教务创建活动（R-4.2 同年级仅一个进行中活动） */
    CampaignResponse createCampaign(Long operatorUserId, String operatorType, CampaignCreateRequest request);

    /** 教务更新活动（R-4.1 选题开始后仅允许延长截止/上调名额） */
    CampaignResponse updateCampaign(Long operatorUserId, String operatorType, Long id, CampaignUpdateRequest request);

    /** 教务切换活动状态 DRAFT/OPEN/CLOSED */
    void changeCampaignStatus(Long operatorUserId, String operatorType, Long id, CampaignStatusEnum status);

    /** 教务分页查看活动 */
    PageResult<CampaignResponse> listCampaigns(CampaignStatusEnum status, PageQuery pageQuery);

    /** 活动详情（四角色可见） */
    CampaignResponse getCampaign(Long id);

    /** 学生可见的进行中活动（参与年级匹配） */
    List<CampaignResponse> listAvailableCampaignsForStudent(Long studentUserId);

    /** 教师/院系活动选择器（返回所有非草稿活动，供下拉选择用） */
    List<CampaignResponse> listCampaignsForSelector();
}
