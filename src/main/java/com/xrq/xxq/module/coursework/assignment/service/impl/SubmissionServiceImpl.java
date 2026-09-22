package com.xrq.xxq.module.coursework.assignment.service.impl;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.xrq.xxq.common.BusinessException;
import com.xrq.xxq.module.coursework.assignment.cache.AssignmentDraftStore;
import com.xrq.xxq.module.coursework.assignment.cache.AssignmentViewedStore;
import com.xrq.xxq.module.coursework.assignment.dto.AnswerInput;
import com.xrq.xxq.module.coursework.assignment.dto.AnswerView;
import com.xrq.xxq.module.coursework.assignment.dto.DraftSaveRequest;
import com.xrq.xxq.module.coursework.assignment.dto.GradeRequest;
import com.xrq.xxq.module.coursework.assignment.dto.SubmissionRowView;
import com.xrq.xxq.module.coursework.assignment.dto.SubmissionView;
import com.xrq.xxq.module.coursework.assignment.dto.SubmitAnswersRequest;
import com.xrq.xxq.module.coursework.assignment.dto.TeacherSubmissionView;
import com.xrq.xxq.module.coursework.assignment.entity.AnswerVisibleEnum;
import com.xrq.xxq.module.coursework.assignment.entity.AssignmentStatusEnum;
import com.xrq.xxq.module.coursework.assignment.entity.CourseAssignment;
import com.xrq.xxq.module.coursework.assignment.entity.CourseAssignmentAnswer;
import com.xrq.xxq.module.coursework.assignment.entity.CourseAssignmentQuestion;
import com.xrq.xxq.module.coursework.assignment.entity.CourseAssignmentSubmission;
import com.xrq.xxq.module.coursework.assignment.entity.SubmissionStatusEnum;
import com.xrq.xxq.module.coursework.assignment.grading.ObjectiveGrader;
import com.xrq.xxq.module.coursework.assignment.mapper.CourseAssignmentAnswerMapper;
import com.xrq.xxq.module.coursework.assignment.mapper.CourseAssignmentMapper;
import com.xrq.xxq.module.coursework.assignment.mapper.CourseAssignmentQuestionMapper;
import com.xrq.xxq.module.coursework.assignment.mapper.CourseAssignmentSubmissionMapper;
import com.xrq.xxq.module.coursework.assignment.service.SubmissionService;
import com.xrq.xxq.module.coursework.common.CourseGroupResolver;
import com.xrq.xxq.module.coursework.common.QuestionTypeEnum;
import com.xrq.xxq.module.file.dto.StoredFileRef;
import com.xrq.xxq.module.file.entity.FileBizEnum;
import com.xrq.xxq.module.file.service.FileStorageService;
import com.xrq.xxq.module.notification.notice.CourseworkNoticeScenes;
import com.xrq.xxq.module.user.entity.User;
import com.xrq.xxq.module.user.entity.user.Student;
import com.xrq.xxq.module.user.mapper.StudentMapper;
import com.xrq.xxq.module.user.mapper.UserMapper;

import lombok.RequiredArgsConstructor;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

