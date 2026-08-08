package com.xrq.xxq.module.clazz.util;

import java.util.Arrays;
import java.util.List;

/**
 * 班级名称工具：解析逗号分隔的班级名 CSV 字符串。
 * <p>
 * 替代散落在 Score/Progress/TeachingEvaluation 服务中重复的 {@code splitClassNames} 私有方法。
 */
public final class ClassNameUtil {

    private ClassNameUtil() {
    }

    /** 拆分班级名 CSV：null/空白返回空列表；按逗号切分、strip、过滤空串、去重。 */
    public static List<String> splitClassNames(String csv) {
        if (csv == null || csv.isBlank()) {
            return List.of();
        }
        return Arrays.stream(csv.split(",")).map(String::strip).filter(s -> !s.isEmpty()).distinct().toList();
    }
}
