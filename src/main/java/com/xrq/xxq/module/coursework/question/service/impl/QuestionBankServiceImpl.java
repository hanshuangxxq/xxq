package com.xrq.xxq.module.coursework.question.service.impl;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.xrq.xxq.common.BusinessException;
import com.xrq.xxq.common.PageQuery;
import com.xrq.xxq.common.PageResult;
import com.xrq.xxq.module.coursework.common.MultiScoreRuleEnum;
import com.xrq.xxq.module.coursework.common.QuestionPayloadValidator;
import com.xrq.xxq.module.coursework.common.QuestionTypeEnum;
import com.xrq.xxq.module.coursework.question.dto.QuestionSaveRequest;
import com.xrq.xxq.module.coursework.question.dto.QuestionView;
import com.xrq.xxq.module.coursework.question.entity.CourseQuestionBank;
import com.xrq.xxq.module.coursework.question.mapper.CourseQuestionBankMapper;
import com.xrq.xxq.module.coursework.question.service.QuestionBankService;

import lombok.RequiredArgsConstructor;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/** 题库服务实现。归属口径：owner_teacher_id = 当前教师 user.id，越权一律 404（不暴露他人题目存在性）。 */
@Service
@RequiredArgsConstructor
public class QuestionBankServiceImpl extends ServiceImpl<CourseQuestionBankMapper, CourseQuestionBank>
        implements QuestionBankService {

    private static final BigDecimal DEFAULT_SCORE = new BigDecimal("5.0");

    private final ObjectMapper objectMapper;

    @Override
    public PageResult<QuestionView> list(Long ownerUserId, Long courseId, Long campaignId,
                                         QuestionTypeEnum type, String keyword, PageQuery pageQuery) {
        Page<CourseQuestionBank> page = baseMapper.selectPage(pageQuery.toPage(),
                new LambdaQueryWrapper<CourseQuestionBank>()
                        .eq(CourseQuestionBank::getOwnerTeacherId, ownerUserId)
                        .eq(courseId != null, CourseQuestionBank::getCourseId, courseId)
                        .eq(campaignId != null, CourseQuestionBank::getCampaignId, campaignId)
                        .eq(type != null, CourseQuestionBank::getType, type)
                        .like(keyword != null && !keyword.isBlank(),
                                CourseQuestionBank::getStem, keyword)
                        .orderByDesc(CourseQuestionBank::getId));
        List<QuestionView> views = page.getRecords().stream().map(this::toView).toList();
        return PageResult.of(page, views);
    }

    @Override
    @Transactional
    public QuestionView create(Long ownerUserId, QuestionSaveRequest req) {
        validate(req);
        CourseQuestionBank q = new CourseQuestionBank();
        applyPayload(q, req);
        q.setOwnerTeacherId(ownerUserId);
        q.setCreateTime(LocalDateTime.now());
        q.setUpdateTime(LocalDateTime.now());
        baseMapper.insert(q);
        return toView(q);
    }

    @Override
    @Transactional
    public QuestionView update(Long ownerUserId, Long id, QuestionSaveRequest req) {
        CourseQuestionBank q = requireOwned(id, ownerUserId);
        validate(req);
        applyPayload(q, req);
        q.setUpdateTime(LocalDateTime.now());
        baseMapper.updateById(q);
        return toView(q);
    }

    @Override
    @Transactional
    public void delete(Long ownerUserId, Long id) {
        requireOwned(id, ownerUserId);
        baseMapper.deleteById(id);
    }

    /** 结构 + 字段校验。 */
    private static void validate(QuestionSaveRequest req) {
        if (req.getStem() == null || req.getStem().isBlank()) {
            throw new BusinessException(400, "题干不能为空");
        }
        if (req.getCourseId() != null && req.getCampaignId() != null) {
            throw new BusinessException(400, "课程标签与公选活动标签只能二选一");
        }
        BigDecimal score = req.getDefaultScore() != null ? req.getDefaultScore() : DEFAULT_SCORE;
        if (score.signum() <= 0) {
            throw new BusinessException(400, "默认配分必须大于 0");
        }
        QuestionPayloadValidator.validate(req.getType(), req.getOptions(), req.getAnswer());
    }

    private void applyPayload(CourseQuestionBank q, QuestionSaveRequest req) {
        q.setType(req.getType());
        q.setStem(req.getStem().trim());
        q.setOptionsJson(req.getOptions() == null ? null
                : objectMapper.writeValueAsString(req.getOptions()));
        q.setAnswerJson(req.getAnswer() == null ? null
                : objectMapper.writeValueAsString(req.getAnswer()));
        q.setAnalysis(req.getAnalysis());
        q.setDefaultScore(req.getDefaultScore() != null ? req.getDefaultScore() : DEFAULT_SCORE);
        q.setScoreRule(req.getType() == QuestionTypeEnum.MULTI_CHOICE
                ? MultiScoreRuleEnum.orDefault(req.getScoreRule()) : null);
        q.setCaseSensitive(req.getType() == QuestionTypeEnum.FILL_BLANK
                && Boolean.TRUE.equals(req.getCaseSensitive()) ? 1 : 0);
        q.setRequireFile(req.getType() == QuestionTypeEnum.ESSAY
                && Boolean.TRUE.equals(req.getRequireFile()) ? 1 : 0);
        q.setCourseId(req.getCourseId());
        q.setCampaignId(req.getCampaignId());
    }

    private CourseQuestionBank requireOwned(Long id, Long ownerUserId) {
        CourseQuestionBank q = baseMapper.selectById(id);
        if (q == null || !ownerUserId.equals(q.getOwnerTeacherId())) {
            throw new BusinessException(404, "题目不存在");
        }
        return q;
    }

    private QuestionView toView(CourseQuestionBank q) {
        QuestionView v = new QuestionView();
        v.setId(q.getId());
        v.setCourseId(q.getCourseId());
        v.setCampaignId(q.getCampaignId());
        v.setType(q.getType());
        v.setStem(q.getStem());
        v.setOptions(parse(q.getOptionsJson()));
        v.setAnswer(parse(q.getAnswerJson()));
        v.setAnalysis(q.getAnalysis());
        v.setDefaultScore(q.getDefaultScore());
        v.setScoreRule(q.getScoreRule() != null ? q.getScoreRule().getCode() : null);
        v.setCaseSensitive(q.getCaseSensitive() != null && q.getCaseSensitive() == 1);
        v.setRequireFile(q.getRequireFile() != null && q.getRequireFile() == 1);
        v.setCreateTime(q.getCreateTime());
        return v;
    }

    /** JSON 列 → JsonNode；null/损坏 → null（题库行损坏不阻断列表，由编辑时修正）。 */
    private JsonNode parse(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readTree(json);
        } catch (RuntimeException e) {
            return null;
        }
    }
}
