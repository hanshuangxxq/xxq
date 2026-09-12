package com.xrq.xxq.module.notification.notice;

import java.lang.reflect.Method;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.core.DefaultParameterNameDiscoverer;
import org.springframework.core.ParameterNameDiscoverer;
import org.springframework.expression.EvaluationContext;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import com.xrq.xxq.module.notification.service.NotificationService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 通知注解切面：拦截 {@link NotifyUser}/{@link NotifyBroadcast} 标注的场景方法，
 * 以方法参数为变量求值注解中的 SpEL，再委托 {@link NotificationService} 发送。
 * <p>
 * 语义对齐原 {@code @TransactionalEventListener(AFTER_COMMIT, fallbackExecution = true)}：
 * 有活动事务时注册 afterCommit 同步（事务回滚则不发），无事务时立即发送。
 * SpEL 求值失败与发送失败均只记 warn 日志，绝不影响业务返回。
 */
@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
public class NotifyAspect {

    private final NotificationService notificationService;
    private final SpelExpressionParser spelParser = new SpelExpressionParser();
    private final ParameterNameDiscoverer nameDiscoverer = new DefaultParameterNameDiscoverer();

    @Around("@annotation(annotation)")
    public Object aroundNotifyUser(ProceedingJoinPoint pjp, NotifyUser annotation) throws Throwable {
        Object result = pjp.proceed();
        try {
            EvaluationContext ctx = buildContext(pjp);
            Long userId = spelParser.parseExpression(annotation.userId()).getValue(ctx, Long.class);
            String title = eval(annotation.title(), ctx);
            String content = eval(annotation.content(), ctx);
            sendAfterCommit(() -> notificationService.sendToUser(userId, annotation.type(), title, content));
        } catch (Exception e) {
            log.warn("通知构建失败: {}", pjp.getSignature(), e);
        }
        return result;
    }

    @Around("@annotation(annotation)")
    public Object aroundNotifyBroadcast(ProceedingJoinPoint pjp, NotifyBroadcast annotation) throws Throwable {
        Object result = pjp.proceed();
        try {
            EvaluationContext ctx = buildContext(pjp);
            Long senderId = spelParser.parseExpression(annotation.senderId()).getValue(ctx, Long.class);
            String title = eval(annotation.title(), ctx);
            String content = eval(annotation.content(), ctx);
            sendAfterCommit(() -> notificationService.broadcast(annotation.type(), annotation.target(),
                    title, content, senderId));
        } catch (Exception e) {
            log.warn("广播通知构建失败: {}", pjp.getSignature(), e);
        }
        return result;
    }

    /** 以场景方法参数名为变量名构建 SpEL 求值上下文（依赖 -parameters 编译参数）。 */
    private EvaluationContext buildContext(ProceedingJoinPoint pjp) {
        Method method = ((MethodSignature) pjp.getSignature()).getMethod();
        String[] names = nameDiscoverer.getParameterNames(method);
        Object[] args = pjp.getArgs();
        StandardEvaluationContext ctx = new StandardEvaluationContext();
        if (names != null) {
            for (int i = 0; i < names.length; i++) {
                ctx.setVariable(names[i], args[i]);
            }
        }
        return ctx;
    }

    private String eval(String spel, EvaluationContext ctx) {
        return spelParser.parseExpression(spel).getValue(ctx, String.class);
    }

    /** 有事务 -> afterCommit 发送（回滚不发）；无事务 -> 立即发送。发送异常仅 warn。 */
    private void sendAfterCommit(Runnable send) {
        Runnable guarded = () -> {
            try {
                send.run();
            } catch (Exception e) {
                log.warn("通知发送失败", e);
            }
        };
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    guarded.run();
                }
            });
        } else {
            guarded.run();
        }
    }
}
