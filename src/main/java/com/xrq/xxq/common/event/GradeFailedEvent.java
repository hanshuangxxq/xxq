package com.xrq.xxq.common.event;

/**
 * 成绩不及格事件：成绩录入后总评 < 60 时发布，由通知监听器异步发站内消息。
 *
 * @param studentUserId 学生 user.id
 * @param courseName    课程名（用于通知文案）
 * @param totalScore    总评成绩（已格式化的字符串，如 "58"）
 */
public record GradeFailedEvent(Long studentUserId, String courseName, String totalScore) {
}
