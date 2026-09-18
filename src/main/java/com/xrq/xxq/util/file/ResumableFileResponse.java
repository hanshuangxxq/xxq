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
 * {@code FileStorageService#resolve} 拿到磁盘路径，再用本类构建响应直接返回。
 */
public final class ResumableFileResponse {

    private ResumableFileResponse() {
    }

    /**
     * 构建文件下载响应：Content-Disposition（RFC 5987 UTF-8 文件名）+ Content-Type 推断
     * + Accept-Ranges: bytes。originalName 为空时回退磁盘文件名。
     * <p>不带 ETag —— 仅用于 legacy 文件（旧扁平目录里的 UUID 命名文件，无内容摘要可作标识）。
     */
    public static ResponseEntity<Resource> buildDownload(Path file, String originalName) {
        return buildDownload(file, originalName, null);
    }

    /**
     * 构建支持 HTTP Range 断点续传的下载响应（带强 ETag）。
     * <p>
     * <b>为什么这里不需要服务端对 {@code If-Range} 求值</b>：本模块产物按内容寻址
     * （{@code objects/{biz}/{sha256}{ext}}），路径由内容摘要决定 ⇒ <b>同一路径的字节永不改变</b>。
     * 因此任何已下载的分片序列永远与新内容兼容，「续传期间文件被替换导致拼接损坏」这一风险
     * 在内容寻址下天然不存在。ETag 的价值退化为让客户端/下载器识别同一对象、命中缓存。
     * <p>legacy 文件无摘要，传 {@code null} 则不输出 ETag（此时客户端不应使用 {@code If-Range}）。
     *
     * @param file         已解析的磁盘文件（经 {@code FileStorageService#resolve} 或同等防护）
     * @param originalName 展示文件名（Content-Disposition），空白则回退磁盘文件名
     * @param etagOrNull   内容摘要（sha256）；非空时输出为强 ETag
     */
    public static ResponseEntity<Resource> buildDownload(Path file, String originalName,
                                                         String etagOrNull) {
        String filename = (originalName == null || originalName.isBlank())
                ? file.getFileName().toString() : originalName;
        String encoded = URLEncoder.encode(filename, StandardCharsets.UTF_8).replace("+", "%20");
        ResponseEntity.BodyBuilder builder = ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename*=UTF-8''" + encoded)
                .header(HttpHeaders.ACCEPT_RANGES, "bytes")
                .contentType(MediaType.parseMediaType(contentType(file)));
        if (etagOrNull != null && !etagOrNull.isBlank()) {
            builder.eTag("\"" + etagOrNull + "\"");
        }
        try {
            builder.lastModified(Files.getLastModifiedTime(file).toMillis());
        } catch (IOException ignored) {
            // 取不到修改时间就不输出 Last-Modified，不影响下载
        }
        return builder.body(new FileSystemResource(file));
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
