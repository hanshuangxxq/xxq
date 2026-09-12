package com.xrq.xxq.util.auth;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 仅教师可访问。等价于 {@code @RequireAuth(UserType.TEACHER)}。
 * <p>
 * 组合注解：{@code AuthInterceptor} 经元注解查找 {@link RequireAuth}，行为与直接标注 {@link RequireAuth} 一致。
 * 可标在 Controller 方法或类上（方法级覆盖类级，两者皆无默认拒绝 403）。
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
@RequireAuth(UserType.TEACHER)
public @interface RequireTeacher {
}
