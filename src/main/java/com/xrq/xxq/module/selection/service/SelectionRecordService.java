package com.xrq.xxq.module.selection.service;

import java.util.List;

import com.xrq.xxq.module.selection.dto.SelectionCourseResponse;
import com.xrq.xxq.module.selection.dto.SelectionRecordRequest;
import com.xrq.xxq.module.selection.dto.SelectionRecordResponse;

public interface SelectionRecordService {

    SelectionRecordResponse select(Long studentUserId, SelectionRecordRequest request);

    void drop(Long studentUserId, Long recordId);

    List<SelectionRecordResponse> listMy(Long studentUserId, Long campaignId);

    List<SelectionCourseResponse> listCampaignCoursesForStudent(Long campaignId, Long studentUserId);
}
