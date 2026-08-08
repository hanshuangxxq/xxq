package com.xrq.xxq.module.score.util;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.xrq.xxq.module.course.util.CourseRouting;
import com.xrq.xxq.module.score.entity.Score;
import com.xrq.xxq.module.score.entity.ScoreTypeEnum;

/**
 * Score 查询构造工具：收口「正常成绩(REGULAR) + 公选课/常规课路由」的查询开头。
 * <p>
 * 替代散落在成绩分析/统计/补考候选查询中重复的
 * {@code new LambdaQueryWrapper<Score>().eq(Score::getScoreType, REGULAR) + 路由} 样板。
 * 返回链式 wrapper，调用方继续追加 semester/studentUserIds/totalScore 等条件。
 */
public final class ScoreQueries {

    private ScoreQueries() {
    }

    /**
     * 构造「正常成绩 + 课程路由」基础查询。
     *
     * @param id     课程 id（公选课时为 campaignId，常规课时为 courseId）
     * @param source 来源（{@link com.xrq.xxq.module.course.entity.Course#SOURCE_SELECTION_CAMPAIGN} 表示公选课）
     * @return 已含 scoreType=REGULAR + 课程路由条件的链式 wrapper
     */
    public static LambdaQueryWrapper<Score> regularByCourseOrCampaign(Long id, String source) {
        LambdaQueryWrapper<Score> w = new LambdaQueryWrapper<Score>()
                .eq(Score::getScoreType, ScoreTypeEnum.REGULAR);
        return CourseRouting.apply(w, id, source, Score::getCampaignId, Score::getCourseId);
    }
}
