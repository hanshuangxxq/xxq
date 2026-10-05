package com.xrq.xxq.util.file;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Set;

import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import com.xrq.xxq.common.BusinessException;

/**
 * 可续传下载响应构建工具（静态，对齐 EncryptUtils 先例）。
 * <p>
 * 返回 {@link ResponseEntity} 包装的 {@link Resource} 后，Spring 的
 * {@code ResourceHttpRequestConverter} 原生处理 {@code Range} 请求头并返回 206 部分内容，
 * 浏览器/下载器（wget -c、IDM）自动从中断位置续下；显式声明 {@code Accept-Ranges: bytes}
 * 让客户端得知可断点续传。调用方在自身接口层完成鉴权后，用业务表中的存储路径经
 * {@code FileStorageService#resolve} 拿到磁盘路径，再用本类构建响应直接返回。
 */
public final class ResumableFileResponse {

    /**
     * 可内联预览的 Content-Type 白名单：{@code FileBizEnum} 各扩展名白名单中
     * 「浏览器能原生渲染」的子集（pdf.js / {@code <img>} / {@code <video>}）。
     * <p>docx/xlsx/pptx 等 Office 格式浏览器无法原生渲染，不在此列 —— 前端拿
     * {@code previewable=false} 后回退下载。html/svg/js 永远不会出现
     * （上传侧 {@code FileBizEnum} 扩展名白名单已拦截），故内联展示无 XSS 面。
     */
    private static final Set<String> PREVIEWABLE_TYPES = Set.of(
            "application/pdf", "image/jpeg", "image/png", "video/mp4");

