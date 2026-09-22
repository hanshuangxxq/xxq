package com.xrq.xxq.module.coursework.assignment.service.impl;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
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
import com.xrq.xxq.module.coursework.assignment.cache.AssignmentDraftStore;
import com.xrq.xxq.module.coursework.assignment.cache.AssignmentViewedStore;
import com.xrq.xxq.module.coursework.assignment.dto.AssignmentQuestionView;
import com.xrq.xxq.module.coursework.assignment.dto.AssignmentSaveRequest;
import com.xrq.xxq.module.coursework.assignment.dto.AssignmentView;
import com.xrq.xxq.module.coursework.assignment.dto.CloneAssignmentRequest;
import com.xrq.xxq.module.coursework.assignment.entity.AnswerVisibleEnum;
import com.xrq.xxq.module.coursework.assignment.entity.AssignmentStatusEnum;
import com.xrq.xxq.module.coursework.assignment.entity.CourseAssignment;
import com.xrq.xxq.module.coursework.assignment.entity.CourseAssignmentQuestion;
import com.xrq.xxq.module.coursework.assignment.entity.CourseAssignmentSubmission;
import com.xrq.xxq.module.coursework.assignment.entity.SubmissionStatusEnum;
import com.xrq.xxq.module.coursework.assignment.mapper.CourseAssignmentMapper;
import com.xrq.xxq.module.coursework.assignment.mapper.CourseAssignmentSubmissionMapper;
import com.xrq.xxq.module.coursework.assignment.service.AssignmentQuestionService;
import com.xrq.xxq.module.coursework.assignment.service.AssignmentService;
import com.xrq.xxq.module.coursework.common.CourseGroupResolver;
import com.xrq.xxq.module.coursework.common.CourseworkFileSupport;
import com.xrq.xxq.module.coursework.common.QuestionTypeEnum;
import com.xrq.xxq.module.file.dto.StoredFileRef;
import com.xrq.xxq.module.file.entity.FileBizEnum;
import com.xrq.xxq.module.notification.notice.CourseworkNoticeScenes;
import com.xrq.xxq.module.teachinfo.entity.TeachInfo;

import lombok.RequiredArgsConstructor;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * 作业服务实现。通知经 {@link CourseworkNoticeScenes} 注解场景在事务提交后发送。
 */
