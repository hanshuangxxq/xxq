package com.xrq.xxq.module.notification.notice;

import org.springframework.stereotype.Component;

import com.xrq.xxq.module.notification.entity.NotificationTypeEnum;

/**
 * 课程课业域通知场景（作业/公告）。方法体为空：通知声明在注解上，由 {@link NotifyAspect} 切面发送。
 * <p>
 * 注意：场景方法必须经 Spring 代理调用（业务类注入本组件后调用），禁止同类内自调用（绕过代理不会发通知）。
 */
@Component
public class CourseworkNoticeScenes {

    /** 作业发布（逐个学生调用一次）。 */
    @NotifyUser(type = NotificationTypeEnum.COURSE, userId = "#studentUserId",
            title = "'新作业发布'",
            content = "'《' + #courseName + '》发布了新作业：' + #assignmentTitle + '，截止时间：' + #deadlineText + '。'")
    public void assignmentPublished(Long studentUserId, String courseName, String assignmentTitle,
                                    String deadlineText) {
    }

    /** 作业批改完成。 */
    @NotifyUser(type = NotificationTypeEnum.COURSE, userId = "#studentUserId",
            title = "'作业批改完成'",
            content = "'你的作业《' + #assignmentTitle + '》已批改，得分：' + #scoreText + '。'")
    public void assignmentGraded(Long studentUserId, String assignmentTitle, String scoreText) {
    }

    /** 课程公告发布（逐个学生调用一次；编辑不重复通知）。 */
    @NotifyUser(type = NotificationTypeEnum.COURSE, userId = "#studentUserId",
            title = "'课程公告'",
            content = "'《' + #courseName + '》发布公告：' + #announcementTitle + '。'")
    public void announcementPublished(Long studentUserId, String courseName, String announcementTitle) {
    }
}
