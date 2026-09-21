package com.xrq.xxq.module.coursework.assignment.service.impl;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.xrq.xxq.common.BusinessException;
import com.xrq.xxq.common.PageQuery;
import com.xrq.xxq.common.PageResult;
import com.xrq.xxq.module.course.service.CourseInfoResolver;
import com.xrq.xxq.module.coursework.assignment.dto.AssignmentSaveRequest;
import com.xrq.xxq.module.coursework.assignment.dto.AssignmentView;
import com.xrq.xxq.module.coursework.assignment.entity.AssignmentStatusEnum;
import com.xrq.xxq.module.coursework.assignment.entity.CourseAssignment;
import com.xrq.xxq.module.coursework.assignment.entity.CourseAssignmentSubmission;
import com.xrq.xxq.module.coursework.assignment.entity.SubmissionStatusEnum;
import com.xrq.xxq.module.coursework.assignment.mapper.CourseAssignmentMapper;
import com.xrq.xxq.module.coursework.assignment.mapper.CourseAssignmentSubmissionMapper;
import com.xrq.xxq.module.coursework.assignment.service.AssignmentService;
import com.xrq.xxq.module.coursework.common.CourseGroupResolver;
import com.xrq.xxq.module.coursework.common.CourseworkFileSupport;
import com.xrq.xxq.module.file.dto.StoredFileRef;
import com.xrq.xxq.module.file.entity.FileBizEnum;
import com.xrq.xxq.module.notification.notice.CourseworkNoticeScenes;
import com.xrq.xxq.module.teachinfo.entity.TeachInfo;

import lombok.RequiredArgsConstructor;

/**
 * 作业服务实现。通知经 {@link CourseworkNoticeScenes} 注解场景在事务提交后发送。
 */
