package com.xrq.xxq.module.notification.notice;

import org.springframework.stereotype.Component;

import com.xrq.xxq.module.notification.entity.NotificationTypeEnum;

/**
 * 成绩域通知场景。方法体为空：通知声明在注解上，由 {@link NotifyAspect} 切面负责发送。
 * <p>
 * 注意：场景方法必须经 Spring 代理调用（业务类注入本组件后调用），禁止同类内自调用（绕过代理不会发通知）。
 */
@Component
public class ScoreNoticeScenes {

    /** 录入即生效：新建成绩且不及格时通知学生（调用点在批量录入循环内，每个不及格学生一条）。 */
    @NotifyUser(type = NotificationTypeEnum.GRADE, userId = "#studentUserId",
            title = "'成绩通知'",
            content = "'您的《' + #courseName + '》总评成绩为 ' + #totalScore + ' 分，未及格，请关注后续补考安排。'")
    public void gradeFailed(Long studentUserId, String courseName, String totalScore) {
    }

    /** 成绩复核：教师已回复。 */
    @NotifyUser(type = NotificationTypeEnum.GRADE, userId = "#studentUserId",
            title = "'成绩复核进展'", content = "'教师已回复您的成绩复核申请，请查看。'")
    public void reviewReplied(Long studentUserId) {
    }

    /** 成绩复核：终审处理完毕。 */
    @NotifyUser(type = NotificationTypeEnum.GRADE, userId = "#studentUserId",
            title = "'成绩复核结果'", content = "'您的成绩复核申请已处理完毕，请查看结果。'")
    public void reviewResolved(Long studentUserId) {
    }
}
