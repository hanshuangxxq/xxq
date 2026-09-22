package com.xrq.xxq.module.coursework.assignment.service.impl;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.xrq.xxq.common.BusinessException;
import com.xrq.xxq.module.coursework.assignment.dto.QuestionInput;
import com.xrq.xxq.module.coursework.assignment.dto.QuestionRefInput;
import com.xrq.xxq.module.coursework.assignment.entity.CourseAssignmentQuestion;
import com.xrq.xxq.module.coursework.assignment.mapper.CourseAssignmentQuestionMapper;
import com.xrq.xxq.module.coursework.assignment.service.AssignmentQuestionService;
import com.xrq.xxq.module.coursework.common.MultiScoreRuleEnum;
import com.xrq.xxq.module.coursework.common.QuestionPayloadValidator;
import com.xrq.xxq.module.coursework.common.QuestionTypeEnum;
import com.xrq.xxq.module.coursework.question.entity.CourseQuestionBank;
import com.xrq.xxq.module.coursework.question.mapper.CourseQuestionBankMapper;

import lombok.RequiredArgsConstructor;
import tools.jackson.databind.ObjectMapper;

/** 作业题目快照服务。快照语义：内容完整复制，题库/源作业后续改动互不影响。 */
@Service
@RequiredArgsConstructor
public class AssignmentQuestionServiceImpl implements AssignmentQuestionService {

    private final CourseAssignmentQuestionMapper questionMapper;
    private final CourseQuestionBankMapper bankMapper;
    private final ObjectMapper objectMapper;

    @Override
    public List<CourseAssignmentQuestion> listQuestions(Long assignmentId) {
        return questionMapper.selectList(new LambdaQueryWrapper<CourseAssignmentQuestion>()
                .eq(CourseAssignmentQuestion::getAssignmentId, assignmentId)
                .orderByAsc(CourseAssignmentQuestion::getSortOrder)
                .orderByAsc(CourseAssignmentQuestion::getId));
    }

    @Override
    @Transactional
    public void replaceQuestions(Long assignmentId, Long teacherUserId,
                                 List<QuestionRefInput> refs, List<QuestionInput> inputs) {
        // 软删旧快照（@TableLogic；内容寻址附件不删，由 FileMaintenanceTask 回收）
        questionMapper.delete(new LambdaQueryWrapper<CourseAssignmentQuestion>()
                .eq(CourseAssignmentQuestion::getAssignmentId, assignmentId));

        LocalDateTime now = LocalDateTime.now();
        int order = 0;
        if (refs != null) {
            for (QuestionRefInput ref : refs) {
                if (ref.getQuestionId() == null) {
                    throw new BusinessException(400, "题库题目 id 不能为空");
                }
                CourseQuestionBank bank = bankMapper.selectById(ref.getQuestionId());
                if (bank == null || !teacherUserId.equals(bank.getOwnerTeacherId())) {
                    throw new BusinessException(404, "题库题目不存在: " + ref.getQuestionId());
                }
                BigDecimal score = ref.getScore() != null ? ref.getScore() : bank.getDefaultScore();
                requirePositiveScore(score);
                insertSnapshot(snapshotFromBank(assignmentId, bank, score, ++order, now));
            }
        }
        if (inputs != null) {
            for (QuestionInput input : inputs) {
                requirePositiveScore(input.getScore());
                QuestionPayloadValidator.validate(input.getType(), input.getOptions(), input.getAnswer());
                Long bankId = null;
                if (!Boolean.FALSE.equals(input.getSaveToBank())) {
                    bankId = saveToBank(teacherUserId, input, now);
                }
                insertSnapshot(snapshotFromInput(assignmentId, input, bankId, ++order, now));
            }
        }
    }

    @Override
    @Transactional
    public void cloneQuestions(Long sourceAssignmentId, Long targetAssignmentId) {
        List<CourseAssignmentQuestion> sources = listQuestions(sourceAssignmentId);
        LocalDateTime now = LocalDateTime.now();
        int order = 0;
        for (CourseAssignmentQuestion src : sources) {
            CourseAssignmentQuestion copy = new CourseAssignmentQuestion();
            copy.setAssignmentId(targetAssignmentId);
            copy.setSourceQuestionId(src.getSourceQuestionId());
            copy.setType(src.getType());
            copy.setStem(src.getStem());
            copy.setOptionsJson(src.getOptionsJson());
            copy.setAnswerJson(src.getAnswerJson());
            copy.setAnalysis(src.getAnalysis());
            copy.setScore(src.getScore());
            copy.setSortOrder(++order);
            copy.setScoreRule(src.getScoreRule());
            copy.setCaseSensitive(src.getCaseSensitive());
            copy.setRequireFile(src.getRequireFile());
            copy.setCreateTime(now);
            copy.setUpdateTime(now);
            questionMapper.insert(copy);
        }
    }

