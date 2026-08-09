package com.xrq.xxq.module.practice.graduation.service;

import java.util.List;

import com.xrq.xxq.common.PageQuery;
import com.xrq.xxq.common.PageResult;
import com.xrq.xxq.module.practice.graduation.dto.SelectionApplyRequest;
import com.xrq.xxq.module.practice.graduation.dto.SelectionResponse;
import com.xrq.xxq.module.practice.graduation.dto.SelectionReviewRequest;
import com.xrq.xxq.module.practice.graduation.dto.TopicCreateRequest;
import com.xrq.xxq.module.practice.graduation.dto.TopicResponse;
import com.xrq.xxq.module.practice.graduation.dto.TopicUpdateRequest;
import com.xrq.xxq.module.practice.graduation.entity.TopicStatusEnum;

/**
 * 毕业设计选题服务：教师发布/审核、学生申请、教务查看。
 */
public interface GraduationService {

    TopicResponse createTopic(Long teacherUserId, TopicCreateRequest request);

    TopicResponse updateTopic(Long topicId, TopicUpdateRequest request, Long operatorUserId, String userType);

    /** 开放/关闭选题。 */
    void changeTopicStatus(Long topicId, TopicStatusEnum status, Long operatorUserId, String userType);

    /**
     * 选题列表。
     * <ul>
     *   <li>教务：全部，teacherId 可选过滤。</li>
     *   <li>教师：仅本人发布的选题。</li>
     * </ul>
     */
    PageResult<TopicResponse> listTopics(Long operatorUserId, String userType,
                                         Long teacherId, TopicStatusEnum status, PageQuery pageQuery);

    TopicResponse getTopic(Long topicId);

    /** 学生可见的开放选题（OPEN 且未满）。 */
    List<TopicResponse> listAvailableTopics(Long studentUserId);

    /** 学生申请选题。 */
    SelectionResponse applyTopic(Long studentUserId, SelectionApplyRequest request);

    /** 学生撤销申请。 */
    void cancelApplication(Long studentUserId, Long applicationId);

    /** 审核申请（通过/驳回）。教师仅本人选题，教务任意。 */
    SelectionResponse reviewApplication(Long applicationId, SelectionReviewRequest request, Long operatorUserId, String userType);

    /** 学生查看我的申请。 */
    List<SelectionResponse> listMyApplications(Long studentUserId);

    /** 按选题查看申请列表（教师查本选题 / 教务查任意）。 */
    PageResult<SelectionResponse> listApplicationsByTopic(Long topicId, Long operatorUserId,
                                                          String userType, PageQuery pageQuery);

    void deleteTopic(Long topicId, Long operatorUserId, String userType);
}