@Service
@RequiredArgsConstructor
public class AssignmentServiceImpl extends ServiceImpl<CourseAssignmentMapper, CourseAssignment>
        implements AssignmentService {

    private static final BigDecimal DEFAULT_TOTAL = new BigDecimal("100");
    private static final DateTimeFormatter DEADLINE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private final CourseAssignmentSubmissionMapper submissionMapper;
    private final CourseGroupResolver groupResolver;
    private final CourseworkFileSupport fileSupport;
    private final CourseInfoResolver courseInfoResolver;
    private final CourseworkNoticeScenes courseworkNoticeScenes;

    @Override
    @Transactional
    public AssignmentView create(Long teacherUserId, AssignmentSaveRequest req, MultipartFile file) {
        if (req.getTeachInfoId() == null) {
            throw new BusinessException(400, "授课安排不能为空");
        }
        TeachInfo anchor = groupResolver.requireOwnedAnchor(req.getTeachInfoId(), teacherUserId);
        validateTitle(req.getTitle());
        if (req.getDeadline() == null) {
            throw new BusinessException(400, "截止时间不能为空");
        }
        BigDecimal total = req.getTotalScore() != null ? req.getTotalScore() : DEFAULT_TOTAL;
        if (total.signum() <= 0) {
            throw new BusinessException(400, "满分必须大于 0");
        }
        StoredFileRef ref = fileSupport.resolveSubmit(req.getFilePath(), file, FileBizEnum.COURSE_ASSIGNMENT, false);

        CourseAssignment a = new CourseAssignment();
        a.setTeachInfoId(anchor.getId());
        a.setTitle(req.getTitle().trim());
        a.setContent(req.getContent());
        a.setDeadline(req.getDeadline());
        a.setTotalScore(total);
        a.setStatus(Boolean.TRUE.equals(req.getPublish())
                ? AssignmentStatusEnum.PUBLISHED : AssignmentStatusEnum.DRAFT);
        a.setTeacherId(teacherUserId);
        if (ref != null) {
            a.setFileName(ref.storedPath());
            a.setFileOriginal(displayName(ref, req.getFileOriginal()));
        }
        a.setCreateTime(LocalDateTime.now());
        a.setUpdateTime(LocalDateTime.now());
        baseMapper.insert(a);

        if (a.getStatus() == AssignmentStatusEnum.PUBLISHED) {
            notifyPublished(a, anchor);
        }
        return toView(a);
    }

    @Override
    @Transactional
    public AssignmentView update(Long teacherUserId, Long id, AssignmentSaveRequest req) {
        CourseAssignment a = requireOwnedAssignment(id, teacherUserId);
        if (a.getStatus() == AssignmentStatusEnum.CLOSED) {
            throw new BusinessException(409, "作业已关闭，不可修改");
        }
        if (a.getStatus() == AssignmentStatusEnum.PUBLISHED) {
            if (req.getDeadline() != null && req.getDeadline().isBefore(a.getDeadline())) {
                throw new BusinessException(400, "已发布作业只允许延长截止时间");
            }
            if (req.getTotalScore() != null && req.getTotalScore().compareTo(a.getTotalScore()) != 0) {
                throw new BusinessException(400, "已发布作业不可修改满分");
            }
        }
        if (req.getTitle() != null) {
            validateTitle(req.getTitle());
            a.setTitle(req.getTitle().trim());
        }
        if (req.getContent() != null) {
            a.setContent(req.getContent());
        }
        if (req.getDeadline() != null) {
            a.setDeadline(req.getDeadline());
        }
        if (req.getTotalScore() != null) {
            if (req.getTotalScore().signum() <= 0) {
                throw new BusinessException(400, "满分必须大于 0");
            }
            a.setTotalScore(req.getTotalScore());
        }
        a.setUpdateTime(LocalDateTime.now());
        baseMapper.updateById(a);
        return toView(a);
    }

    @Override
    @Transactional
    public AssignmentView replaceAttachment(Long teacherUserId, Long id, String filePath, String fileOriginal,
                                            MultipartFile file) {
        CourseAssignment a = requireOwnedAssignment(id, teacherUserId);
        if (a.getStatus() == AssignmentStatusEnum.CLOSED) {
            throw new BusinessException(409, "作业已关闭，不可修改");
        }
        StoredFileRef ref = fileSupport.resolveSubmit(filePath, file, FileBizEnum.COURSE_ASSIGNMENT, true);
        // 旧附件不删：内容寻址产物可能被共享，由 FileMaintenanceTask 无引用后回收
        a.setFileName(ref.storedPath());
        a.setFileOriginal(displayName(ref, fileOriginal));
        a.setUpdateTime(LocalDateTime.now());
        baseMapper.updateById(a);
        return toView(a);
    }

    @Override
    @Transactional
    public void delete(Long teacherUserId, Long id) {
        CourseAssignment a = requireOwnedAssignment(id, teacherUserId);
        if (a.getStatus() != AssignmentStatusEnum.DRAFT) {
            throw new BusinessException(409, "仅草稿可删除；已发布作业请关闭");
        }
        Long submissions = submissionMapper.selectCount(new LambdaQueryWrapper<CourseAssignmentSubmission>()
                .eq(CourseAssignmentSubmission::getAssignmentId, id));
        if (submissions != null && submissions > 0) {
            throw new BusinessException(409, "该作业已有提交记录，无法删除");
        }
        baseMapper.deleteById(id);
    }

    @Override
    @Transactional
    public AssignmentView publish(Long teacherUserId, Long id) {
        CourseAssignment a = requireOwnedAssignment(id, teacherUserId);
        if (a.getStatus() != AssignmentStatusEnum.DRAFT) {
            throw new BusinessException(409, "仅草稿可发布");
        }
        a.setStatus(AssignmentStatusEnum.PUBLISHED);
        a.setUpdateTime(LocalDateTime.now());
        baseMapper.updateById(a);
        notifyPublished(a, groupResolver.requireAnchor(a.getTeachInfoId()));
        return toView(a);
    }

    @Override
    @Transactional
    public AssignmentView close(Long teacherUserId, Long id) {
        CourseAssignment a = requireOwnedAssignment(id, teacherUserId);
        if (a.getStatus() != AssignmentStatusEnum.PUBLISHED) {
            throw new BusinessException(409, "仅已发布作业可关闭");
        }
        a.setStatus(AssignmentStatusEnum.CLOSED);
        a.setUpdateTime(LocalDateTime.now());
        baseMapper.updateById(a);
        return toView(a);
    }

    @Override
    public PageResult<AssignmentView> listForTeacher(Long teacherUserId, Long teachInfoId, PageQuery pageQuery) {
        TeachInfo anchor = groupResolver.requireOwnedAnchor(teachInfoId, teacherUserId);
        Page<CourseAssignment> page = baseMapper.selectPage(pageQuery.toPage(),
                new LambdaQueryWrapper<CourseAssignment>()
                        .eq(CourseAssignment::getTeachInfoId, anchor.getId())
                        .orderByDesc(CourseAssignment::getCreateTime)
                        .orderByDesc(CourseAssignment::getId));
        List<AssignmentView> views = page.getRecords().stream().map(this::toView).toList();
        enrichTeacherStats(views);
        return PageResult.of(page, views);
    }

    @Override
    public PageResult<AssignmentView> listForStudent(Long studentUserId, Long teachInfoId, PageQuery pageQuery) {
        groupResolver.assertVisibleToStudent(teachInfoId, studentUserId);
        TeachInfo anchor = groupResolver.requireAnchor(teachInfoId);
        Page<CourseAssignment> page = baseMapper.selectPage(pageQuery.toPage(),
                new LambdaQueryWrapper<CourseAssignment>()
                        .eq(CourseAssignment::getTeachInfoId, anchor.getId())
                        .in(CourseAssignment::getStatus, AssignmentStatusEnum.PUBLISHED, AssignmentStatusEnum.CLOSED)
                        .orderByDesc(CourseAssignment::getCreateTime)
                        .orderByDesc(CourseAssignment::getId));
        List<AssignmentView> views = page.getRecords().stream().map(this::toView).toList();
        enrichMySubmission(views, studentUserId);
        return PageResult.of(page, views);
    }

    @Override
    public AssignmentView detailForTeacher(Long teacherUserId, Long id) {
        CourseAssignment a = requireOwnedAssignment(id, teacherUserId);
        AssignmentView v = toView(a);
        enrichTeacherStats(List.of(v));
        return v;
    }

    @Override
    public AssignmentView detailForStudent(Long studentUserId, Long id) {
        CourseAssignment a = baseMapper.selectById(id);
        // 草稿对学生按不存在处理，避免探测未发布内容
        if (a == null || a.getStatus() == AssignmentStatusEnum.DRAFT) {
            throw new BusinessException(404, "作业不存在");
        }
        groupResolver.assertVisibleToStudent(a.getTeachInfoId(), studentUserId);
        AssignmentView v = toView(a);
        enrichMySubmission(List.of(v), studentUserId);
        return v;
    }

    // ==================== 内部 ====================

    private CourseAssignment requireOwnedAssignment(Long id, Long teacherUserId) {
        CourseAssignment a = baseMapper.selectById(id);
        if (a == null) {
            throw new BusinessException(404, "作业不存在");
        }
        groupResolver.requireOwnedAnchor(a.getTeachInfoId(), teacherUserId);
        return a;
    }

    private void notifyPublished(CourseAssignment a, TeachInfo anchor) {
        CourseInfoResolver.CourseInfo info =
                courseInfoResolver.resolveOne(anchor.getCourseId(), anchor.getCampaignId());
        String courseName = info != null ? info.getCourseName() : "课程";
        String deadlineText = a.getDeadline().format(DEADLINE_FMT);
        for (Long studentUserId : groupResolver.rosterUserIds(anchor.getId())) {
            courseworkNoticeScenes.assignmentPublished(studentUserId, courseName, a.getTitle(), deadlineText);
        }
    }

    /** 教师视角：批量统计每份作业的提交数/已批改数（一次 IN 查询内存聚合，避免 N+1）。 */
    private void enrichTeacherStats(List<AssignmentView> views) {
        List<Long> ids = views.stream().map(AssignmentView::getId).toList();
        if (ids.isEmpty()) {
            return;
        }
        List<CourseAssignmentSubmission> subs = submissionMapper.selectList(
                new LambdaQueryWrapper<CourseAssignmentSubmission>()
                        .in(CourseAssignmentSubmission::getAssignmentId, ids));
        Map<Long, Long> submitted = subs.stream().collect(Collectors.groupingBy(
                CourseAssignmentSubmission::getAssignmentId, Collectors.counting()));
        Map<Long, Long> graded = subs.stream()
                .filter(s -> s.getStatus() == SubmissionStatusEnum.GRADED)
                .collect(Collectors.groupingBy(CourseAssignmentSubmission::getAssignmentId, Collectors.counting()));
        for (AssignmentView v : views) {
            v.setSubmittedCount(submitted.getOrDefault(v.getId(), 0L).intValue());
            v.setGradedCount(graded.getOrDefault(v.getId(), 0L).intValue());
        }
    }

    /** 学生视角：批量回填「我的提交」摘要。 */
    private void enrichMySubmission(List<AssignmentView> views, Long studentUserId) {
        List<Long> ids = views.stream().map(AssignmentView::getId).toList();
        if (ids.isEmpty()) {
            return;
        }
        // 应用层 upsert 无 DB 唯一约束，并发双击可能留下重复行：按 id 大者（最新）生效
        Map<Long, CourseAssignmentSubmission> mine = submissionMapper.selectList(
                        new LambdaQueryWrapper<CourseAssignmentSubmission>()
                                .in(CourseAssignmentSubmission::getAssignmentId, ids)
                                .eq(CourseAssignmentSubmission::getStudentId, studentUserId)).stream()
                .collect(Collectors.toMap(CourseAssignmentSubmission::getAssignmentId, s -> s,
                        (x, y) -> x.getId() >= y.getId() ? x : y));
        for (AssignmentView v : views) {
            CourseAssignmentSubmission s = mine.get(v.getId());
            if (s != null) {
                v.setMySubmissionId(s.getId());
                v.setMySubmissionStatus(s.getStatus() != null ? s.getStatus().getCode() : null);
                v.setMyScore(s.getScore());
                v.setMyLate(s.getLate());
                v.setMyVersion(s.getVersion());
            }
        }
    }

    private AssignmentView toView(CourseAssignment a) {
        AssignmentView v = new AssignmentView();
        v.setId(a.getId());
        v.setTeachInfoId(a.getTeachInfoId());
        v.setTitle(a.getTitle());
        v.setContent(a.getContent());
        v.setFileName(a.getFileName());
        v.setFileOriginal(a.getFileOriginal());
        v.setDeadline(a.getDeadline());
        v.setTotalScore(a.getTotalScore());
        v.setStatus(a.getStatus());
        v.setTeacherId(a.getTeacherId());
        v.setCreateTime(a.getCreateTime());
        return v;
    }

    private static void validateTitle(String title) {
        if (title == null || title.isBlank()) {
            throw new BusinessException(400, "作业标题不能为空");
        }
    }

    private static String displayName(StoredFileRef ref, String requested) {
        return (requested == null || requested.isBlank()) ? ref.originalName() : requested.trim();
    }
}