    @Override
    public BigDecimal totalScoreOf(Long assignmentId) {
        return listQuestions(assignmentId).stream()
                .map(CourseAssignmentQuestion::getScore)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    // ---- 内部 ----

    private void insertSnapshot(CourseAssignmentQuestion q) {
        questionMapper.insert(q);
    }

    private CourseAssignmentQuestion snapshotFromBank(Long assignmentId, CourseQuestionBank bank,
                                                      BigDecimal score, int order, LocalDateTime now) {
        CourseAssignmentQuestion q = new CourseAssignmentQuestion();
        q.setAssignmentId(assignmentId);
        q.setSourceQuestionId(bank.getId());
        q.setType(bank.getType());
        q.setStem(bank.getStem());
        q.setOptionsJson(bank.getOptionsJson());
        q.setAnswerJson(bank.getAnswerJson());
        q.setAnalysis(bank.getAnalysis());
        q.setScore(score);
        q.setSortOrder(order);
        q.setScoreRule(bank.getScoreRule());
        q.setCaseSensitive(bank.getCaseSensitive());
        q.setRequireFile(bank.getRequireFile());
        q.setCreateTime(now);
        q.setUpdateTime(now);
        return q;
    }

    private CourseAssignmentQuestion snapshotFromInput(Long assignmentId, QuestionInput input,
                                                       Long bankId, int order, LocalDateTime now) {
        CourseAssignmentQuestion q = new CourseAssignmentQuestion();
        q.setAssignmentId(assignmentId);
        q.setSourceQuestionId(bankId);
        q.setType(input.getType());
        q.setStem(input.getStem().trim());
        q.setOptionsJson(input.getOptions() == null ? null
                : objectMapper.writeValueAsString(input.getOptions()));
        q.setAnswerJson(input.getAnswer() == null ? null
                : objectMapper.writeValueAsString(input.getAnswer()));
        q.setAnalysis(input.getAnalysis());
        q.setScore(input.getScore());
        q.setSortOrder(order);
        q.setScoreRule(input.getType() == QuestionTypeEnum.MULTI_CHOICE
                ? MultiScoreRuleEnum.orDefault(input.getScoreRule()) : null);
        q.setCaseSensitive(input.getType() == QuestionTypeEnum.FILL_BLANK
                && Boolean.TRUE.equals(input.getCaseSensitive()) ? 1 : 0);
        q.setRequireFile(input.getType() == QuestionTypeEnum.ESSAY
                && Boolean.TRUE.equals(input.getRequireFile()) ? 1 : 0);
        q.setCreateTime(now);
        q.setUpdateTime(now);
        return q;
    }

    /** 直接录入的题同步入题库（与题库 create 同规则；defaultScore 取本题配分）。 */
    private Long saveToBank(Long teacherUserId, QuestionInput input, LocalDateTime now) {
        CourseQuestionBank bank = new CourseQuestionBank();
        bank.setOwnerTeacherId(teacherUserId);
        bank.setCourseId(input.getCourseId());
        bank.setCampaignId(input.getCampaignId());
        bank.setType(input.getType());
        bank.setStem(input.getStem().trim());
        bank.setOptionsJson(input.getOptions() == null ? null
                : objectMapper.writeValueAsString(input.getOptions()));
        bank.setAnswerJson(input.getAnswer() == null ? null
                : objectMapper.writeValueAsString(input.getAnswer()));
        bank.setAnalysis(input.getAnalysis());
        bank.setDefaultScore(input.getScore());
        bank.setScoreRule(input.getType() == QuestionTypeEnum.MULTI_CHOICE
                ? MultiScoreRuleEnum.orDefault(input.getScoreRule()) : null);
        bank.setCaseSensitive(input.getType() == QuestionTypeEnum.FILL_BLANK
                && Boolean.TRUE.equals(input.getCaseSensitive()) ? 1 : 0);
        bank.setRequireFile(input.getType() == QuestionTypeEnum.ESSAY
                && Boolean.TRUE.equals(input.getRequireFile()) ? 1 : 0);
        bank.setCreateTime(now);
        bank.setUpdateTime(now);
        bankMapper.insert(bank);
        return bank.getId();
    }

    private static void requirePositiveScore(BigDecimal score) {
        if (score == null || score.signum() <= 0) {
            throw new BusinessException(400, "题目配分必须大于 0");
        }
    }
}