    private ResumableFileResponse() {
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
     * 构建 byte[] 内容的可续传下载响应（小文件导出场景：xlsx/csv/pdf 报表）。
     * <p>{@link ByteArrayResource} 与 {@link FileSystemResource} 一样走
     * {@code ResourceHttpRequestConverter} 的 Range 处理 → 命中即 206；
     * ETag 取内容 sha256（小文件计算开销可忽略），内容与响应头一一对应。
     */
    public static ResponseEntity<Resource> buildDownload(byte[] data, String fileName) {
        String name = (fileName == null || fileName.isBlank()) ? "download" : fileName;
        String encoded = URLEncoder.encode(name, StandardCharsets.UTF_8).replace("+", "%20");
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename*=UTF-8''" + encoded)
                .header(HttpHeaders.ACCEPT_RANGES, "bytes")
                .contentType(MediaType.parseMediaType(contentTypeFromName(name)))
                .eTag("\"" + sha256Hex(data) + "\"")
                .body(new ByteArrayResource(data));
    }

    /**
     * 构建在线预览响应（{@code Content-Disposition: inline} + Range 懒加载 + 强缓存）。
     * <p>与 {@link #buildDownload} 的差异：</p>
     * <ul>
     *   <li><b>inline 而非 attachment</b>：浏览器/pdf.js 直接渲染而非触发下载。</li>
     *   <li><b>类型门禁</b>：仅放行 {@link #PREVIEWABLE_TYPES}，其余 415 —— 内联展示
     *       docx/zip 没有意义（浏览器只会转下载），反而放大误用面。</li>
     *   <li><b>{@code X-Content-Type-Options: nosniff}</b>：扩展名门禁只挡「文件名」，
     *       挡不住「把 HTML 改名成 .pdf 上传」；nosniff 强制浏览器按声明类型渲染，
     *       杜绝嗅探成 text/html 后的同源 XSS。</li>
     *   <li><b>缓存</b>：内容寻址产物（{@code etagOrNull} 非空）路径即摘要、字节永不改变，
     *       输出 {@code Cache-Control: private, max-age=1y, immutable} —— 二次打开零传输；
     *       无摘要（legacy）退化为 {@code no-cache}（每次回源经 Last-Modified 协商，304 兜底）。</li>
     * </ul>
     * Range 处理与下载相同，由 Spring 对 {@code Resource} 返回值原生完成（命中即 206），
     * pdf.js / {@code <video>} 的按需分段加载由此实现 —— 这就是「懒加载」的服务端侧。
     *
     * @param file         已解析的磁盘文件（经 {@code FileStorageService#resolve} 或同等防护）
     * @param originalName 展示文件名；其扩展名优先用于类型判定（兼容 legacy 无扩展名 UUID 文件），
     *                     判定不出时回退磁盘文件名（内容寻址产物的扩展名即真实类型）
     * @param etagOrNull   内容摘要（sha256）；非空时输出强 ETag + immutable 缓存
     * @throws BusinessException 415 —— 类型不可预览，前端应回退到下载
     */
    public static ResponseEntity<Resource> buildPreview(Path file, String originalName,
                                                        String etagOrNull) {
        String filename = (originalName == null || originalName.isBlank())
                ? file.getFileName().toString() : originalName;
        String contentType = previewContentType(filename);
        if (contentType == null) {
            contentType = previewContentType(file.getFileName().toString());
        }
        if (contentType == null) {
            throw new BusinessException(415, "该文件类型不支持在线预览，请下载后查看");
        }
        String encoded = URLEncoder.encode(filename, StandardCharsets.UTF_8).replace("+", "%20");
        ResponseEntity.BodyBuilder builder = ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename*=UTF-8''" + encoded)
                .header(HttpHeaders.ACCEPT_RANGES, "bytes")
                .header("X-Content-Type-Options", "nosniff")
                .contentType(MediaType.parseMediaType(contentType));
        if (etagOrNull != null && !etagOrNull.isBlank()) {
            builder.eTag("\"" + etagOrNull + "\"")
                    .header(HttpHeaders.CACHE_CONTROL, "private, max-age=31536000, immutable");
        } else {
            builder.header(HttpHeaders.CACHE_CONTROL, "private, no-cache");
        }
        try {
            builder.lastModified(Files.getLastModifiedTime(file).toMillis());
        } catch (IOException ignored) {
            // 取不到修改时间就不输出 Last-Modified，不影响预览
        }
        return builder.body(new FileSystemResource(file));
    }

    /**
     * 预览类型门禁：按文件名扩展名映射，命中 {@link #PREVIEWABLE_TYPES} 返回对应
     * Content-Type，否则返回 {@code null}（调用方据此 415 或向客户端报告 {@code previewable=false}）。
     */
    public static String previewContentType(String fileName) {
        String mapped = contentTypeFromName(fileName);
        return PREVIEWABLE_TYPES.contains(mapped) ? mapped : null;
    }

    /**
     * 从存储相对路径（{@code objects/{biz}/{sha256}{ext}}）提取内容摘要，用作下载响应的强 ETag。
     *
     * @return 64 位十六进制摘要；路径非内容寻址形态（如 legacy 的 UUID 文件名）返回 {@code null}
     */
    public static String sha256FromStoredPath(String storedPath) {
        if (storedPath == null || !storedPath.startsWith("objects/")) {
            return null;
        }
        String[] segments = storedPath.split("/");
        return segments.length == 3 ? sha256FromFileName(segments[2]) : null;
    }

    /**
     * 从磁盘文件名（{@code {sha256}{ext}}）提取内容摘要。
     * <p>legacy 文件是 32 位 UUID 名，长度不符自然返回 {@code null} —— 不会误把 UUID 当摘要输出。
     */
    public static String sha256FromFileName(String fileName) {
        if (fileName == null) {
            return null;
        }
        int dot = fileName.indexOf('.');
        String sha = dot < 0 ? fileName : fileName.substring(0, dot);
        return sha.length() == 64 && sha.chars().allMatch(c -> Character.digit(c, 16) >= 0)
                ? sha.toLowerCase(Locale.ROOT) : null;
    }

    /**
     * 推断 Content-Type：先按扩展名映射常见类型（行为跨环境确定，不依赖服务器注册表），
     * 未收录的扩展名再走 {@link Files#probeContentType}，最终兜底 application/octet-stream。
     */
    public static String contentType(Path file) {
        String mapped = contentTypeFromName(file.getFileName().toString());
        if (!"application/octet-stream".equals(mapped)) {
            return mapped;
        }
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

    /**
     * 按文件名扩展名映射常见 Content-Type（跨环境确定，不依赖服务器注册表）；
     * 未收录的扩展名返回 application/octet-stream。
     */
    public static String contentTypeFromName(String fileName) {
        String name = fileName == null ? "" : fileName.toLowerCase(Locale.ROOT);
        if (name.endsWith(".pdf")) return "application/pdf";
        if (name.endsWith(".doc")) return "application/msword";
        if (name.endsWith(".docx")) return "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
        if (name.endsWith(".xls")) return "application/vnd.ms-excel";
        if (name.endsWith(".xlsx")) return "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
        if (name.endsWith(".csv")) return "text/csv";
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
        return "application/octet-stream";
    }

    /** 内容 sha256 的十六进制串，用作 byte[] 响应的强 ETag。 */
    private static String sha256Hex(byte[] data) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(data));
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 是 JCA 必备算法，不可能缺失
            throw new IllegalStateException(e);
        }
    }
}
