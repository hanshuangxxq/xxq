package com.xrq.xxq.module.score.service.impl;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.xrq.xxq.common.BusinessException;
import com.xrq.xxq.module.analysis.util.ScoreStats;
import com.xrq.xxq.module.course.mapper.CourseMapper;
import com.xrq.xxq.module.course.service.CourseInfoResolver;
import com.xrq.xxq.module.score.dto.ReviewApplyRequest;
import com.xrq.xxq.module.score.dto.ReviewReplyRequest;
import com.xrq.xxq.module.score.dto.ReviewResolveRequest;
import com.xrq.xxq.module.score.dto.ReviewView;
import com.xrq.xxq.module.score.entity.Score;
import com.xrq.xxq.module.score.entity.ScoreReview;
import com.xrq.xxq.module.score.entity.ReviewStatusEnum;
import com.xrq.xxq.module.score.mapper.ScoreMapper;
import com.xrq.xxq.module.score.mapper.ScoreReviewMapper;
import com.xrq.xxq.module.score.service.ScoreReviewService;
import org.springframework.context.ApplicationEventPublisher;
import com.xrq.xxq.common.event.ReviewStatusEvent;
import com.xrq.xxq.module.user.entity.user.Teacher;
import com.xrq.xxq.module.user.mapper.StudentMapper;
import com.xrq.xxq.module.user.mapper.TeacherMapper;
import com.xrq.xxq.module.user.mapper.UserMapper;
import com.xrq.xxq.util.ParamValidator;
import com.xrq.xxq.util.ReferenceValidator;
import com.xrq.xxq.util.auth.AuthFacade;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 成绩复核服务实现：教师先审 -> 学生升级 -> 教务终审（终审调分并锁定成绩）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ScoreReviewServiceImpl extends ServiceImpl<ScoreReviewMapper, ScoreReview> implements ScoreReviewService {

    private final ScoreMapper scoreMapper;
    private final CourseMapper courseMapper;
    private final CourseInfoResolver courseInfoResolver;
    private final UserMapper userMapper;
    private final StudentMapper studentMapper;
    private final TeacherMapper teacherMapper;
    private final ApplicationEventPublisher eventPublisher;
    private final ReferenceValidator referenceValidator;

    // ==================== 申请 ====================

    @Override
    @Transactional
    public ReviewView apply(ReviewApplyRequest request, Long studentUserId) {
        ParamValidator.requireNonNull(request.getScoreId(), "成绩ID");
        ParamValidator.requireNonBlank(request.getReason(), "申请理由");
        Score g = scoreMapper.selectById(request.getScoreId());
        if (g == null) {
            throw new BusinessException(404, "成绩不存在");
        }
        if (!g.getStudentUserId().equals(studentUserId)) {
            throw new BusinessException(403, "权限不足");
        }
        Long active = baseMapper.selectCount(new LambdaQueryWrapper<ScoreReview>()
                .eq(ScoreReview::getScoreId, request.getScoreId())
                .in(ScoreReview::getStatus,
                        ReviewStatusEnum.PENDING, ReviewStatusEnum.TEACHER_REPLIED, ReviewStatusEnum.ESCALATED));
        if (active != null && active > 0) {
            throw new BusinessException(409, "该成绩已有进行中的复核申请");
        }
        // 外键存在性校验（成绩已由上方 selectById 校验）
        referenceValidator.requireExists(userMapper, studentUserId, "用户");
        ScoreReview r = new ScoreReview();
        r.setScoreId(request.getScoreId());
        r.setStudentUserId(studentUserId);
        r.setReason(request.getReason());
        r.setStatus(ReviewStatusEnum.PENDING);
        baseMapper.insert(r);
        return toViews(List.of(r)).getFirst();
    }

    @Override
    public List<ReviewView> listMy(Long studentUserId) {
        return toViews(baseMapper.selectList(new LambdaQueryWrapper<ScoreReview>()
                .eq(ScoreReview::getStudentUserId, studentUserId)
                .orderByDesc(ScoreReview::getCreateTime)));
    }

    @Override
    public List<ReviewView> listForHandler(Long userId, String userType, ReviewStatusEnum status) {
        LambdaQueryWrapper<ScoreReview> w = new LambdaQueryWrapper<ScoreReview>()
                .orderByDesc(ScoreReview::getCreateTime);
        if (AuthFacade.USER_TYPE_ACADEMIC_ADMIN.equals(userType)) {
            // 教务：全部
        } else if (AuthFacade.USER_TYPE_TEACHER.equals(userType)) {
            Teacher t = teacherMapper.findByUserId(userId);
            if (t == null) {
                return List.of();
            }
            List<Long> scoreIds = scoreMapper.selectList(
                            new LambdaQueryWrapper<Score>().eq(Score::getTeacherId, t.getId()))
                    .stream().map(Score::getId).toList();
            if (scoreIds.isEmpty()) {
                return List.of();
            }
            w.in(ScoreReview::getScoreId, scoreIds);
        } else {
            throw new BusinessException(403, "权限不足");
        }
        if (status != null) {
            w.eq(ScoreReview::getStatus, status);
        }
        return toViews(baseMapper.selectList(w));
    }

    // ==================== 教师回复 ====================

    @Override
    @Transactional
    public ReviewView teacherReply(Long reviewId, ReviewReplyRequest request, Long userId, String userType) {
        if (!AuthFacade.USER_TYPE_TEACHER.equals(userType)) {
            throw new BusinessException(403, "权限不足");
        }
        ScoreReview r = baseMapper.selectById(reviewId);
        if (r == null) {
            throw new BusinessException(404, "复核申请不存在");
        }
        Score g = scoreMapper.selectById(r.getScoreId());
        if (g == null) {
            throw new BusinessException(404, "成绩不存在");
        }
        Teacher t = teacherMapper.findByUserId(userId);
        if (t == null || !t.getId().equals(g.getTeacherId())) {
            throw new BusinessException(403, "权限不足");
        }
        if (r.getStatus() != ReviewStatusEnum.PENDING && r.getStatus() != ReviewStatusEnum.TEACHER_REPLIED) {
            throw new BusinessException(409, "当前状态不可回复");
        }
        r.setTeacherReply(request.getReply());
        r.setTeacherId(t.getId());
        r.setStatus(ReviewStatusEnum.TEACHER_REPLIED);
        // 外键存在性校验（成绩已由上方 selectById 校验）
        referenceValidator.requireExists(userMapper, r.getStudentUserId(), "用户");
        referenceValidator.requireExists(teacherMapper, r.getTeacherId(), "教师");
        baseMapper.updateById(r);
        if (request.getNewTotalScore() != null) {
            adjustTotal(g, request.getNewTotalScore());
        }
        notify(r.getStudentUserId(), "成绩复核进展", "教师已回复您的成绩复核申请，请查看。");
        return toViews(List.of(r)).getFirst();
    }

    // ==================== 升级 ====================

    @Override
    @Transactional
    public void escalate(Long reviewId, Long studentUserId) {
        ScoreReview r = baseMapper.selectById(reviewId);
        if (r == null) {
            throw new BusinessException(404, "复核申请不存在");
        }
        if (!r.getStudentUserId().equals(studentUserId)) {
            throw new BusinessException(403, "权限不足");
        }
        if (r.getStatus() != ReviewStatusEnum.TEACHER_REPLIED) {
            throw new BusinessException(409, "仅教师已回复后可升级到教务");
        }
        r.setStatus(ReviewStatusEnum.ESCALATED);
        r.setEscalateTime(LocalDateTime.now());
        // 外键存在性校验
        referenceValidator.requireExists(scoreMapper, r.getScoreId(), "成绩");
        referenceValidator.requireExists(userMapper, r.getStudentUserId(), "用户");
        referenceValidator.requireExists(teacherMapper, r.getTeacherId(), "教师");
        baseMapper.updateById(r);
    }

    // ==================== 教务终审 ====================

    @Override
    @Transactional
    public ReviewView adminResolve(Long reviewId, ReviewResolveRequest request, Long adminUserId) {
        ScoreReview r = baseMapper.selectById(reviewId);
        if (r == null) {
            throw new BusinessException(404, "复核申请不存在");
        }
        if (r.getStatus() != ReviewStatusEnum.ESCALATED) {
            throw new BusinessException(409, "仅已升级到教务的申请可终审");
        }
        r.setAdminReply(request.getReply());
        r.setAdminId(adminUserId);
        r.setStatus(request.isResolved() ? ReviewStatusEnum.RESOLVED : ReviewStatusEnum.REJECTED);
        r.setResolvedTime(LocalDateTime.now());
        // 外键存在性校验
        referenceValidator.requireExists(scoreMapper, r.getScoreId(), "成绩");
        referenceValidator.requireExists(userMapper, r.getStudentUserId(), "用户");
        referenceValidator.requireExists(teacherMapper, r.getTeacherId(), "教师");
        referenceValidator.requireExists(userMapper, r.getAdminId(), "用户");
        baseMapper.updateById(r);

        Score g = scoreMapper.selectById(r.getScoreId());
        if (g != null) {
            if (request.getNewTotalScore() != null) {
                ScoreStats.validateScore(request.getNewTotalScore());
                g.setTotalScore(request.getNewTotalScore());
                g.setScoreLevel(ScoreStats.levelOf(request.getNewTotalScore()));
            }
            g.setLocked(1); // 终审后锁定成绩
            scoreMapper.updateById(g);
        }
        notify(r.getStudentUserId(), "成绩复核结果", "您的成绩复核申请已处理完毕，请查看结果。");
        return toViews(List.of(r)).getFirst();
    }

    // ==================== 富化与辅助 ====================

    private void adjustTotal(Score g, BigDecimal newTotal) {
        ScoreStats.validateScore(newTotal);
        g.setTotalScore(newTotal);
        g.setScoreLevel(ScoreStats.levelOf(newTotal));
        scoreMapper.updateById(g);
    }

    private List<ReviewView> toViews(List<ScoreReview> reviews) {
        if (reviews.isEmpty()) {
            return List.of();
        }
        List<Long> scoreIds = reviews.stream().map(ScoreReview::getScoreId).distinct().toList();
        Map<Long, Score> gradeMap = scoreMapper.selectByIds(scoreIds).stream()
                .collect(Collectors.toMap(Score::getId, g -> g, (a, b) -> a));
        List<Long> courseIds = gradeMap.values().stream().map(Score::getCourseId).filter(Objects::nonNull).distinct().toList();
        List<Long> campaignIds = gradeMap.values().stream().map(Score::getCampaignId).filter(Objects::nonNull).distinct().toList();
        List<Long> teacherIds = gradeMap.values().stream().map(Score::getTeacherId).filter(Objects::nonNull).distinct().toList();
        List<Long> studentUserIds = reviews.stream().map(ScoreReview::getStudentUserId).distinct().toList();

        Map<Long, String> courseNameByCourse = courseInfoResolver.resolveCourseNameMap(courseIds);
        Map<Long, String> courseNameByCampaign = courseInfoResolver.resolveCampaignNameMap(campaignIds);
        Map<Long, Long> teacherIdToUserId = teacherIds.isEmpty() ? Map.of()
                : teacherMapper.selectByIds(teacherIds).stream()
                        .collect(Collectors.toMap(Teacher::getId, Teacher::getUserId, (a, b) -> a));
        List<Long> allUserIds = new ArrayList<>(studentUserIds);
        allUserIds.addAll(teacherIdToUserId.values());
        Map<Long, String> userNameMap = userMapper.toNameMap(allUserIds);
        Map<Long, String> studentNoMap = studentMapper.toStudentNoMap(studentUserIds);

        return reviews.stream().map(r -> {
            ReviewView v = new ReviewView();
            v.setId(r.getId());
            v.setScoreId(r.getScoreId());
            v.setStudentUserId(r.getStudentUserId());
            v.setStudentName(userNameMap.get(r.getStudentUserId()));
            v.setStudentNo(studentNoMap.get(r.getStudentUserId()));
            Score g = gradeMap.get(r.getScoreId());
            if (g != null) {
                v.setCourseId(g.getCourseId());
                v.setCourseName(g.getCampaignId() != null
                        ? courseNameByCampaign.get(g.getCampaignId())
                        : courseNameByCourse.get(g.getCourseId()));
                v.setTeacherId(g.getTeacherId());
                Long tuid = teacherIdToUserId.get(g.getTeacherId());
                v.setTeacherName(tuid != null ? userNameMap.get(tuid) : null);
                v.setCurrentTotalScore(g.getTotalScore());
            }
            v.setReason(r.getReason());
            v.setStatus(r.getStatus());
            v.setTeacherReply(r.getTeacherReply());
            v.setAdminReply(r.getAdminReply());
            v.setEscalateTime(r.getEscalateTime());
            v.setResolvedTime(r.getResolvedTime());
            v.setCreateTime(r.getCreateTime());
            return v;
        }).toList();
    }

    private void notify(Long studentUserId, String title, String content) {
        eventPublisher.publishEvent(new ReviewStatusEvent(studentUserId, title, content));
    }
}
