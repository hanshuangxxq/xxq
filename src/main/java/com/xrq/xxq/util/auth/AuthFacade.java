package com.xrq.xxq.util.auth;

import com.xrq.xxq.common.BusinessException;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 统一鉴权门面：集中处理 Controller 层的「当前用户上下文访问」与「权限校验」。
 * <p>
 * 所有 Controller 中的鉴权操作一律调用本类，禁止再写 {@code request.getAttribute("userId"/"userType"/"role"/"tokenId")}
 * 或重复实现 {@code checkXxx} 私有方法。新增权限校验时优先复用 {@code requireXxx}，
 * 遇到特殊组合用 {@link #requireUserTypes(HttpServletRequest, String...)}。
 * <p>
 * 权限校验失败统一抛 {@link BusinessException}(403, "权限不足")，不暴露具体角色文案。
 * <p>
 * 当前用户上下文由 {@link com.xrq.xxq.config.AuthInterceptor} 解析 Bearer JWT 后注入到
 * request attribute 中，本类仅负责读取与校验。
 */
@Component
@RequiredArgsConstructor
public class AuthFacade {

    public static final String ATTR_USER_ID = "userId";
    public static final String ATTR_USER_TYPE = "userType";
    public static final String ATTR_ROLE = "role";
    public static final String ATTR_TOKEN_ID = "tokenId";

    public static final String USER_TYPE_STUDENT = "student";
    public static final String USER_TYPE_TEACHER = "teacher";
    public static final String USER_TYPE_ACADEMIC_ADMIN = "academic_admin";
    public static final String USER_TYPE_DEPARTMENT = "department";

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

    /** 从 Redis 取当前会话的完整快照；若 token 已注销返回 null。 */
    public UserSession currentSession(HttpServletRequest request) {
        String tokenId = currentTokenId(request);
        return tokenId == null ? null : sessionStore.get(tokenId);
    }

    // ---- 权限校验 ----

    public void requireUserType(HttpServletRequest request, String expectedType) {
        if (!expectedType.equals(currentUserType(request))) {
            throw new BusinessException(403, FORBIDDEN_MSG);
        }
    }

    public void requireUserTypes(HttpServletRequest request, String... allowedTypes) {
        String userType = currentUserType(request);
        for (String allowed : allowedTypes) {
            if (allowed.equals(userType)) {
                return;
            }
        }
        throw new BusinessException(403, FORBIDDEN_MSG);
    }

    public void requireAcademicAdmin(HttpServletRequest request) {
        requireUserType(request, USER_TYPE_ACADEMIC_ADMIN);
    }

    public void requireStudent(HttpServletRequest request) {
        requireUserType(request, USER_TYPE_STUDENT);
    }

    public void requireTeacher(HttpServletRequest request) {
        requireUserType(request, USER_TYPE_TEACHER);
    }

    public void requireDepartment(HttpServletRequest request) {
        requireUserType(request, USER_TYPE_DEPARTMENT);
    }

    // ---- 便捷组合：校验 + 返回 userId ----

    public Long requireStudentUserId(HttpServletRequest request) {
        requireStudent(request);
        return currentUserId(request);
    }

    public Long requireAcademicAdminUserId(HttpServletRequest request) {
        requireAcademicAdmin(request);
        return currentUserId(request);
    }

    public Long requireDepartmentUserId(HttpServletRequest request) {
        requireDepartment(request);
        return currentUserId(request);
    }

    // ---- 多类型校验：校验 + 返回 userId / 上下文 ----

    /** 多类型校验上下文（userId + userType），供需要同时透传两者的调用方使用。 */
    public record AuthContext(Long userId, String userType) {
    }

    /** 多类型校验 + 返回 userId（仅需 userId 的调用方）。 */
    public Long requireUserTypesUserId(HttpServletRequest request, String... allowedTypes) {
        requireUserTypes(request, allowedTypes);
        return currentUserId(request);
    }

    /** 多类型校验 + 返回上下文（需同时取 userId 与 userType 的调用方）。 */
    public AuthContext requireUserTypesContext(HttpServletRequest request, String... allowedTypes) {
        requireUserTypes(request, allowedTypes);
        return new AuthContext(currentUserId(request), currentUserType(request));
    }
}
