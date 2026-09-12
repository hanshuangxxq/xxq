package com.xrq.xxq.module.notification.notice;

import org.springframework.stereotype.Component;

import com.xrq.xxq.module.notification.entity.NotificationTypeEnum;

/**
 * 学业预警域通知场景。方法体为空：通知声明在注解上，由 {@link NotifyAspect} 切面负责发送。
 * <p>
 * 注意：场景方法必须经 Spring 代理调用（业务类注入本组件后调用），禁止同类内自调用（绕过代理不会发通知）。
 */
@Component
public class WarningNoticeScenes {

    /** 预警激活：通知学生本人（标题即预警级别描述）。 */
    @NotifyUser(type = NotificationTypeEnum.WARNING, userId = "#studentUserId",
            title = "#levelDescription",
            content = "'您触发' + #levelDescription + '：' + #reason + '，请尽快联系辅导员/教务制定改进计划。'")
    public void warningActivated(Long studentUserId, String levelDescription, String reason) {
    }
}
