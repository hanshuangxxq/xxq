package com.xrq.xxq.util.auth;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 仅学生可访问。等价于 {@code @RequireAuth(UserType.STUDENT)}。
 * <p>
 * 组合注解：{@code AuthInterceptor} 经元注解查找 {@link RequireAuth}，行为与直接标注 {@link RequireAuth} 一致。
 * 可标在 Controller 方法或类上（方法级覆盖类级，两者皆无默认拒绝 403）。
 * <p>
 * 命名注意：项目内 {@code Admin}（系统管理员表）与 {@code AcademicAdmin}（教务管理员）是不同概念，
 * 本注解对应的是 {@link UserType#STUDENT}。
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
@RequireAuth(UserType.STUDENT)
public @interface RequireStudent {
}
