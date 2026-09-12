package com.xrq.xxq.module.notification.notice;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import com.xrq.xxq.module.notification.entity.NotificationTypeEnum;

/**
 * 单点通知注解：标注在通知场景方法上，由 {@link NotifyAspect} 拦截并调用
 * {@code NotificationService.sendToUser} 完成落库与实时推送。
 * <p>
 * 发送语义：标注方法正常返回后，若当前存在活动事务则注册 afterCommit 同步
 * （事务回滚则不发），否则立即发送；通知构建与发送失败仅记 warn 日志，不影响业务返回。
 * <p>
 * {@link #userId}/{@link #title}/{@link #content} 均为 SpEL 表达式，以场景方法的
 * 参数名为变量（如 {@code #studentUserId}）。书写约定：只使用字面量（须带单引号）、
 * 参数引用、字符串 {@code +} 拼接、三元表达式四要素；文案一律写死在注解上，业务侧只传数据。
 * <p>
 * 正确性由 {@code NotifyScenesCompletenessTest} 在构建期保证：注解缺失、SpEL 语法错误、
 * 引用了不存在参数名的场景方法会导致测试失败。
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface NotifyUser {

    /** 消息类型。 */
    NotificationTypeEnum type();

    /** 接收者 user.id，SpEL 引用方法参数，如 {@code "#studentUserId"}。 */
    String userId();

    /** 标题，SpEL。纯字面量须带单引号：{@code "'成绩通知'"}；引用参数：{@code "#levelDescription"}。 */
    String title();

    /** 内容，SpEL，支持拼接与三元。 */
    String content();
}
