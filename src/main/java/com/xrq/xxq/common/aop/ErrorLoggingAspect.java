package com.xrq.xxq.common.aop;

import com.xrq.xxq.common.BusinessException;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.AfterThrowing;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.Set;

@Aspect
@Component
@Slf4j
public class ErrorLoggingAspect {

    private static final ScopedValue<Set<Integer>> LOGGED_EXCEPTIONS = ScopedValue.newInstance();

    @Pointcut("execution(* com.xrq.xxq.module..controller..*.*(..))")
    public void controllerMethods() {}

    @Pointcut("execution(* com.xrq.xxq.module..*.*(..))")
    public void moduleMethods() {}

    @Around("controllerMethods()")
    public Object establishScope(ProceedingJoinPoint pjp) throws Throwable {
        return ScopedValue.where(LOGGED_EXCEPTIONS, new HashSet<>()).call(pjp::proceed);
    }

    @AfterThrowing(pointcut = "moduleMethods()", throwing = "e")
    public void logException(JoinPoint joinPoint, Exception e) {
        if (LOGGED_EXCEPTIONS.isBound()) {
            Set<Integer> logged = LOGGED_EXCEPTIONS.get();
            if (!logged.add(System.identityHashCode(e))) {
                return;
            }
        }

        String className = joinPoint.getSignature().getDeclaringTypeName();
        String methodName = joinPoint.getSignature().getName();

        if (e instanceof BusinessException be) {
            log.warn("业务异常 — class={}, method={}, code={}, message={}",
                    className, methodName, be.getCode(), e.getMessage());
        } else if (e instanceof IllegalArgumentException) {
            log.warn("参数校验失败 — class={}, method={}, message={}",
                    className, methodName, e.getMessage());
        } else {
            log.error("服务器内部错误 — class={}, method={}, message={}",
                    className, methodName, e.getMessage(), e);
        }
    }
}
