package com.xrq.xxq.module.practice.graduation.service;

import java.util.List;

import com.xrq.xxq.module.practice.graduation.dto.AllocationRequest;
import com.xrq.xxq.module.practice.graduation.dto.AssignmentOverviewRow;
import com.xrq.xxq.module.practice.graduation.dto.AssignmentResponse;
import com.xrq.xxq.module.practice.graduation.dto.PickRequest;
import com.xrq.xxq.module.practice.graduation.dto.ReassignRequest;
import com.xrq.xxq.module.practice.graduation.dto.TeacherPickPoolRow;

/**
 * 师生双选与分配（§6：教师自由选择 / 院系指定分配 / 改派）。
 */
public interface GraduationAssignmentService {

    /** 教师自选学生（R-6.1~R-6.5，先占先得，锁防并发） */
    AssignmentResponse pickStudent(Long teacherUserId, PickRequest request);

    /** 教师放弃自选（R-6.6，选题截止前，仅限 TEACHER_PICK） */
    void cancelPick(Long teacherUserId, Long assignmentId);

    /** 院系指定分配（R-6.7~R-6.11，选题截止后开放） */
    AssignmentResponse allocateStudent(Long deptUserId, AllocationRequest request);

    /** 院系改派（R-6.13，选题截止后，记录原教师与原因） */
    AssignmentResponse reassignStudent(Long deptUserId, ReassignRequest request);

    /** 教师自选池：本活动参与年级 ∩ 本院系学生（Q-2 全年级，含未选题） */
    List<TeacherPickPoolRow> listTeacherPickPool(Long teacherUserId, Long campaignId);

    /** 我的匹配：学生本人 / 教师名下学生 */
    List<AssignmentResponse> listMyAssignments(Long userId, String userType, Long campaignId);

    /** 分配总览（R-6.14 教务：每教师已选/已指定/空缺 + 未分配学生清单） */
    List<AssignmentOverviewRow> listAssignmentOverview(Long academicAdminUserId, Long campaignId);

    /** 未分配学生清单（R-6.14；教务可传 collegeId 过滤，院系强制本院系） */
    List<Long> listUnassignedStudentIds(Long campaignId, String userType, Long userId, Long collegeId);
}
