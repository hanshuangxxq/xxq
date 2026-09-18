package com.xrq.xxq.module.file.controller;

import java.io.InputStream;
import java.nio.file.Path;

import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.xrq.xxq.common.Result;
import com.xrq.xxq.module.file.dto.ChunkSavedView;
import com.xrq.xxq.module.file.dto.DownloadRequest;
import com.xrq.xxq.module.file.dto.StoredFileRef;
import com.xrq.xxq.module.file.dto.UploadInitRequest;
import com.xrq.xxq.module.file.dto.UploadSessionView;
import com.xrq.xxq.module.file.dto.VerifyResult;
import com.xrq.xxq.module.file.entity.FileBizEnum;
import com.xrq.xxq.module.file.service.FileStorageService;
import com.xrq.xxq.util.auth.AuthFacade;
import com.xrq.xxq.util.auth.RequireLogin;
import com.xrq.xxq.util.file.ResumableFileResponse;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;

/**
 * 通用文件传输端点：分片上传四件套 + 整传 + 进度/校验 + 通用下载。
 * <p>
 * 所有端点只要求「已登录」：<b>不做归属判权</b>。文件归属由各业务表自行存字段记录，
 * 敏感文件一律走业务自己的下载端点（业务层判权后返回）。本端点覆盖的是
 * 「上传者自己能看回刚传的东西」与「半公开资源」这类通用场景。
 * <p>
 * <b>限流</b>：整组走 {@code /api/file/**} 独立桶（默认 600/分钟）—— 2GB ÷ 5MB = 410 个分片请求
 * 会直接撞穿全局限流的 120/分钟。
 * <p>
 * <b>分片请求体是裸二进制</b>（{@code application/octet-stream}），不经 multipart 解析，
 * 因此不受 {@code spring.servlet.multipart.max-file-size}（25MB）约束 —— 这正是大文件能到 2GB 的原因。
 * 分片摘要走 {@code X-Chunk-SHA256} 头，<b>必传</b>（缺失即 400）。
 */
@RestController
@RequestMapping("/api/file")
@RequiredArgsConstructor
@RequireLogin
public class FileController {

    private final FileStorageService fileStorage;
    private final AuthFacade authFacade;

    /**
     * 初始化或恢复上传会话（幂等，可随时重放）。
     * <p>{@code completedFile} 非空表示秒传命中或该会话已合并过 —— 直接跳到业务提交，不必传分片。
     */
    @PostMapping("/uploads")
    public Result<UploadSessionView> init(HttpServletRequest request, @RequestBody UploadInitRequest body) {
        FileBizEnum biz = FileBizEnum.requireCode(body.getBiz());
        return Result.ok(fileStorage.init(authFacade.currentUserId(request),
                authFacade.currentUserType(request), biz, body.getOriginalName(),
                body.getTotalSize(), body.getTotalChunks(), body.getSha256()));
    }

    /** 查询上传进度（只读，不创建）。用于刷新页面后拿回已传分片清单。 */
    @GetMapping("/uploads/{uploadId}")
    public Result<UploadSessionView> progress(HttpServletRequest request, @PathVariable String uploadId) {
        return Result.ok(fileStorage.progress(authFacade.currentUserId(request), uploadId));
    }

    /**
     * 上传一个分片：{@code index} 从 0 起，请求体为裸二进制，分片摘要放 {@code X-Chunk-SHA256} 头。
     * <p>同序号重传幂等覆盖；大小或摘要不符一律 400 且不留痕，客户端只需重传该片。
     */
    @PutMapping(value = "/uploads/{uploadId}/parts/{index}",
            consumes = MediaType.APPLICATION_OCTET_STREAM_VALUE)
    public Result<ChunkSavedView> saveChunk(HttpServletRequest request,
                                            @PathVariable String uploadId,
                                            @PathVariable int index,
                                            @RequestHeader(value = "X-Chunk-SHA256", required = false)
                                            String chunkSha256,
                                            InputStream body) {
        int received = fileStorage.saveChunk(authFacade.currentUserId(request), uploadId,
                index, body, chunkSha256);
        return Result.ok(new ChunkSavedView(index, received));
    }

    /**
     * 合并分片为成品（幂等，超时重试安全）。返回的 {@code storedPath} 随业务请求提交。
     * <p>校验失败返回 400 且<b>保留分片</b>；先调 {@code /verify} 定位坏片，重传后重试即可。
     */
    @PostMapping("/uploads/{uploadId}/complete")
    public Result<StoredFileRef> complete(HttpServletRequest request, @PathVariable String uploadId) {
        return Result.ok(fileStorage.complete(authFacade.currentUserId(request), uploadId));
    }

    /** 校验已收分片，返回缺失/损坏清单（把「整文件校验失败」从全量重传降级为精准重传）。 */
    @PostMapping("/uploads/{uploadId}/verify")
    public Result<VerifyResult> verify(HttpServletRequest request, @PathVariable String uploadId) {
        return Result.ok(fileStorage.verify(authFacade.currentUserId(request), uploadId));
    }

    /** 取消上传：删除分片目录与会话。 */
    @DeleteMapping("/uploads/{uploadId}")
    public Result<Void> abort(HttpServletRequest request, @PathVariable String uploadId) {
        fileStorage.abort(authFacade.currentUserId(request), uploadId);
        return Result.ok();
    }

    /**
     * 小文件整传（multipart）。受 {@code spring.servlet.multipart.max-file-size}（25MB）约束；
     * 超过该量级请走分片四件套。
     */
    @PostMapping(value = "/whole", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Result<StoredFileRef> storeWhole(@RequestParam String biz,
                                            @RequestParam("file") MultipartFile file) {
        return Result.ok(fileStorage.storeWhole(FileBizEnum.requireCode(biz), file));
    }

    /**
     * 通用下载（POST：路径不进 URL / 浏览器历史 / 网关日志）。
     * <p>{@code Range} 请求头在 POST 下同样生效（Spring 对 {@code Resource} 返回值的 Range 处理
     * 不检查 HTTP method），命中即返回 206 + {@code Content-Range}。
     */
    @PostMapping("/download")
    public ResponseEntity<Resource> download(@RequestBody DownloadRequest body) {
        Path file = fileStorage.resolve(body.getFilePath());
        return ResumableFileResponse.buildDownload(file, body.getOriginalName(),
                ResumableFileResponse.sha256FromStoredPath(body.getFilePath()));
    }
}