@Service
@RequiredArgsConstructor
public class SubmissionServiceImpl
        extends ServiceImpl<CourseAssignmentSubmissionMapper, CourseAssignmentSubmission>
        implements SubmissionService {

    private final CourseAssignmentMapper assignmentMapper;
    private final CourseAssignmentQuestionMapper questionMapper;
    private final CourseAssignmentAnswerMapper answerMapper;
    private final CourseGroupResolver groupResolver;
    private final FileStorageService fileStorage;
    private final AssignmentDraftStore draftStore;
    private final AssignmentViewedStore viewedStore;
    private final CourseworkNoticeScenes courseworkNoticeScenes;
    private final UserMapper userMapper;
    private final StudentMapper studentMapper;
    private final ObjectMapper objectMapper;

    /** 迟交判定：提交时间晚于截止时间为迟交（恰好相等不算）。包级可见供单测。 */
    static int lateOf(LocalDateTime submitTime, LocalDateTime deadline) {
        return submitTime.isAfter(deadline) ? 1 : 0;
    }

    @Override
    public void saveDraft(Long studentUserId, Long assignmentId, DraftSaveRequest req) {
        CourseAssignment a = requireSubmittable(assignmentId);
        groupResolver.assertVisibleToStudent(a.getTeachInfoId(), studentUserId);
        List<CourseAssignmentQuestion> questions = listQuestions(assignmentId);
        Set<Long> known = questions.stream().map(CourseAssignmentQuestion::getId).collect(Collectors.toSet());
        Map<String, String> draft = new HashMap<>();
        if (req.getAnswers() == null) {
            throw new BusinessException(400, "答案列表不能为空");
        }
        for (AnswerInput input : req.getAnswers()) {
            if (input == null) {
                throw new BusinessException(400, "作答项不能为空");
            }
            if (input.getQuestionId() == null) {
                throw new BusinessException(400, "题目 id 不能为空");
            }
            if (!known.contains(input.getQuestionId())) {
                throw new BusinessException(400, "题目不属于该作业: " + input.getQuestionId());
            }
            if (input.getAnswer() != null && !input.getAnswer().isNull()) {
                draft.put(String.valueOf(input.getQuestionId()),
                        objectMapper.writeValueAsString(input.getAnswer()));
            }
        }
        draftStore.save(assignmentId, studentUserId, a.getDeadline(), draft);
    }

    @Override
    @Transactional
    public SubmissionView submit(Long studentUserId, Long assignmentId, SubmitAnswersRequest req) {
        CourseAssignment a = requireSubmittable(assignmentId);
        groupResolver.assertVisibleToStudent(a.getTeachInfoId(), studentUserId);

        List<CourseAssignmentQuestion> questions = listQuestions(assignmentId);
        if (questions.isEmpty()) {
            throw new BusinessException(409, "作业尚未配置题目");
        }
        Map<Long, JsonNode> answerMap = new HashMap<>();
        if (req.getAnswers() != null) {
            for (AnswerInput input : req.getAnswers()) {
                if (input == null) {
                    throw new BusinessException(400, "作答项不能为空");
                }
                if (input.getQuestionId() == null) {
                    throw new BusinessException(400, "题目 id 不能为空");
                }
                if (answerMap.containsKey(input.getQuestionId())) {
                    throw new BusinessException(400, "题目重复作答: " + input.getQuestionId());
                }
                answerMap.put(input.getQuestionId(), input.getAnswer());
            }
        }
        Set<Long> known = questions.stream().map(CourseAssignmentQuestion::getId).collect(Collectors.toSet());
        for (Long qid : answerMap.keySet()) {
            if (!known.contains(qid)) {
                throw new BusinessException(400, "题目不属于该作业: " + qid);
            }
        }

        LocalDateTime now = LocalDateTime.now();
        // 应用层 upsert（沿用：并发双击可能产生重复行，读取侧按 id 倒序取首条兜底）
        CourseAssignmentSubmission s = latestOf(assignmentId, studentUserId);
        if (s == null) {
            s = new CourseAssignmentSubmission();
            s.setAssignmentId(assignmentId);
            s.setStudentId(studentUserId);
            s.setVersion(1);
            s.setCreateTime(now);
        } else {
            s.setVersion(s.getVersion() + 1);
        }

        // 逐题处理：形状校验 → 客观题即时判分；每题一行（未答 answerJson=null）
        List<CourseAssignmentAnswer> answers = new ArrayList<>();
        BigDecimal autoTotal = BigDecimal.ZERO;
        boolean hasEssay = false;
        for (CourseAssignmentQuestion q : questions) {
            JsonNode raw = answerMap.get(q.getId());
            CourseAssignmentAnswer ans = new CourseAssignmentAnswer();
            ans.setQuestionId(q.getId());
            if (q.getType() == QuestionTypeEnum.ESSAY) {
                hasEssay = true;
                ans.setAnswerJson(normalizeEssayAnswer(raw, q));
            } else {
                ans.setAnswerJson(normalizeObjectiveAnswer(q.getType(), raw));
                BigDecimal auto = ObjectiveGrader.grade(q.getType(), parseJson(q.getAnswerJson()), raw,
                        q.getScoreRule(), q.getCaseSensitive() != null && q.getCaseSensitive() == 1,
                        q.getScore());
                ans.setAutoScore(auto);
                ans.setFinalScore(auto); // 客观题终判分=自动分（不可人工改判）
                autoTotal = autoTotal.add(auto);
            }
            ans.setCreateTime(now);
            ans.setUpdateTime(now);
            answers.add(ans);
        }

        // 旧列（content/fileName/fileOriginal）停写；重交清空批改痕迹
        s.setContent(null);
        s.setFileName(null);
        s.setFileOriginal(null);
        s.setSubmitTime(now);
        s.setLate(lateOf(now, a.getDeadline()));
        s.setAutoScore(autoTotal);
        s.setComment(null);
        if (hasEssay) {
            s.setStatus(SubmissionStatusEnum.SUBMITTED);
            s.setScore(null);      // 总分待教师判完主观题才出
            s.setGradeTime(null);
        } else {
            // 全客观作业：提交即 GRADED 即时出分（不发通知，学生已即时看到）
            s.setStatus(SubmissionStatusEnum.GRADED);
            s.setScore(autoTotal.setScale(1, RoundingMode.HALF_UP));
            s.setGradeTime(now);
        }
        s.setUpdateTime(now);
        if (s.getId() == null) {
            baseMapper.insert(s);
        } else {
            baseMapper.updateById(s);
        }

        // 答案全量替换：旧行软删（附件交 FileMaintenanceTask 回收），插入新行
        answerMapper.delete(new LambdaQueryWrapper<CourseAssignmentAnswer>()
                .eq(CourseAssignmentAnswer::getSubmissionId, s.getId()));
        for (CourseAssignmentAnswer ans : answers) {
            ans.setSubmissionId(s.getId());
            answerMapper.insert(ans);
        }
        draftStore.clear(assignmentId, studentUserId);
        return toView(s, answers, questions, false);
    }

    @Override
    public SubmissionView mySubmission(Long studentUserId, Long assignmentId) {
        CourseAssignment a = assignmentMapper.selectById(assignmentId);
        if (a == null || a.getStatus() == AssignmentStatusEnum.DRAFT) {
            throw new BusinessException(404, "作业不存在");
        }
        groupResolver.assertVisibleToStudent(a.getTeachInfoId(), studentUserId);
        CourseAssignmentSubmission s = latestOf(assignmentId, studentUserId);
        if (s == null) {
            return null;
        }
        List<CourseAssignmentQuestion> questions = listQuestions(assignmentId);
        List<CourseAssignmentAnswer> answers = listAnswers(s.getId());
        boolean reveal = AnswerVisibleEnum.orDefault(a.getAnswerVisible())
                .visible(a.getDeadline(), a.getStatus(), LocalDateTime.now());
        return toView(s, answers, questions, reveal);
    }

    @Override
    public List<SubmissionRowView> roster(Long teacherUserId, Long assignmentId) {
        CourseAssignment a = assignmentMapper.selectById(assignmentId);
        if (a == null) {
            throw new BusinessException(404, "作业不存在");
        }
        groupResolver.requireOwnedAnchor(a.getTeachInfoId(), teacherUserId);

        List<Long> rosterIds = groupResolver.rosterUserIds(a.getTeachInfoId());
        if (rosterIds.isEmpty()) {
            return List.of();
        }
        Map<Long, CourseAssignmentSubmission> byStudent = baseMapper.selectList(
                        new LambdaQueryWrapper<CourseAssignmentSubmission>()
                                .eq(CourseAssignmentSubmission::getAssignmentId, assignmentId)
                                .in(CourseAssignmentSubmission::getStudentId, rosterIds)
                                .orderByDesc(CourseAssignmentSubmission::getId)).stream()
                // 并发重复行兜底：id 大者（最新）生效
                .collect(Collectors.toMap(CourseAssignmentSubmission::getStudentId, Function.identity(),
                        (x, y) -> x.getId() >= y.getId() ? x : y));
        Map<Long, String> nameMap = userMapper.selectByIds(rosterIds).stream()
                .collect(Collectors.toMap(User::getId, User::getName, (x, y) -> x));
        Map<Long, String> studentNoMap = studentMapper.selectList(
                        new LambdaQueryWrapper<Student>().in(Student::getUserId, rosterIds)).stream()
                .collect(Collectors.toMap(Student::getUserId, Student::getStudentNo, (x, y) -> x));

        // 五态的 Redis 侧：已查看 Set 一次 SMEMBERS；草稿对未提交学生批量 EXISTS（pipeline）
        Set<Long> viewed = viewedStore.viewers(assignmentId);
        List<Long> notSubmitted = rosterIds.stream().filter(sid -> !byStudent.containsKey(sid)).toList();
        Set<Long> drafting = draftStore.draftingStudents(assignmentId, notSubmitted);

        return rosterIds.stream().map(sid -> {
            SubmissionRowView row = new SubmissionRowView();
            row.setStudentUserId(sid);
            row.setStudentName(nameMap.get(sid));
            row.setStudentNo(studentNoMap.get(sid));
            CourseAssignmentSubmission s = byStudent.get(sid);
            row.setSubmitted(s != null);
            if (s != null) {
                row.setSubmissionId(s.getId());
                row.setLate(s.getLate());
                row.setStatus(s.getStatus());
                row.setScore(s.getScore());
                row.setAutoScore(s.getAutoScore());
                row.setComment(s.getComment());
                row.setVersion(s.getVersion());
                row.setSubmitTime(s.getSubmitTime());
                row.setState(s.getStatus() != null ? s.getStatus().name() : "SUBMITTED");
            } else if (drafting.contains(sid)) {
                row.setState("DRAFTING");
            } else if (viewed.contains(sid)) {
                row.setState("VIEWED");
            } else {
                row.setState("NOT_VIEWED");
            }
            return row;
        }).sorted(Comparator.comparing(SubmissionRowView::getStudentNo,
                        Comparator.nullsLast(Comparator.naturalOrder())))
                .toList();
    }

    @Override
    public TeacherSubmissionView detailForTeacher(Long teacherUserId, Long submissionId) {
        CourseAssignmentSubmission s = baseMapper.selectById(submissionId);
        if (s == null) {
            throw new BusinessException(404, "提交记录不存在");
        }
        CourseAssignment a = assignmentMapper.selectById(s.getAssignmentId());
        if (a == null) {
            throw new BusinessException(404, "作业不存在");
        }
        groupResolver.requireOwnedAnchor(a.getTeachInfoId(), teacherUserId);
        return toTeacherView(s, a);
    }

    @Override
    @Transactional
    public TeacherSubmissionView grade(Long teacherUserId, Long submissionId, GradeRequest req) {
        CourseAssignmentSubmission s = baseMapper.selectById(submissionId);
        if (s == null) {
            throw new BusinessException(404, "提交记录不存在");
        }
        CourseAssignment a = assignmentMapper.selectById(s.getAssignmentId());
        if (a == null) {
            throw new BusinessException(404, "作业不存在");
        }
        groupResolver.requireOwnedAnchor(a.getTeachInfoId(), teacherUserId);
        if (req.getItems() == null || req.getItems().isEmpty()) {
            throw new BusinessException(400, "批改项不能为空");
        }

        List<CourseAssignmentAnswer> answers = listAnswers(submissionId);
        Map<Long, CourseAssignmentAnswer> byId = answers.stream()
                .collect(Collectors.toMap(CourseAssignmentAnswer::getId, Function.identity()));
        Map<Long, CourseAssignmentQuestion> questions = listQuestions(a.getId()).stream()
                .collect(Collectors.toMap(CourseAssignmentQuestion::getId, Function.identity()));

        LocalDateTime now = LocalDateTime.now();
        for (GradeRequest.GradeItem item : req.getItems()) {
            if (item == null) {
                throw new BusinessException(400, "批改项不能为空");
            }
            if (item.getAnswerId() == null) {
                throw new BusinessException(400, "批改项 id 不能为空");
            }
            if (item.getScore() == null) {
                throw new BusinessException(400, "分数不能为空");
            }
            CourseAssignmentAnswer ans = byId.get(item.getAnswerId());
            if (ans == null) {
                throw new BusinessException(400, "作答记录不属于该提交: " + item.getAnswerId());
            }
            CourseAssignmentQuestion q = questions.get(ans.getQuestionId());
            if (q == null) {
                throw new BusinessException(500, "作答记录缺少题目快照: " + ans.getQuestionId());
            }
            if (q.getType() != QuestionTypeEnum.ESSAY) {
                throw new BusinessException(400, "客观题不可人工改判（第 " + q.getSortOrder() + " 题）");
            }
            if (item.getScore().signum() < 0 || item.getScore().compareTo(q.getScore()) > 0) {
                throw new BusinessException(400,
                        "第 " + q.getSortOrder() + " 题分数须在 0 与 " + q.getScore() + " 之间");
            }
            ans.setFinalScore(item.getScore());
            if (item.getComment() != null) {
                ans.setComment(item.getComment());
            }
            ans.setUpdateTime(now);
            answerMapper.updateById(ans);
        }

        // 全部大题判完 → GRADED，总分 = Σ(客观 autoScore + 大题 finalScore)
        boolean allEssayGraded = answers.stream()
                .filter(ans -> {
                    CourseAssignmentQuestion q = questions.get(ans.getQuestionId());
                    return q != null && q.getType() == QuestionTypeEnum.ESSAY;
                })
                .allMatch(ans -> ans.getFinalScore() != null);
        boolean wasGraded = s.getStatus() == SubmissionStatusEnum.GRADED;
        if (allEssayGraded) {
            BigDecimal total = answers.stream()
                    .map(ans -> ans.getFinalScore() != null ? ans.getFinalScore() : BigDecimal.ZERO)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            s.setScore(total.setScale(1, RoundingMode.HALF_UP));
            s.setStatus(SubmissionStatusEnum.GRADED);
            s.setGradeTime(now);
        }
        if (req.getComment() != null) {
            s.setComment(req.getComment());
        }
        s.setUpdateTime(now);
        baseMapper.updateById(s);

        // 仅在 SUBMITTED → GRADED 跃迁时通知（改判不重复打扰）
        if (allEssayGraded && !wasGraded) {
            courseworkNoticeScenes.assignmentGraded(s.getStudentId(), a.getTitle(),
                    s.getScore().stripTrailingZeros().toPlainString());
        }
        return toTeacherView(s, a);
    }

    // ==================== 内部 ====================

    /** 学生可提交校验：草稿按不存在处理，CLOSED 拒写。 */
    private CourseAssignment requireSubmittable(Long assignmentId) {
        CourseAssignment a = assignmentMapper.selectById(assignmentId);
        if (a == null || a.getStatus() == AssignmentStatusEnum.DRAFT) {
            throw new BusinessException(404, "作业不存在");
        }
        if (a.getStatus() == AssignmentStatusEnum.CLOSED) {
            throw new BusinessException(400, "作业已关闭，停止提交");
        }
        return a;
    }

    private List<CourseAssignmentQuestion> listQuestions(Long assignmentId) {
        return questionMapper.selectList(new LambdaQueryWrapper<CourseAssignmentQuestion>()
                .eq(CourseAssignmentQuestion::getAssignmentId, assignmentId)
                .orderByAsc(CourseAssignmentQuestion::getSortOrder)
                .orderByAsc(CourseAssignmentQuestion::getId));
    }

    private List<CourseAssignmentAnswer> listAnswers(Long submissionId) {
        return answerMapper.selectList(new LambdaQueryWrapper<CourseAssignmentAnswer>()
                .eq(CourseAssignmentAnswer::getSubmissionId, submissionId)
                .orderByAsc(CourseAssignmentAnswer::getId));
    }

    /** 客观题答案形状校验（未答 → null）。 */
    private String normalizeObjectiveAnswer(QuestionTypeEnum type, JsonNode node) {
        if (node == null || node.isNull()) {
            return null;
        }
        boolean ok = switch (type) {
            case SINGLE_CHOICE -> node.isString();
            case MULTI_CHOICE, FILL_BLANK -> node.isArray()
                    && StreamSupport.stream(node.spliterator(), false).allMatch(JsonNode::isString);
            case JUDGE -> node.isBoolean();
            case ESSAY -> true; // 不经过本方法
        };
        if (!ok) {
            throw new BusinessException(400, "答案格式与题型不符: " + type.getCode());
        }
        return objectMapper.writeValueAsString(node);
    }

    /**
     * 大题答案归一：{text?, files?[{path, original}]}，附件 ≤3 且必须已在 file 模块完成上传
     * （bind 校验路径形状/归属/存在性）。requireFile=1 时必须带附件；未答（无文本无附件）→ null。
     */
    private String normalizeEssayAnswer(JsonNode raw, CourseAssignmentQuestion q) {
        boolean requireFile = q.getRequireFile() != null && q.getRequireFile() == 1;
        if (raw == null || raw.isNull()) {
            if (requireFile) {
                throw new BusinessException(400, "第 " + q.getSortOrder() + " 题必须上传附件");
            }
            return null;
        }
        if (!raw.isObject()) {
            throw new BusinessException(400, "简答题答案格式错误（须为 {text, files} 对象）");
        }
        String text = raw.path("text").isString() ? raw.path("text").asString() : null;
        boolean hasText = text != null && !text.isBlank();
        List<Map<String, String>> files = new ArrayList<>();
        JsonNode filesNode = raw.path("files");
        if (filesNode.isArray()) {
            if (filesNode.size() > 3) {
                throw new BusinessException(400, "每题最多 3 个附件");
            }
            for (JsonNode f : filesNode) {
                String path = f.path("path").isString() ? f.path("path").asString() : null;
                if (path == null || path.isBlank()) {
                    throw new BusinessException(400, "附件路径不能为空");
                }
                StoredFileRef ref = fileStorage.bind(FileBizEnum.COURSE_ASSIGNMENT_SUBMISSION, path);
                String original = f.path("original").isString() && !f.path("original").asString().isBlank()
                        ? f.path("original").asString().trim() : ref.originalName();
                if (original == null) {
                    original = ref.storedPath();
                }
                files.add(Map.of("path", ref.storedPath(), "original", original));
            }
        }
        if (requireFile && files.isEmpty()) {
            throw new BusinessException(400, "第 " + q.getSortOrder() + " 题必须上传附件");
        }
        if (!hasText && files.isEmpty()) {
            return null; // 未答
        }
        ObjectNode node = objectMapper.createObjectNode();
        if (hasText) {
            node.put("text", text);
        }
        if (!files.isEmpty()) {
            // Jackson 3 移除了 valueToTree：手工构树，行为等价
            ArrayNode filesOut = objectMapper.createArrayNode();
            for (Map<String, String> f : files) {
                ObjectNode fo = objectMapper.createObjectNode();
                fo.put("path", f.get("path"));
                fo.put("original", f.get("original"));
                filesOut.add(fo);
            }
            node.set("files", filesOut);
        }
        return objectMapper.writeValueAsString(node);
    }

    /** JSON 列 → JsonNode；null/损坏 → null。 */
    private JsonNode parseJson(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readTree(json);
        } catch (RuntimeException e) {
            return null;
        }
    }

    /** 提交视图 + 逐题作答；revealAnswer=true 时附标准答案/解析。 */
    SubmissionView toView(CourseAssignmentSubmission s, List<CourseAssignmentAnswer> answers,
                          List<CourseAssignmentQuestion> questions, boolean revealAnswer) {
        SubmissionView v = new SubmissionView();
        v.setId(s.getId());
        v.setAssignmentId(s.getAssignmentId());
        v.setSubmitTime(s.getSubmitTime());
        v.setLate(s.getLate());
        v.setStatus(s.getStatus());
        v.setScore(s.getScore());
        v.setAutoScore(s.getAutoScore());
        v.setComment(s.getComment());
        v.setVersion(s.getVersion());
        Map<Long, CourseAssignmentAnswer> byQuestion = answers.stream().collect(
                Collectors.toMap(CourseAssignmentAnswer::getQuestionId, x -> x, (x, y) -> x));
        List<AnswerView> answerViews = new ArrayList<>(questions.size());
        for (CourseAssignmentQuestion q : questions) {
            answerViews.add(toAnswerView(q, byQuestion.get(q.getId()), revealAnswer));
        }
        v.setAnswers(answerViews);
        return v;
    }

    AnswerView toAnswerView(CourseAssignmentQuestion q, CourseAssignmentAnswer ans, boolean revealAnswer) {
        AnswerView av = new AnswerView();
        av.setAnswerId(ans != null ? ans.getId() : null);
        av.setQuestionId(q.getId());
        av.setType(q.getType().getCode());
        av.setStem(q.getStem());
        av.setOptions(parseJson(q.getOptionsJson()));
        av.setQuestionScore(q.getScore());
        av.setSortOrder(q.getSortOrder());
        av.setRequireFile(q.getRequireFile() != null && q.getRequireFile() == 1);
        if (ans != null) {
            av.setMyAnswer(parseJson(ans.getAnswerJson()));
            av.setAutoScore(ans.getAutoScore());
            av.setFinalScore(ans.getFinalScore());
            av.setComment(ans.getComment());
        }
        if (revealAnswer) {
            av.setStandardAnswer(parseJson(q.getAnswerJson()));
            av.setAnalysis(q.getAnalysis());
        }
        return av;
    }

    /** 教师视图：恒含标准答案/解析。 */
    private TeacherSubmissionView toTeacherView(CourseAssignmentSubmission s, CourseAssignment a) {
        TeacherSubmissionView v = new TeacherSubmissionView();
        v.setId(s.getId());
        v.setAssignmentId(s.getAssignmentId());
        v.setStudentUserId(s.getStudentId());
        v.setSubmitTime(s.getSubmitTime());
        v.setLate(s.getLate());
        v.setStatus(s.getStatus());
        v.setScore(s.getScore());
        v.setAutoScore(s.getAutoScore());
        v.setComment(s.getComment());
        v.setVersion(s.getVersion());
        User student = userMapper.selectById(s.getStudentId());
        v.setStudentName(student != null ? student.getName() : null);
        Student stu = studentMapper.selectOne(new LambdaQueryWrapper<Student>()
                .eq(Student::getUserId, s.getStudentId()).last("LIMIT 1"));
        v.setStudentNo(stu != null ? stu.getStudentNo() : null);
        List<CourseAssignmentQuestion> questions = listQuestions(a.getId());
        Map<Long, CourseAssignmentAnswer> byQuestion = listAnswers(s.getId()).stream().collect(
                Collectors.toMap(CourseAssignmentAnswer::getQuestionId, Function.identity(), (x, y) -> x));
        v.setAnswers(questions.stream()
                .map(q -> toAnswerView(q, byQuestion.get(q.getId()), true))
                .toList());
        return v;
    }

    private CourseAssignmentSubmission latestOf(Long assignmentId, Long studentUserId) {
        return baseMapper.selectOne(new LambdaQueryWrapper<CourseAssignmentSubmission>()
                .eq(CourseAssignmentSubmission::getAssignmentId, assignmentId)
                .eq(CourseAssignmentSubmission::getStudentId, studentUserId)
                .orderByDesc(CourseAssignmentSubmission::getId)
                .last("LIMIT 1"));
    }
}
