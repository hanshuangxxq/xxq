package com.xrq.xxq.common.event;

/**
 * 学业预警激活事件：扫描发现学生触发预警级别时发布，由通知监听器异步发站内消息。
 *
 * @param studentUserId   学生 user.id
 * @param levelDescription 预警级别描述（如 "黄色预警"）
 * @param reason          触发原因
 */
public record WarningActivatedEvent(Long studentUserId, String levelDescription, String reason) {
}
