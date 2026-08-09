package com.xrq.xxq.module.practice.socialpractice.service;

import java.util.List;

import com.xrq.xxq.common.PageQuery;
import com.xrq.xxq.common.PageResult;
import com.xrq.xxq.module.practice.socialpractice.dto.SocialPracticeApplicationResponse;
import com.xrq.xxq.module.practice.socialpractice.dto.SocialPracticeApplyRequest;
import com.xrq.xxq.module.practice.socialpractice.dto.SocialPracticeCreateRequest;
import com.xrq.xxq.module.practice.socialpractice.dto.SocialPracticeResponse;
import com.xrq.xxq.module.practice.socialpractice.dto.SocialPracticeReviewRequest;
import com.xrq.xxq.module.practice.socialpractice.dto.SocialPracticeUpdateRequest;
import com.xrq.xxq.module.practice.socialpractice.entity.SocialPracticeStatusEnum;

/**
 * 社会实践项目服务：发布/审核（教务）、学生申报。
 */
public interface SocialPracticeService {

    SocialPracticeResponse createPractice(SocialPracticeCreateRequest request);

    SocialPracticeResponse updatePractice(Long id, SocialPracticeUpdateRequest request);

    void changePracticeStatus(Long id, SocialPracticeStatusEnum status);

    PageResult<SocialPracticeResponse> listPractices(SocialPracticeStatusEnum status, PageQuery pageQuery);

    SocialPracticeResponse getPractice(Long id);

    List<SocialPracticeResponse> listAvailablePractices(Long studentUserId);

    SocialPracticeApplicationResponse apply(Long studentUserId, SocialPracticeApplyRequest request);

    void cancelApplication(Long studentUserId, Long applicationId);

    SocialPracticeApplicationResponse reviewApplication(Long applicationId, SocialPracticeReviewRequest request);

    List<SocialPracticeApplicationResponse> listMyApplications(Long studentUserId);

    PageResult<SocialPracticeApplicationResponse> listApplicationsByPractice(Long practiceId, PageQuery pageQuery);

    void deletePractice(Long id);
}
