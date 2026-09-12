package com.xrq.xxq.util.auth;

import lombok.Getter;

/**
 * 用户类型枚举，与 {@code user.user_type} 字段及 JWT claim 中的字符串一一对应。
 * <p>
 * 供 {@link RequireAuth} 注解与 {@code AuthInterceptor} 类型鉴权使用；
 * 存量字符串场景（实体字段、Service 层数据 scope 分支）经
 * {@link AuthFacade#USER_TYPE_STUDENT} 等常量引用本枚举 code，保持单一事实源。
 */
@Getter
public enum UserType {

    STUDENT("student"),
    TEACHER("teacher"),
    ACADEMIC_ADMIN("academic_admin"),
    DEPARTMENT("department");

    /** user.userType 字段与 JWT claim 中的存取值 */
    private final String code;

    UserType(String code) {
        this.code = code;
    }

    /** 按 code 匹配枚举，未命中返回 null。 */
    public static UserType fromCode(String code) {
        for (UserType type : values()) {
            if (type.code.equals(code)) {
                return type;
            }
        }
        return null;
    }
}
