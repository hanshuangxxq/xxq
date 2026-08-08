package com.xrq.xxq.module.course.util;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.support.SFunction;
import com.xrq.xxq.module.course.entity.Course;

/**
 * 公选课/常规课路由工具：按 source 在查询条件中分派 campaignId 或 courseId。
 * <p>
 * 公选课解耦后，下游表通过 campaign_id（公选课）或 course_id（常规课）引用课程。
 * 本工具收口散落在 Score/Exam/TeachInfo 查询中重复的
 * {@code if (SOURCE_SELECTION_CAMPAIGN.equals(source)) ... else ...} 路由块。
 */
public final class CourseRouting {

    private CourseRouting() {
    }

    /**
     * 按 source 给 wrapper 追加课程路由条件：公选课按 campaignGetter，否则按 courseGetter。
     *
     * @param w              查询 wrapper（链式返回）
     * @param id             课程 id（公选课时为 campaignId，常规课时为 courseId）
     * @param source         来源（{@link Course#SOURCE_SELECTION_CAMPAIGN} 表示公选课）
     * @param campaignGetter 实体 campaignId 字段引用
     * @param courseGetter   实体 courseId 字段引用
     * @return 原 wrapper（便于链式调用）
     */
    public static <T> LambdaQueryWrapper<T> apply(LambdaQueryWrapper<T> w, Long id, String source,
                                                  SFunction<T, ?> campaignGetter, SFunction<T, ?> courseGetter) {
        if (Course.SOURCE_SELECTION_CAMPAIGN.equals(source)) {
            w.eq(campaignGetter, id);
        } else {
            w.eq(courseGetter, id);
        }
        return w;
    }
}
