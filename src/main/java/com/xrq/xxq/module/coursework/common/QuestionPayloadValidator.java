package com.xrq.xxq.module.coursework.common;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import com.xrq.xxq.common.BusinessException;

import tools.jackson.databind.JsonNode;

/**
 * 题目载荷结构校验（题库录入 / 作业直接录入共用）：
 * 按题型校验选项与标准答案的形状，把坏题拦在入库前。
 */
public final class QuestionPayloadValidator {

    private QuestionPayloadValidator() {
    }

    /**
     * @param type    题型（null → 400）
     * @param options 选择题选项（仅选择题要求非空）
     * @param answer  标准答案 JSON（ESSAY 可空）
     */
    public static void validate(QuestionTypeEnum type, List<OptionItem> options, JsonNode answer) {
        if (type == null) {
            throw new BusinessException(400, "题型不能为空（SINGLE_CHOICE/MULTI_CHOICE/JUDGE/FILL_BLANK/ESSAY）");
        }
        switch (type) {
            case SINGLE_CHOICE -> {
                Set<String> keys = requireOptions(options);
                if (answer == null || !answer.isString()
                        || !keys.contains(answer.asString().trim().toUpperCase(Locale.ROOT))) {
                    throw new BusinessException(400, "单选题标准答案必须是选项 key 之一");
                }
            }
            case MULTI_CHOICE -> {
                Set<String> keys = requireOptions(options);
                if (answer == null || !answer.isArray() || answer.size() < 2) {
                    throw new BusinessException(400, "多选题标准答案至少 2 个选项");
                }
                for (JsonNode node : answer) {
                    if (!node.isString()
                            || !keys.contains(node.asString().trim().toUpperCase(Locale.ROOT))) {
                        throw new BusinessException(400, "多选题标准答案必须全部命中选项 key");
                    }
                }
            }
            case JUDGE -> {
                if (answer == null || !answer.isBoolean()) {
                    throw new BusinessException(400, "判断题标准答案必须是 true/false");
                }
            }
            case FILL_BLANK -> {
                if (answer == null || !answer.isObject() || !answer.path("blanks").isArray()
                        || answer.path("blanks").isEmpty()) {
                    throw new BusinessException(400, "填空题标准答案须为 {\"ordered\":bool,\"blanks\":[[\"可接受答案\",...],...]}");
                }
                if (answer.has("ordered") && !answer.path("ordered").isBoolean()) {
                    throw new BusinessException(400, "填空题 ordered 必须是布尔值");
                }
                for (JsonNode group : answer.path("blanks")) {
                    if (!group.isArray() || group.isEmpty()) {
                        throw new BusinessException(400, "填空题每空至少一个可接受答案");
                    }
                    for (JsonNode acceptable : group) {
                        if (!acceptable.isString() || acceptable.asString().isBlank()) {
                            throw new BusinessException(400, "填空题可接受答案不能为空字符串");
                        }
                    }
                }
            }
            case ESSAY -> {
                if (answer != null && !answer.isString()) {
                    throw new BusinessException(400, "简答题参考答案须为文本（可空）");
                }
            }
        }
    }

    /** 选择题选项：≥2 个、key 非空不重复；返回大写归一后的 key 集合。 */
    private static Set<String> requireOptions(List<OptionItem> options) {
        if (options == null || options.size() < 2) {
            throw new BusinessException(400, "选择题至少需要 2 个选项");
        }
        Set<String> keys = new HashSet<>();
        for (OptionItem item : options) {
            if (item == null || item.getKey() == null || item.getKey().isBlank()) {
                throw new BusinessException(400, "选项 key 不能为空");
            }
            if (!keys.add(item.getKey().trim().toUpperCase(Locale.ROOT))) {
                throw new BusinessException(400, "选项 key 重复: " + item.getKey());
            }
        }
        return keys;
    }
}
