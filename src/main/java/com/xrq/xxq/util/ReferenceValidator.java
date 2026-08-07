package com.xrq.xxq.util;

import java.io.Serializable;

import org.springframework.stereotype.Component;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.xrq.xxq.common.BusinessException;
import com.xrq.xxq.module.course.mapper.CourseMapper;
import com.xrq.xxq.module.selection.mapper.SelectionCampaignMapper;

import lombok.RequiredArgsConstructor;

/**
 * 引用完整性校验器：把数据库外键约束迁移到应用层。
 *
 * <p>
 * 在 insert/update 前校验外键引用的实体是否存在。{@code selectById} 自动尊重
 * {@code @TableLogic} 软删除（软删记录视为不存在，阻止新写入悬挂引用）。
 *
 * <p>
 * 设计：调用方传入已注入的 {@link BaseMapper}（多数 Service 已持有对应 Mapper），
 * 避免在本类集中注入 30+ Mapper。课程引用因公选课解耦存在 course_id/campaign_id
 * 双路由，由 {@link #requireCourseRef} 统一处理。
 */
@Component
@RequiredArgsConstructor
public class ReferenceValidator {

    private final CourseMapper courseMapper;
    private final SelectionCampaignMapper selectionCampaignMapper;

    /**
     * 校验引用存在：id 非空时确认被引用实体存在；id 为 null 则跳过（表示可空外键未赋值）。
     *
     * @param mapper     被引用实体的 Mapper
     * @param id         引用 id；null 跳过
     * @param entityName 实体中文名，用于错误提示
     */
    public <T> void requireExists(BaseMapper<T> mapper, Serializable id, String entityName) {
        if (id == null) {
            return;
        }
        if (mapper.selectById(id) == null) {
            throw new BusinessException(400, "引用的" + entityName + "不存在(id=" + id + ")");
        }
    }

    /**
     * 课程引用校验（公选课双路由）：campaignId 非空走 selection_campaign，否则 courseId 走 course。
     * 两者皆 null 时跳过（调用方按需另行校验“必填”）。
     *
     * @param courseId    常规课 course.id（公选课时为 null）
     * @param campaignId  公选课 selection_campaign.id（常规课时为 null）
     */
    public void requireCourseRef(Long courseId, Long campaignId) {
        if (campaignId != null) {
            if (selectionCampaignMapper.selectById(campaignId) == null) {
                throw new BusinessException(400, "引用的选课活动不存在(id=" + campaignId + ")");
            }
        } else if (courseId != null) {
            if (courseMapper.selectById(courseId) == null) {
                throw new BusinessException(400, "引用的课程不存在(id=" + courseId + ")");
            }
        }
    }
}
