package com.xrq.xxq.module.coursework.question.service;

import com.xrq.xxq.common.PageQuery;
import com.xrq.xxq.common.PageResult;
import com.xrq.xxq.module.coursework.common.QuestionTypeEnum;
import com.xrq.xxq.module.coursework.question.dto.QuestionSaveRequest;
import com.xrq.xxq.module.coursework.question.dto.QuestionView;

/** 教师个人题库。 */
public interface QuestionBankService {

    /** 分页：按课程标签/题型/关键词（题干模糊）筛选，仅本人题目。 */
    PageResult<QuestionView> list(Long ownerUserId, Long courseId, Long campaignId,
                                  QuestionTypeEnum type, String keyword, PageQuery pageQuery);

    /** 录入题目（结构校验后落库）。 */
    QuestionView create(Long ownerUserId, QuestionSaveRequest req);

    /** 修改（仅本人题目）。 */
    QuestionView update(Long ownerUserId, Long id, QuestionSaveRequest req);

    /** 软删（仅本人题目；已被作业快照引用的题可删，快照不受影响）。 */
    void delete(Long ownerUserId, Long id);
}
