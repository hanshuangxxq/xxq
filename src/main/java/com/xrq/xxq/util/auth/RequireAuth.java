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
 * 用法（<b>优先</b>用同包下的组合注解，可读性更好；仅长尾多类型组合才直接用本注解显式枚举）：
 * <ul>
 *   <li>{@link RequireLogin} —— 任意已登录用户，等价 {@code @RequireAuth()}；</li>
 *   <li>{@link RequireStudent}/{@link RequireTeacher}/{@link RequireAcademicAdmin}/{@link RequireDepartment} —— 单类型；</li>
 *   <li>{@link RequireManagement} —— 教务 + 院系（管理侧），等价 {@code @RequireAuth({UserType.ACADEMIC_ADMIN, UserType.DEPARTMENT})}；</li>
 *   <li>{@code @RequireAuth({UserType.TEACHER, UserType.ACADEMIC_ADMIN})} —— 多类型任一（长尾组合保留显式写法）。</li>
 * </ul>
 * 组合注解之所以生效：{@code AuthInterceptor} 用 {@code HandlerMethod#getMethodAnnotation} 与
 * {@code AnnotationUtils#findAnnotation} 查找本注解，两者均经 Spring {@code MergedAnnotations} 遍历元注解
 * （与 {@code @GetMapping} 之于 {@code @RequestMapping} 同一机制），故无需任何拦截器改动。
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
