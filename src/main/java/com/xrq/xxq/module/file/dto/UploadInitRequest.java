package com.xrq.xxq.module.file.dto;

import org.jspecify.annotations.NonNull;

import lombok.Data;

/**
 * 初始化上传会话请求。
 * <p>客户端须在上传前算好整文件 SHA-256（浏览器用 {@code crypto.subtle.digest}，原生无依赖），
 * 它同时是秒传标识与最终完整性校验基线。
 */
@Data
public class UploadInitRequest {

    /** 业务目录 code，取值必须是 {@code FileBizEnum} 之一 —— 客户端不能自造目录。 */
    @NonNull
    private String biz;

    /** 原始文件名（含扩展名）；扩展名须在业务目录的白名单内。 */
    @NonNull
    private String originalName;

    /** 文件总字节数。 */
    private long totalSize;

    /** 分片总数；服务端按 {@code ceil(totalSize/totalChunks)} 推导实际分片大小并随响应返回。 */
    private int totalChunks;

    /** 整文件 SHA-256（64 位十六进制，小写敏感不严格）。 */
    @NonNull
    private String sha256;
}
