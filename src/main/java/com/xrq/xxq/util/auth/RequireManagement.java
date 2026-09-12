package com.xrq.xxq.util.auth;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 管理侧可访问：教务管理员 + 院系管理员。
 * <p>
 * 等价于 {@code @RequireAuth({UserType.ACADEMIC_ADMIN, UserType.DEPARTMENT})}，对应「教务全校视角、院系本院视角」
 * 的数据 scope 组合（scope 裁剪由 {@code StudentScopeResolver} 在 Service 层完成，本注解只负责门禁）。
 * <p>
 * 组合注解：{@code AuthInterceptor} 经元注解查找 {@link RequireAuth}，行为与直接标注 {@link RequireAuth} 一致。
 * 可标在 Controller 方法或类上（方法级覆盖类级，两者皆无默认拒绝 403）。
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
@RequireAuth({UserType.ACADEMIC_ADMIN, UserType.DEPARTMENT})
public @interface RequireManagement {
}
