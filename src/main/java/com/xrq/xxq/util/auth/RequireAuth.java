package com.xrq.xxq.util.auth;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 接口类型鉴权注解，由 {@code AuthInterceptor} 在登录校验通过后统一执行。
 * <p>
 * 生效规则：方法级注解优先，类级兜底（覆盖语义，非并集）；两者皆无<b>默认拒绝</b>（403）。
 * <p>
 * 用法：
 * <ul>
 *   <li>{@code @RequireAuth} —— 任意已登录用户；</li>
 *   <li>{@code @RequireAuth(UserType.ACADEMIC_ADMIN)} —— 单类型；</li>
 *   <li>{@code @RequireAuth({UserType.TEACHER, UserType.ACADEMIC_ADMIN})} —— 多类型任一。</li>
 * </ul>
 * {@code /api/login}、{@code /api/login/refresh} 已被 {@code WebMvcConfig} 排除在拦截器外，无需标注。
 * <p>
 * 完整性由 {@code RequireAuthCompletenessTest} 在构建期保证：/api/** 下未标注的端点会导致测试失败。
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface RequireAuth {

    /** 允许访问的用户类型；空数组 = 任意已登录用户。 */
    UserType[] value() default {};
}
