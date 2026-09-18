package com.xrq.xxq.module.file.dto;

import org.jspecify.annotations.NonNull;

import lombok.Data;

/**
 * 通用下载请求。
 * <p>用 POST 而非 GET 承载路径，是为了让存储路径不进 URL、浏览器历史与网关访问日志。
 * 代价见对接文档：{@code wget -c} / IDM / 浏览器 {@code <a download>} 的<b>自动</b>断点续传
 * 依赖 GET，POST 下载要续传须前端自研分块循环（自己发 {@code Range} 头）。
 */
@Data
public class DownloadRequest {

    /** 存储相对路径，形如 {@code objects/{biz}/{sha256}{ext}}。 */
    @NonNull
    private String filePath;

    /** 展示文件名（{@code Content-Disposition}）；留空则回退磁盘文件名。 */
    private String originalName;
}
