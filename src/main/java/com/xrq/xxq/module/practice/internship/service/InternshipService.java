package com.xrq.xxq.module.practice.internship.service;

import java.util.List;

import com.xrq.xxq.common.PageQuery;
import com.xrq.xxq.common.PageResult;
import com.xrq.xxq.module.practice.internship.dto.InternshipApplicationResponse;
import com.xrq.xxq.module.practice.internship.dto.InternshipApplyRequest;
import com.xrq.xxq.module.practice.internship.dto.InternshipCreateRequest;
import com.xrq.xxq.module.practice.internship.dto.InternshipResponse;
import com.xrq.xxq.module.practice.internship.dto.InternshipReviewRequest;
import com.xrq.xxq.module.practice.internship.dto.InternshipUpdateRequest;
import com.xrq.xxq.module.practice.internship.entity.InternshipStatusEnum;

/**
 * 实习项目服务：发布/审核报名（教师/教务）、学生报名。
 */
public interface InternshipService {

    InternshipResponse createInternship(Long creatorUserId, String userType, InternshipCreateRequest request);

    InternshipResponse updateInternship(Long id, InternshipUpdateRequest request, Long operatorUserId, String userType);

    void changeInternshipStatus(Long id, InternshipStatusEnum status, Long operatorUserId, String userType);

    PageResult<InternshipResponse> listInternships(Long operatorUserId, String userType,
                                                   Long supervisorId, InternshipStatusEnum status, PageQuery pageQuery);

    InternshipResponse getInternship(Long id);

    List<InternshipResponse> listAvailableInternships(Long studentUserId);

    InternshipApplicationResponse applyInternship(Long studentUserId, InternshipApplyRequest request);

    void cancelApplication(Long studentUserId, Long applicationId);

    InternshipApplicationResponse reviewApplication(Long applicationId, InternshipReviewRequest request,
                                                   Long operatorUserId, String userType);

    List<InternshipApplicationResponse> listMyApplications(Long studentUserId);

    PageResult<InternshipApplicationResponse> listApplicationsByInternship(Long internshipId, Long operatorUserId,
                                                                           String userType, PageQuery pageQuery);

    void deleteInternship(Long id, Long operatorUserId, String userType);
}
