package com.xrq.xxq.util;

import java.math.BigDecimal;
import java.util.Collection;

import com.xrq.xxq.common.BusinessException;

/**
 * 参数校验工具：集中处理 Controller/Service 入参的非空、非空白、非空集合、正数、区间校验。
 * <p>
 * 替代散落各处的 {@code if (x == null) throw new BusinessException(400, "xx不能为空")} 样板。
 * 校验失败统一抛 {@link BusinessException}(400, ...)，由全局异常处理转 400 响应。
 * <p>
 * 纯逻辑工具，无 Spring 依赖，全部为静态方法（对齐 {@link EncryptUtils} 风格）。
 */
public final class ParamValidator {

    private ParamValidator() {
    }

    /** 非空校验：value 为 null 抛 400「{name}不能为空」。 */
    public static void requireNonNull(Object value, String name) {
        if (value == null) {
            throw new BusinessException(400, name + "不能为空");
        }
    }

    /** 非空白校验：value 为 null 或空白串抛 400「{name}不能为空」。 */
    public static void requireNonBlank(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new BusinessException(400, name + "不能为空");
        }
    }

    /** 非空集合校验：coll 为 null 或空抛 400「{name}不能为空」。 */
    public static void requireNonEmpty(Collection<?> coll, String name) {
        if (coll == null || coll.isEmpty()) {
            throw new BusinessException(400, name + "不能为空");
        }
    }

    /** 正数校验：value <= 0 抛 400「{name}必须大于0」。 */
    public static void requirePositive(long value, String name) {
        if (value <= 0) {
            throw new BusinessException(400, name + "必须大于0");
        }
    }

    /** 区间校验：value 为 null 或超出 [min,max] 抛 400「{name}必须在 {min}-{max} 之间」。 */
    public static void requireInRange(BigDecimal value, double min, double max, String name) {
        if (value == null) {
            throw new BusinessException(400, name + "不能为空");
        }
        double v = value.doubleValue();
        if (v < min || v > max) {
            throw new BusinessException(400, name + "必须在 " + fmt(min) + "-" + fmt(max) + " 之间");
        }
    }

    /** 整数 d 显示为整数，非整数显示原值。 */
    private static String fmt(double d) {
        return d == Math.floor(d) && !Double.isInfinite(d) ? Long.toString((long) d) : Double.toString(d);
    }
}
