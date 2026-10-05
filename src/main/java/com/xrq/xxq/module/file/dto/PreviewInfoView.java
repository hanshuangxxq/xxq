package com.xrq.xxq.module.file.dto;

/**
 * 在线预览元信息：前端在拉取内容前调用，零内容传输地决定预览策略。
 * <p>典型用法：{@code size} 用于大文件提示（「48MB，将按需加载」）、{@code contentType}
 * 用于 viewer 选型（pdf.js / {@code <img>} / {@code <video>}）、{@code previewable=false}
 * 时直接回退到下载端点（docx/xlsx 等浏览器无法原生渲染的类型）。
 *
 * @param size        字节数
 * @param contentType 按存储文件扩展名推断的 Content-Type（内容寻址产物扩展名即真实类型）
 * @param previewable {@code GET /api/file/preview} 是否会放行该类型
 */
public record PreviewInfoView(long size, String contentType, boolean previewable) {
}
