package com.xrq.xxq.module.selection.service;

import java.util.List;

import com.xrq.xxq.module.selection.dto.SelectionCourseAddRequest;
import com.xrq.xxq.module.selection.dto.SelectionCourseResponse;
import com.xrq.xxq.module.selection.entity.SelectionCourse;

public interface SelectionCourseService {

    SelectionCourse add(Long campaignId, SelectionCourseAddRequest request);

    List<SelectionCourseResponse> listByCampaign(Long campaignId);

    void remove(Long campaignId, Long selectionCourseId);
}
