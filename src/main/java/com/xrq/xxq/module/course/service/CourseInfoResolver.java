package com.xrq.xxq.module.course.service;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import com.xrq.xxq.module.course.entity.Course;
import com.xrq.xxq.module.course.entity.CurseEnum;
import com.xrq.xxq.module.course.mapper.CourseMapper;
import com.xrq.xxq.module.selection.entity.SelectionCampaign;
import com.xrq.xxq.module.selection.mapper.SelectionCampaignMapper;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 课程信息统一解析器。
 * <p>
 * 公选课不再存入 course 表后，下游表通过 {@code course_id}（常规课）或 {@code campaign_id}
 *（公选课）引用课程。本组件按非空列路由到 {@code course} 或 {@code selection_campaign} 表，
 * 统一返回 {@link CourseInfo}，避免各富化点重复实现"两表合并"逻辑。
 */
@Component
public class CourseInfoResolver {

    private final CourseMapper courseMapper;
    private final SelectionCampaignMapper selectionCampaignMapper;

    public CourseInfoResolver(CourseMapper courseMapper, SelectionCampaignMapper selectionCampaignMapper) {
        this.courseMapper = courseMapper;
        this.selectionCampaignMapper = selectionCampaignMapper;
    }

    /** 课程信息快照（名称/编号/学分/学时/类型/描述）。 */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CourseInfo {
        private String courseName;
        private String courseCode;
        private Integer credit;
        private Integer courseHour;
        private CurseEnum courseType;
        private String description;

        public String courseTypeDesc() {
            return courseType != null ? courseType.getDescription() : null;
        }
    }

    /** 单条解析：campaignId 非空走活动，否则走 course。 */
    public CourseInfo resolveOne(Long courseId, Long campaignId) {
        if (campaignId != null) {
            SelectionCampaign c = selectionCampaignMapper.selectById(campaignId);
            return c != null ? toInfo(c) : null;
        }
        if (courseId != null) {
            Course c = courseMapper.selectById(courseId);
            return c != null ? toInfo(c) : null;
        }
        return null;
    }

    /** 按 course.id 批量解析（常规课）。 */
    public Map<Long, CourseInfo> resolveCourses(Collection<Long> courseIds) {
        List<Long> ids = nonNullDistinct(courseIds);
        if (ids.isEmpty()) {
            return Map.of();
        }
        return courseMapper.selectByIds(ids).stream()
                .collect(Collectors.toMap(Course::getId, CourseInfoResolver::toInfo, (a, b) -> a));
    }

    /** 按 selection_campaign.id 批量解析（公选课）。 */
    public Map<Long, CourseInfo> resolveCampaigns(Collection<Long> campaignIds) {
        List<Long> ids = nonNullDistinct(campaignIds);
        if (ids.isEmpty()) {
            return Map.of();
        }
        return selectionCampaignMapper.selectByIds(ids).stream()
                .collect(Collectors.toMap(SelectionCampaign::getId, CourseInfoResolver::toInfo, (a, b) -> a));
    }

    private static CourseInfo toInfo(Course c) {
        return new CourseInfo(c.getCourseName(), c.getCourseCode(), c.getCredit(),
                c.getCourseHour(), c.getCourseType(), c.getDescription());
    }

    private static CourseInfo toInfo(SelectionCampaign c) {
        return new CourseInfo(c.getCourseName(), c.getCourseCode(), c.getCredit(),
                c.getCourseHour(), c.getCourseType(), c.getDescription());
    }

    private static List<Long> nonNullDistinct(Collection<Long> ids) {
        if (ids == null) {
            return List.of();
        }
        return ids.stream().filter(Objects::nonNull).distinct().toList();
    }
}
