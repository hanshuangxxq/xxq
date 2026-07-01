package com.xrq.xxq.common.aop;

import com.xrq.xxq.common.BusinessException;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.AfterThrowing;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.springframework.stereotype.Component;

@Aspect
@Component
@Slf4j
public class ErrorLoggingAspect {

    @Pointcut("execution(* com.xrq.xxq.module..*.*(..))")
    public void moduleMethods() {}

    @AfterThrowing(pointcut = "moduleMethods()", throwing = "e")
    public void logException(JoinPoint joinPoint, Exception e) {
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
