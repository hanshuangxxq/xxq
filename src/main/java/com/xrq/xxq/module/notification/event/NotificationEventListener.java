package com.xrq.xxq.module.notification.event;

import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import com.xrq.xxq.common.event.CampaignOpenedEvent;
import com.xrq.xxq.common.event.GradeFailedEvent;
import com.xrq.xxq.common.event.ReviewStatusEvent;
import com.xrq.xxq.common.event.WarningActivatedEvent;
import com.xrq.xxq.module.notification.entity.NotificationTargetEnum;
import com.xrq.xxq.module.notification.entity.NotificationTypeEnum;
import com.xrq.xxq.module.notification.service.NotificationService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 业务事件 -> 站内通知的监听器。
 * <p>
 * 统一用 {@link TransactionalEventListener}(AFTER_COMMIT) 在业务事务提交后才发通知，
 * 使业务服务不再直接依赖 NotificationService，通知失败完全隔离、事务回滚则不发。
 * fallbackExecution=true 保证无事务上下文时也能触发。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationEventListener {

    private final NotificationService notificationService;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onGradeFailed(GradeFailedEvent event) {
        try {
            notificationService.sendToUser(event.studentUserId(), NotificationTypeEnum.GRADE,
                    "成绩通知",
                    "您的《" + event.courseName() + "》总评成绩为 " + event.totalScore()
                            + " 分，未及格，请关注后续补考安排。");
        } catch (Exception e) {
            log.warn("成绩不及格通知失败: studentUserId={}", event.studentUserId(), e);
        }
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onWarningActivated(WarningActivatedEvent event) {
        try {
            notificationService.sendToUser(event.studentUserId(), NotificationTypeEnum.WARNING,
                    event.levelDescription(),
                    "您触发" + event.levelDescription() + "：" + event.reason()
                            + "，请尽快联系辅导员/教务制定改进计划。");
        } catch (Exception e) {
            log.warn("预警通知发送失败: studentUserId={}", event.studentUserId(), e);
        }
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onReviewStatus(ReviewStatusEvent event) {
        try {
            notificationService.sendToUser(event.studentUserId(), NotificationTypeEnum.GRADE,
                    event.title(), event.content());
        } catch (Exception e) {
            log.warn("复核通知发送失败: studentUserId={}", event.studentUserId(), e);
        }
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onCampaignOpened(CampaignOpenedEvent event) {
        try {
            notificationService.broadcast(NotificationTypeEnum.SELECTION, NotificationTargetEnum.STUDENT,
                    "选课开始通知",
                    "选课活动《" + event.courseName() + "》已开放，截止时间 " + event.endTimeText()
                            + "，请及时登录系统完成选课。",
                    event.senderId());
        } catch (Exception e) {
            log.warn("选课开始广播通知失败: courseName={}", event.courseName(), e);
        }
    }
}
