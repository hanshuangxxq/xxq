package com.xrq.xxq.module.coursework.assignment.service;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.xrq.xxq.common.BusinessException;
import com.xrq.xxq.module.coursework.assignment.dto.RegularScoreSyncView;
import com.xrq.xxq.module.coursework.assignment.entity.AssignmentStatusEnum;
import com.xrq.xxq.module.coursework.assignment.entity.CourseAssignment;
import com.xrq.xxq.module.coursework.assignment.entity.CourseAssignmentSubmission;
import com.xrq.xxq.module.coursework.assignment.entity.SubmissionStatusEnum;
import com.xrq.xxq.module.coursework.assignment.mapper.CourseAssignmentMapper;
import com.xrq.xxq.module.coursework.assignment.mapper.CourseAssignmentSubmissionMapper;
import com.xrq.xxq.module.coursework.common.CourseGroupResolver;
import com.xrq.xxq.module.coursework.common.RegularScoreCalculator;
import com.xrq.xxq.module.score.dto.ApplyRegularScoreResult;
import com.xrq.xxq.module.score.service.ScoreService;
import com.xrq.xxq.module.teachinfo.entity.TeachInfo;

import lombok.RequiredArgsConstructor;

/**
 * 作业成绩合成平时分：教师手动触发，覆盖式重算（幂等）。
 * 读取在本类（不加事务），写入委托 score 模块的 applyRegularScores（自带事务）。
 */
@Service
@RequiredArgsConstructor
public class RegularScoreSyncService {

    private final CourseAssignmentMapper assignmentMapper;
    private final CourseAssignmentSubmissionMapper submissionMapper;
    private final CourseGroupResolver groupResolver;
    private final ScoreService scoreService;

    public RegularScoreSyncView sync(Long teacherUserId, Long teachInfoId) {
        TeachInfo anchor = groupResolver.requireOwnedAnchor(teachInfoId, teacherUserId);
        List<CourseAssignment> assignments = assignmentMapper.selectList(
                new LambdaQueryWrapper<CourseAssignment>()
                        .eq(CourseAssignment::getTeachInfoId, anchor.getId())
                        .in(CourseAssignment::getStatus,
                                AssignmentStatusEnum.PUBLISHED, AssignmentStatusEnum.CLOSED));
        if (assignments.isEmpty()) {
            throw new BusinessException(400, "该授课组暂无已发布作业");
        }
        List<Long> assignmentIds = assignments.stream().map(CourseAssignment::getId).toList();
        List<CourseAssignmentSubmission> graded = submissionMapper.selectList(
                new LambdaQueryWrapper<CourseAssignmentSubmission>()
                        .in(CourseAssignmentSubmission::getAssignmentId, assignmentIds)
                        .eq(CourseAssignmentSubmission::getStatus, SubmissionStatusEnum.GRADED));
        Map<Long, BigDecimal> regularMap = RegularScoreCalculator.normalize(assignments, graded);
        if (regularMap.isEmpty()) {
            throw new BusinessException(400, "暂无已批改的作业提交");
        }
        List<Long> roster = groupResolver.rosterUserIds(anchor.getId());
        List<Long> skippedUngraded = roster.stream().filter(sid -> !regularMap.containsKey(sid)).toList();
        ApplyRegularScoreResult result = scoreService.applyRegularScores(anchor.getId(), regularMap, teacherUserId);
        return new RegularScoreSyncView(result.updatedCount(), result.skippedLockedUserIds(), skippedUngraded);
    }
}
