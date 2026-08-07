package com.xrq.xxq.module.analysis.util;

import java.util.HashMap;
import java.util.Map;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.xrq.xxq.module.course.entity.Course;
import com.xrq.xxq.module.course.mapper.CourseMapper;
import com.xrq.xxq.module.score.entity.Score;
import com.xrq.xxq.module.selection.entity.SelectionCampaign;
import com.xrq.xxq.module.selection.mapper.SelectionCampaignMapper;

/**
 * 学分来源：常规课走 course 表、公选课走 selection_campaign 表，**分表存储**。
 * <p>
 * course.id 与 selection_campaign.id 均为自增、数值空间重叠，若合并到同一 {@code Map<Long,Integer>}
 * 会在 id 相同时串台（常规课拿到公选课学分、或反之）。本类用两个独立 map + 按成绩非空列路由查找，
 * 并提供碰撞安全的分组键 {@link #keyOf(Score)} 供"按课程去重/分组"场景使用。
 */
public final class CreditSource {

    private final Map<Long, Integer> courseCredit;
    private final Map<Long, Integer> campaignCredit;

    public CreditSource(Map<Long, Integer> courseCredit, Map<Long, Integer> campaignCredit) {
        this.courseCredit = courseCredit != null ? courseCredit : new HashMap<>();
        this.campaignCredit = campaignCredit != null ? campaignCredit : new HashMap<>();
    }

    /** 加载全量学分（常规课 + 公选课），分表存放。 */
    public static CreditSource loadAll(CourseMapper courseMapper, SelectionCampaignMapper selectionCampaignMapper) {
        Map<Long, Integer> cc = new HashMap<>();
        courseMapper.selectList(new LambdaQueryWrapper<>()).stream()
                .filter(c -> c.getCredit() != null)
                .forEach(c -> cc.put(c.getId(), c.getCredit()));
        Map<Long, Integer> pc = new HashMap<>();
        selectionCampaignMapper.selectList(new LambdaQueryWrapper<>()).stream()
                .filter(c -> c.getCredit() != null)
                .forEach(c -> pc.put(c.getId(), c.getCredit()));
        return new CreditSource(cc, pc);
    }

    /** 按成绩非空列路由查学分：常规课 courseId 查 course 表，公选课 campaignId 查 selection_campaign 表。 */
    public Integer creditOf(Score s) {
        if (s == null) {
            return null;
        }
        if (s.getCourseId() != null) {
            return courseCredit.get(s.getCourseId());
        }
        if (s.getCampaignId() != null) {
            return campaignCredit.get(s.getCampaignId());
        }
        return null;
    }

    /**
     * 成绩所属课程/活动的碰撞安全分组键。
     * 加前缀 "K"(course)/"C"(campaign) 区分两表，避免同数值 id 把常规课与公选课混为一组。
     */
    public static String keyOf(Score s) {
        if (s == null) {
            return null;
        }
        if (s.getCourseId() != null) {
            return "K" + s.getCourseId();
        }
        if (s.getCampaignId() != null) {
            return "C" + s.getCampaignId();
        }
        return null;
    }
}
