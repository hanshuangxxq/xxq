package com.xrq.xxq.module.file.service;

import java.io.InputStream;
import java.nio.file.Path;

import org.springframework.web.multipart.MultipartFile;

import com.xrq.xxq.module.file.dto.StoredFileRef;
import com.xrq.xxq.module.file.dto.UploadSessionView;
import com.xrq.xxq.module.file.dto.VerifyResult;
import com.xrq.xxq.module.file.entity.FileBizEnum;

/**
 * 文件存储层：分片上传（断点续传/秒传）、整传、产物解析。
 * <p>
 * <b>存储模型</b>（{@code file.storage-path} 下）：
 * <pre>
 * chunks/{biz}/{ownerId}/{sha256}/meta.json              会话不可变参数（磁盘持久镜像）
 * chunks/{biz}/{ownerId}/{sha256}/part-{N}               已提交分片（tmp -&gt; 原子 rename 即提交点）
 * objects/{biz}/{sha256}{ext}                            成品，内容寻址
 * objects/{biz}/.staging/{uuid}{ext}                     整传落盘中转（事务提交后才 rename）
 * </pre>
 *
 * <b>为什么分片目录带 ownerId、而产物目录不带</b>：产物按内容去重（同一份文件被多个业务/用户引用只存一份）；
 * 分片目录按「内容 + 上传者」隔离，避免两个学生同时传同一份学院模板时互相踩 {@code part-N}，
 * 也避免任一人 {@code abort} 掉他人的上传进度。分片目录不含会话参数，因此 Redis 丢失后
 * 可由 {@code (biz, ownerId, sha256)} 直接重新定位，磁盘上的 {@code meta.json} 是权威参数。
 * <p>
 * <b>完整性校验四级</b>：① 分片级 —— {@code chunkSha256} 必传，边写边算，不符即拒（坏片可定位，
 * 只重传该片）；② 流式限长 —— 读满「期望 + 1 字节」立即中止，绝不先把磁盘写满再校验；
 * ③ 整文件级 —— {@code complete} 流式合并时重算 SHA-256 与总大小；④ 产物自证 —— 文件名即内容摘要。
 * <p>
 * <b>并发</b>：{@code complete} 不使用分布式锁，而是构造性幂等 —— 产物内容寻址 + 合并前二次检查产物存在 +
 * 读片 IOException 时复查产物。理由：双重 complete 在客户端超时重试场景下很常见，返回 409 不如幂等友好；
 * 且不引入 Redis 依赖能让本层脱离 Spring 做零依赖单测（本项目无 Mockito / embedded-redis）。
 */
public interface FileStorageService {

    // ---- 存储布局常量：清扫任务与存储层共用，避免两处字面量各自漂移 ----

    /** 成品目录名（内容寻址产物）。 */
    String OBJECTS_DIR = "objects";

    /** 存储相对路径里成品的前缀，业务表存的就是带此前缀的路径。 */
    String OBJECTS_PREFIX = OBJECTS_DIR + "/";

    /** 合并半成品后缀（{@code {sha256}{ext}.{UUID}.merging}）。 */
    String MERGING_SUFFIX = ".merging";

    /** 写入中临时文件后缀。 */
    String TMP_SUFFIX = ".tmp";

    /** 整传中转目录名（位于 {@code objects/{biz}/} 下）。 */
    String STAGING_DIR = ".staging";

    /** 导出暂存目录名（流式导出产物，非内容寻址，按龄期清扫）。 */
    String EXPORTS_DIR = "exports";

    /**
     * 初始化或恢复上传会话（幂等）。
     * <p>产物已存在 → 秒传，返回 {@code completedFile} 非空且 {@code uploadId} 为 null；
     * 会话已存在（Redis 命中，或 Redis 丢失后由磁盘 {@code meta.json} 重建）→ 返回会话与已收分片；
     * 否则新建会话。
     */
    UploadSessionView init(Long ownerId, String ownerType, FileBizEnum biz, String originalName,
                           long totalSize, int totalChunks, String sha256);

    /** 查询会话进度（只读，不创建）。会话不存在或已过期 → 404。 */
    UploadSessionView progress(Long ownerId, String uploadId);

    /**
     * 保存一个分片：写唯一名临时文件 → 流式限长校验大小 → 校验 SHA-256 → 原子 rename 为 {@code part-N}（提交点）。
     * 校验失败删临时文件不留痕；同 index 重传幂等覆盖。
     *
     * @return 当前已收分片数（Redis {@code SCARD}，O(1)）
     */
    int saveChunk(Long ownerId, String uploadId, int index, InputStream data, String chunkSha256);

    /**
     * 合并分片为成品：分片齐全校验 → 流式合并（全程不载内存）→ 重算总大小与整文件 SHA-256 →
     * 原子 rename 至 {@code objects/} → 释放分片目录。幂等：已合并的会话再次调用返回同一结果。
     * <p>校验失败删合并半成品、<b>保留分片</b>，可先 {@link #verify} 定位坏片再重传。
     */
    StoredFileRef complete(Long ownerId, String uploadId);

    /** 取消上传：删除分片目录与会话（不存在的会话视为已成功，幂等）。 */
    void abort(Long ownerId, String uploadId);

    /** 校验已收分片，返回缺失/损坏清单（把「全量重传」降级为「精准重传」）。 */
    VerifyResult verify(Long ownerId, String uploadId);

    /**
     * 整传（小文件一次 multipart 提交）：流式落盘到 {@code objects/{biz}/.staging/} → 校验 → 秒传判定。
     * <p><b>事务感知</b>：处于活动事务中时注册 {@code afterCommit} 才 rename 到最终路径，
     * 回滚则删 staging —— 消灭旧实现「先落盘、DB 回滚留孤儿」的缺陷。无事务时立即 rename。
     */
    StoredFileRef storeWhole(FileBizEnum biz, MultipartFile file);

    /**
     * 绑定分片产物：校验 {@code storedPath} 形状与归属业务目录后返回引用（不复制文件）。
     * 用于「二选一入口」中大文件走分片、业务请求只回传路径的场景。
     */
    StoredFileRef bind(FileBizEnum biz, String storedPath);

    /** 解析存储相对路径为磁盘路径（normalize + startsWith 路径穿越防护），不存在 → 404。 */
    Path resolve(String storedPath);
}
