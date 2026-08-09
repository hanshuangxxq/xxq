package com.xrq.xxq.common.event;

/**
 * 实践与创新模块通知事件：选题/报名/申报审核、论文/报告评审结果等场景发布，
 * 由通知监听器异步通知学生。
 *
 * @param studentUserId 学生 user.id
 * @param title         通知标题（由业务层拼接）
 * @param content       通知内容（由业务层拼接）
 */
public record PracticeNoticeEvent(Long studentUserId, String title, String content) {
}