@Service
@RequiredArgsConstructor
public class AssignmentServiceImpl extends ServiceImpl<CourseAssignmentMapper, CourseAssignment>
        implements AssignmentService {

    private static final DateTimeFormatter DEADLINE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private final CourseAssignmentSubmissionMapper submissionMapper;
    private final CourseGroupResolver groupResolver;
    private final CourseworkFileSupport fileSupport;
    private final CourseInfoResolver courseInfoResolver;
    private final CourseworkNoticeScenes courseworkNoticeScenes;
    private final AssignmentQuestionService assignmentQuestionService;
    private final ObjectMapper objectMapper;
    private final AssignmentViewedStore assignmentViewedStore;
    private final AssignmentDraftStore assignmentDraftStore;

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
        StoredFileRef ref = fileSupport.resolveSubmit(req.getFilePath(), file, FileBizEnum.COURSE_ASSIGNMENT, false);

        CourseAssignment a = new CourseAssignment();
        a.setTeachInfoId(anchor.getId());
        a.setTitle(req.getTitle().trim());
        a.setContent(req.getContent());
        a.setDeadline(req.getDeadline());
        a.setTotalScore(BigDecimal.ZERO); // 总分由题目配分求和，下面重算
        a.setAnswerVisible(AnswerVisibleEnum.orDefault(req.getAnswerVisible()));
        a.setStatus(AssignmentStatusEnum.DRAFT); // 先落草稿，题目校验通过后按 publish 标志转发布
        a.setTeacherId(teacherUserId);
        if (ref != null) {
            a.setFileName(ref.storedPath());
            a.setFileOriginal(displayName(ref, req.getFileOriginal()));
        }
        a.setCreateTime(LocalDateTime.now());
        a.setUpdateTime(LocalDateTime.now());
        baseMapper.insert(a);

        assignmentQuestionService.replaceQuestions(a.getId(), teacherUserId,
                req.getQuestionIds(), req.getNewQuestions());
        a.setTotalScore(assignmentQuestionService.totalScoreOf(a.getId()));

        if (Boolean.TRUE.equals(req.getPublish())) {
            requirePublishable(a.getId());
            a.setStatus(AssignmentStatusEnum.PUBLISHED);
        }
        a.setUpdateTime(LocalDateTime.now());
        baseMapper.updateById(a);

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
        boolean hasQuestionPayload = req.getQuestionIds() != null || req.getNewQuestions() != null;
        if (a.getStatus() == AssignmentStatusEnum.PUBLISHED) {
            if (hasQuestionPayload) {
                throw new BusinessException(400, "已发布作业不可修改题目");
            }
            if (req.getDeadline() != null && req.getDeadline().isBefore(a.getDeadline())) {
                throw new BusinessException(400, "已发布作业只允许延长截止时间");
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
        if (req.getAnswerVisible() != null) {
            a.setAnswerVisible(req.getAnswerVisible());
        }
        // totalScore 不再受理前端传值：始终由题目配分求和派生
        if (hasQuestionPayload) {
            assignmentQuestionService.replaceQuestions(a.getId(), teacherUserId,
                    req.getQuestionIds(), req.getNewQuestions());
            a.setTotalScore(assignmentQuestionService.totalScoreOf(a.getId()));
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
        assignmentQuestionService.replaceQuestions(id, teacherUserId, List.of(), List.of()); // 软删快照
        assignmentViewedStore.clear(id);
        baseMapper.deleteById(id);
    }

    @Override
    @Transactional
    public AssignmentView publish(Long teacherUserId, Long id) {
        CourseAssignment a = requireOwnedAssignment(id, teacherUserId);
        if (a.getStatus() != AssignmentStatusEnum.DRAFT) {
            throw new BusinessException(409, "仅草稿可发布");
        }
        requirePublishable(id);
        a.setTotalScore(assignmentQuestionService.totalScoreOf(id));
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
    @Transactional
    public AssignmentView clone(Long teacherUserId, Long id, CloneAssignmentRequest req) {
        CourseAssignment src = requireOwnedAssignment(id, teacherUserId);
        TeachInfo targetAnchor = groupResolver.requireOwnedAnchor(req.getTeachInfoId(), teacherUserId);
        if (req.getDeadline() == null) {
            throw new BusinessException(400, "截止时间不能为空");
        }
        if (assignmentQuestionService.listQuestions(id).isEmpty()) {
            throw new BusinessException(400, "源作业没有题目，无法克隆");
        }
        CourseAssignment copy = new CourseAssignment();
        copy.setTeachInfoId(targetAnchor.getId());
        copy.setTitle(src.getTitle());
        copy.setContent(src.getContent());
        // 内容寻址产物路径直接共享是安全的（同路径字节永不改变）
        copy.setFileName(src.getFileName());
        copy.setFileOriginal(src.getFileOriginal());
        copy.setDeadline(req.getDeadline());
        copy.setTotalScore(src.getTotalScore());
        copy.setAnswerVisible(src.getAnswerVisible());
        copy.setStatus(AssignmentStatusEnum.DRAFT);
        copy.setTeacherId(teacherUserId);
        copy.setCreateTime(LocalDateTime.now());
        copy.setUpdateTime(LocalDateTime.now());
        baseMapper.insert(copy);
        assignmentQuestionService.cloneQuestions(id, copy.getId());
        return toView(copy);
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
        enrichQuestions(v, assignmentQuestionService.listQuestions(id), true, null);
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
        assignmentViewedStore.markViewed(id, studentUserId, a.getDeadline());
        AssignmentView v = toView(a);
        enrichMySubmission(List.of(v), studentUserId);
        // 已提交的学生走 my-submission 看作答；作答页只回显草稿（answer_visible 管的是提交后的查看）
        Map<String, String> draft = v.getMySubmissionId() == null
                ? assignmentDraftStore.load(id, studentUserId) : null;
        enrichQuestions(v, assignmentQuestionService.listQuestions(id), false, draft);
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

    /** 发布前置校验：至少一道题且总分 > 0。 */
    private void requirePublishable(Long assignmentId) {
        BigDecimal total = assignmentQuestionService.totalScoreOf(assignmentId);
        if (total.signum() <= 0) {
            throw new BusinessException(400, "发布前请至少添加一道配分大于 0 的题目");
        }
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

    /**
     * 回填题目视图 + 题型汇总。
     *
     * @param revealAnswer 是否附标准答案/解析（教师端 true；学生端按 answer_visible 判定后传入）
     * @param draft        学生草稿（questionId → 答案 JSON 子串；教师端传 null）
     */
    void enrichQuestions(AssignmentView v, List<CourseAssignmentQuestion> questions,
                         boolean revealAnswer, Map<String, String> draft) {
        List<AssignmentQuestionView> views = new ArrayList<>(questions.size());
        for (CourseAssignmentQuestion q : questions) {
            AssignmentQuestionView qv = new AssignmentQuestionView();
            qv.setId(q.getId());
            qv.setType(q.getType().getCode());
            qv.setStem(q.getStem());
            qv.setOptions(parseJson(q.getOptionsJson()));
            qv.setScore(q.getScore());
            qv.setSortOrder(q.getSortOrder());
            qv.setScoreRule(q.getScoreRule() != null ? q.getScoreRule().getCode() : null);
            qv.setCaseSensitive(q.getCaseSensitive() != null && q.getCaseSensitive() == 1);
            qv.setRequireFile(q.getRequireFile() != null && q.getRequireFile() == 1);
            if (revealAnswer) {
                qv.setAnswer(parseJson(q.getAnswerJson()));
                qv.setAnalysis(q.getAnalysis());
            }
            if (draft != null) {
                qv.setMyAnswer(parseJson(draft.get(String.valueOf(q.getId()))));
            }
            views.add(qv);
        }
        v.setQuestions(views);
        v.setQuestionTypes(views.stream().map(AssignmentQuestionView::getType).distinct().toList());
        v.setHasFileQuestion(questions.stream().anyMatch(q -> q.getType() == QuestionTypeEnum.ESSAY));
    }

    /** JSON 列 → JsonNode；null/损坏 → null。 */
    JsonNode parseJson(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readTree(json);
        } catch (RuntimeException e) {
            return null;
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
        v.setAnswerVisible(a.getAnswerVisible());
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
