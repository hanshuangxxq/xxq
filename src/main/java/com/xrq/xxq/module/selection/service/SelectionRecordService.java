package com.xrq.xxq.module.selection.service;

import java.util.List;

import com.xrq.xxq.module.selection.dto.SelectionRecordRequest;
import com.xrq.xxq.module.selection.dto.SelectionRecordResponse;
import com.xrq.xxq.module.selection.dto.StudentCampaignResponse;

public interface SelectionRecordService {

    SelectionRecordResponse select(Long studentUserId, SelectionRecordRequest request);

    void drop(Long studentUserId, Long recordId);

    List<SelectionRecordResponse> listMy(Long studentUserId, Long campaignId);

    /** 列出所有 OPEN 状态的活动（含课程信息 + 组上下文 + 选课状态），供学生端浏览。 */
    List<StudentCampaignResponse> listOpenCampaignsForStudent(Long studentUserId);

    /** 查询单个活动详情（含课程信息 + 组上下文 + 选课状态），供学生端查看。 */
    StudentCampaignResponse getCampaignForStudent(Long campaignId, Long studentUserId);
}
