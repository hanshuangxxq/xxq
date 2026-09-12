package com.xrq.xxq.util.auth;

import com.xrq.xxq.common.BusinessException;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 统一鉴权门面：集中处理 Controller 层的「当前用户上下文访问」。
 * <p>
 * 权限校验已迁移至 {@link RequireAuth} 注解 + {@code AuthInterceptor} 统一执行（默认拒绝），
 * 新代码禁止再调用本类的 {@code requireXxx} 方法（已标记 {@link Deprecated}，稳定后删除）。
 * <p>
 * 上下文读取仍走本类（{@code currentUserId}/{@code currentUserType} 等），
 * 禁止直接 {@code request.getAttribute("userId"/"userType"/"role"/"tokenId")}。
 * 比较 {@code userType} 字符串时使用 {@code USER_TYPE_*} 常量（引用 {@link UserType} 枚举 code），
 * 不要硬编码 "student"/"academic_admin" 等字面量。
 * <p>
 * 当前用户上下文由 {@link com.xrq.xxq.config.AuthInterceptor} 解析 Bearer JWT 后注入到
 * request attribute 中，本类仅负责读取。
 */
@Component
@RequiredArgsConstructor
public class AuthFacade {

    public static final String ATTR_USER_ID = "userId";
    public static final String ATTR_USER_TYPE = "userType";
    public static final String ATTR_ROLE = "role";
    public static final String ATTR_TOKEN_ID = "tokenId";
    /** 登录成功时登记的客户端 IP 显示串（公网IP|内网IP），由 AuthInterceptor 从会话注入 */
    public static final String ATTR_LOGIN_IP = "loginIp";

    public static final String USER_TYPE_STUDENT = UserType.STUDENT.getCode();
    public static final String USER_TYPE_TEACHER = UserType.TEACHER.getCode();
    public static final String USER_TYPE_ACADEMIC_ADMIN = UserType.ACADEMIC_ADMIN.getCode();
    public static final String USER_TYPE_DEPARTMENT = UserType.DEPARTMENT.getCode();

    private static final String FORBIDDEN_MSG = "权限不足";

    private final LoginSessionStore sessionStore;

    // ---- 当前用户上下文 ----

    public Long currentUserId(HttpServletRequest request) {
        return (Long) request.getAttribute(ATTR_USER_ID);
    }

    public String currentUserType(HttpServletRequest request) {
        return (String) request.getAttribute(ATTR_USER_TYPE);
    }

    public String currentRole(HttpServletRequest request) {
        return (String) request.getAttribute(ATTR_ROLE);
    }

    public String currentTokenId(HttpServletRequest request) {
        return (String) request.getAttribute(ATTR_TOKEN_ID);
    }

    /**
     * 当前请求登录成功时登记的客户端 IP 显示串（公网IP|内网IP），供操作日志展示。
     * 会话无登记（内网 IP 获取失败 / 重建会话 / 存量旧会话）时返回 null，调用方回退实时解析。
     */
    public String currentLoginIp(HttpServletRequest request) {
        return (String) request.getAttribute(ATTR_LOGIN_IP);
    }

    /** 从 Redis 取当前会话的完整快照；若 token 已注销返回 null。 */
    public UserSession currentSession(HttpServletRequest request) {
        String tokenId = currentTokenId(request);
        return tokenId == null ? null : sessionStore.get(tokenId);
    }

    // ---- 权限校验（已废弃：由 @RequireAuth 注解 + AuthInterceptor 统一执行，稳定后删除） ----

    /**
     * @deprecated 改用 {@link RequireAuth} 注解声明在 Controller 方法/类上。
     */
    @Deprecated
    public void requireUserType(HttpServletRequest request, String expectedType) {
        if (!expectedType.equals(currentUserType(request))) {
            throw new BusinessException(403, FORBIDDEN_MSG);
        }
    }

    /**
     * @deprecated 改用 {@link RequireAuth} 注解声明多类型，如 {@code @RequireAuth({UserType.TEACHER, UserType.ACADEMIC_ADMIN})}。
     */
    @Deprecated
    public void requireUserTypes(HttpServletRequest request, String... allowedTypes) {
        String userType = currentUserType(request);
        for (String allowed : allowedTypes) {
            if (allowed.equals(userType)) {
                return;
            }
        }
        throw new BusinessException(403, FORBIDDEN_MSG);
    }

    /**
     * @deprecated 改用 {@code @RequireAuth(UserType.ACADEMIC_ADMIN)}。
     */
    @Deprecated
    public void requireAcademicAdmin(HttpServletRequest request) {
        requireUserType(request, USER_TYPE_ACADEMIC_ADMIN);
    }

    /**
     * @deprecated 改用 {@code @RequireAuth(UserType.STUDENT)}。
     */
    @Deprecated
    public void requireStudent(HttpServletRequest request) {
        requireUserType(request, USER_TYPE_STUDENT);
    }

    /**
     * @deprecated 改用 {@code @RequireAuth(UserType.TEACHER)}。
     */
    @Deprecated
    public void requireTeacher(HttpServletRequest request) {
        requireUserType(request, USER_TYPE_TEACHER);
    }

    /**
     * @deprecated 改用 {@code @RequireAuth(UserType.DEPARTMENT)}。
     */
    @Deprecated
    public void requireDepartment(HttpServletRequest request) {
        requireUserType(request, USER_TYPE_DEPARTMENT);
    }

    // ---- 便捷组合：校验 + 返回 userId（已废弃，同上） ----

    /**
     * @deprecated 改用 {@code @RequireAuth(UserType.STUDENT)} + {@link #currentUserId(HttpServletRequest)}。
     */
    @Deprecated
    public Long requireStudentUserId(HttpServletRequest request) {
        requireStudent(request);
        return currentUserId(request);
    }

    /**
     * @deprecated 改用 {@code @RequireAuth(UserType.ACADEMIC_ADMIN)} + {@link #currentUserId(HttpServletRequest)}。
     */
    @Deprecated
    public Long requireAcademicAdminUserId(HttpServletRequest request) {
        requireAcademicAdmin(request);
        return currentUserId(request);
    }

    /**
     * @deprecated 改用 {@code @RequireAuth(UserType.DEPARTMENT)} + {@link #currentUserId(HttpServletRequest)}。
     */
    @Deprecated
    public Long requireDepartmentUserId(HttpServletRequest request) {
        requireDepartment(request);
        return currentUserId(request);
    }

    // ---- 多类型校验：校验 + 返回 userId / 上下文（已废弃，同上） ----

    /**
     * 多类型校验上下文（userId + userType），供需要同时透传两者的调用方使用。
     *
     * @deprecated 改用 {@link RequireAuth} 注解 + {@link #currentUserId}/{@link #currentUserType}。
     */
    @Deprecated
    public record AuthContext(Long userId, String userType) {
    }

    /**
     * @deprecated 改用 {@link RequireAuth} 注解 + {@link #currentUserId(HttpServletRequest)}。
     */
    @Deprecated
    public Long requireUserTypesUserId(HttpServletRequest request, String... allowedTypes) {
        requireUserTypes(request, allowedTypes);
        return currentUserId(request);
    }

    /**
     * @deprecated 改用 {@link RequireAuth} 注解 + {@link #currentUserId}/{@link #currentUserType}。
     */
    @Deprecated
    public AuthContext requireUserTypesContext(HttpServletRequest request, String... allowedTypes) {
        requireUserTypes(request, allowedTypes);
        return new AuthContext(currentUserId(request), currentUserType(request));
    }
}
