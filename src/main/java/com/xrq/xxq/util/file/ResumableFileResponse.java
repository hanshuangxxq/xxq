package com.xrq.xxq.util.file;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

/**
 * 可续传下载响应构建工具（静态，对齐 EncryptUtils 先例）。
 * <p>
 * 返回 {@link ResponseEntity} 包装的 {@link FileSystemResource} 后，Spring 的
 * {@code ResourceHttpRequestConverter} 原生处理 {@code Range} 请求头并返回 206 部分内容，
 * 浏览器/下载器（wget -c、IDM）自动从中断位置续下；显式声明 {@code Accept-Ranges: bytes}
 * 让客户端得知可断点续传。调用方在自身接口层完成鉴权后，用业务表中的存储路径经
 * {@link ChunkedUploadStore#resolve} 拿到磁盘路径，再用本类构建响应直接返回。
 */
public final class ResumableFileResponse {

    private ResumableFileResponse() {
    }

    /**
     * 构建文件下载响应：Content-Disposition（RFC 5987 UTF-8 文件名）+ Content-Type 推断
     * + Accept-Ranges: bytes。originalName 为空时回退磁盘文件名。
     */
    public static ResponseEntity<Resource> buildDownload(Path file, String originalName) {
        String filename = (originalName == null || originalName.isBlank())
                ? file.getFileName().toString() : originalName;
        String encoded = URLEncoder.encode(filename, StandardCharsets.UTF_8).replace("+", "%20");
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename*=UTF-8''" + encoded)
                .header(HttpHeaders.ACCEPT_RANGES, "bytes")
                .contentType(MediaType.parseMediaType(contentType(file)))
                .body(new FileSystemResource(file));
    }

    /**
     * 推断 Content-Type：先按扩展名映射常见类型（行为跨环境确定，不依赖服务器注册表），
     * 未收录的扩展名再走 {@link Files#probeContentType}，最终兜底 application/octet-stream。
     */
    public static String contentType(Path file) {
        String name = file.getFileName().toString().toLowerCase(Locale.ROOT);
        if (name.endsWith(".pdf")) return "application/pdf";
        if (name.endsWith(".doc")) return "application/msword";
        if (name.endsWith(".docx")) return "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
        if (name.endsWith(".xls")) return "application/vnd.ms-excel";
        if (name.endsWith(".xlsx")) return "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
        if (name.endsWith(".zip")) return "application/zip";
        if (name.endsWith(".rar")) return "application/vnd.rar";
        if (name.endsWith(".7z")) return "application/x-7z-compressed";
        if (name.endsWith(".png")) return "image/png";
        if (name.endsWith(".jpg") || name.endsWith(".jpeg")) return "image/jpeg";
        if (name.endsWith(".gif")) return "image/gif";
        if (name.endsWith(".mp4")) return "video/mp4";
        if (name.endsWith(".mp3")) return "audio/mpeg";
        if (name.endsWith(".txt")) return "text/plain";
        if (name.endsWith(".md")) return "text/markdown";
        try {
            String probed = Files.probeContentType(file);
            if (probed != null) {
                return probed;
            }
        } catch (IOException ignored) {
            // 兜底 octet-stream
        }
        return "application/octet-stream";
    }
}
