package com.xrq.xxq.module.practice.common;

import java.nio.file.Path;

/**
 * 附件下载视图：已解析的磁盘文件 + 原始展示名。
 * <p>
 * 原先在 {@code GraduationProcessService} 与 {@code GraduationThesisService} 里各定义了一份
 * 逐字相同的嵌套 record，已合并到此处 —— 两处定义会让「下载响应怎么构建」这类改动需要同步改两边。
 */
public record FileView(Path path, String originalName) {
}
