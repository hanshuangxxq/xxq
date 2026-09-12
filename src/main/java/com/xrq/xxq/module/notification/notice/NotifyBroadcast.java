package com.xrq.xxq.module.notification.notice;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import com.xrq.xxq.module.notification.entity.NotificationTargetEnum;
import com.xrq.xxq.module.notification.entity.NotificationTypeEnum;

/**
 * 广播通知注解：标注在通知场景方法上，由 {@link NotifyAspect} 拦截并调用
 * {@code NotificationService.broadcast} 完成落库（仅 1 行广播记录）与目标群体实时推送。
 * <p>
 * 发送语义与 {@link NotifyUser} 一致：事务提交后发送（回滚不发），无事务立即发送，
 * 构建与发送失败仅记 warn 日志。SpEL 书写约定同 {@link NotifyUser}。
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface NotifyBroadcast {

    /** 消息类型。 */
    NotificationTypeEnum type();

    /** 目标群体（STUDENT/ALL）。 */
    NotificationTargetEnum target();

    /** 发送者 user.id（审计用），SpEL 引用方法参数。 */
    String senderId();

    /** 标题，SpEL。 */
    String title();

    /** 内容，SpEL。 */
    String content();
}
