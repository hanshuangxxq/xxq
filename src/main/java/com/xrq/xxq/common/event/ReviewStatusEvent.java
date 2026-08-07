package com.xrq.xxq.common.event;

/**
 * 成绩复核状态变更事件：教师回复/教务终审后发布，由通知监听器异步通知学生。
 *
 * @param studentUserId 学生 user.id
 * @param title         通知标题（由业务层拼接）
 * @param content       通知内容（由业务层拼接）
 */
public record ReviewStatusEvent(Long studentUserId, String title, String content) {
}
