package com.xrq.xxq.common.event;

/**
 * 选课活动开放事件：活动转为 OPEN 时发布，由通知监听器异步广播给全体学生。
 *
 * @param courseName   活动名（课程名，用于通知文案）
 * @param endTimeText  选课截止时间文本
 * @param senderId     发起人 user.id（用于审计）
 */
public record CampaignOpenedEvent(String courseName, String endTimeText, Long senderId) {
}
