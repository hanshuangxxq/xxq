package com.xrq.xxq.module.notification.notice;

import org.springframework.stereotype.Component;

import com.xrq.xxq.module.notification.entity.NotificationTargetEnum;
import com.xrq.xxq.module.notification.entity.NotificationTypeEnum;

/**
 * 选课域通知场景。方法体为空：通知声明在注解上，由 {@link NotifyAspect} 切面负责发送。
 * <p>
 * 注意：场景方法必须经 Spring 代理调用（业务类注入本组件后调用），禁止同类内自调用（绕过代理不会发通知）。
 */
@Component
public class SelectionNoticeScenes {

    /** 选课活动开放：向全体学生广播。 */
    @NotifyBroadcast(type = NotificationTypeEnum.SELECTION, target = NotificationTargetEnum.STUDENT,
            senderId = "#senderId",
            title = "'选课开始通知'",
            content = "'选课活动《' + #courseName + '》已开放，截止时间 ' + #endTimeText + '，请及时登录系统完成选课。'")
    public void campaignOpened(String courseName, String endTimeText, Long senderId) {
    }
}
