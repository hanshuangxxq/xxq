package com.xrq.xxq.module.practice.internship.service;

import java.util.List;

import com.xrq.xxq.common.PageQuery;
import com.xrq.xxq.common.PageResult;
import com.xrq.xxq.module.practice.internship.dto.TrainingCreateRequest;
import com.xrq.xxq.module.practice.internship.dto.TrainingEnrollmentResponse;
import com.xrq.xxq.module.practice.internship.dto.TrainingResponse;
import com.xrq.xxq.module.practice.internship.dto.TrainingUpdateRequest;
import com.xrq.xxq.module.practice.internship.entity.TrainingStatusEnum;

/**
 * 培训课程服务：发布（院系管理者）、学生报名（即报即生效）。
 */
public interface TrainingService {

    TrainingResponse createCourse(Long creatorUserId, String userType, TrainingCreateRequest request);

    TrainingResponse updateCourse(Long id, TrainingUpdateRequest request, Long operatorUserId, String userType);

    void changeCourseStatus(Long id, TrainingStatusEnum status, Long operatorUserId, String userType);

    PageResult<TrainingResponse> listCourses(Long operatorUserId, String userType,
                                             Long teacherId, TrainingStatusEnum status, PageQuery pageQuery);

    TrainingResponse getCourse(Long id);

    List<TrainingResponse> listAvailableCourses(Long studentUserId);

    TrainingEnrollmentResponse enroll(Long studentUserId, Long courseId);

    void cancelEnroll(Long studentUserId, Long enrollmentId);

    List<TrainingEnrollmentResponse> listMyEnrollments(Long studentUserId);

    PageResult<TrainingEnrollmentResponse> listEnrollmentsByCourse(Long courseId, Long operatorUserId,
                                                                   String userType, PageQuery pageQuery);

    void deleteCourse(Long id, Long operatorUserId, String userType);
}
