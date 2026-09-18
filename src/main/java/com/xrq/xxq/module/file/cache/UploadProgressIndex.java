package com.xrq.xxq.module.file.cache;

import java.util.Map;
import java.util.Set;

import com.xrq.xxq.module.file.entity.FileBizEnum;

/**
 * 上传会话进度索引：分片上传的**临时**状态（会话参数 + 已收分片 + 分片摘要 + 恢复索引）。
 * <p>
 * 设计取舍：
 * <ul>
 *   <li><b>不落库</b>：会话是传输期临时状态，产物交付后由 TTL 自动清除，无需常驻元数据表，
 *       也无需清理任务扫表。</li>
 *   <li><b>抽取接口</b>：本项目无 Mockito / embedded-redis（pom 无相关依赖），纯逻辑组件必须能
 *       脱离 Spring 直接 new 才有单测；本接口的存在让存储层可以用内存替身做零依赖测试，
 *       并能**确定性**地验证「Redis 全丢 → 从磁盘 meta.json 重建进度」这条关键路径。</li>
 *   <li><b>已收分片用 Set 而非 Bitmap</b>：410 片时 Bitmap(52B) 比 Set(几 KB) 省，但恢复时要
 *       把序号全读出来得 {@code BITFIELD GET u1} 分段拉或遍历计数，代码量与出错面都更大；
 *       而旧实现真正的性能问题是「每次 saveChunk 都全目录 list + 逐文件 stat」的 O(N²)，
 *       用 {@code SCARD} 一行即可消灭。</li>
 * </ul>
 * 磁盘上的 {@code meta.json} 仍是权威持久镜像：Redis 丢失时由它重建本索引。
 */
public interface UploadProgressIndex {

    /** 会话状态：UPLOADING 进行中；MERGED 已合并（残影，供 complete 幂等返回）。 */
    enum Status {
        UPLOADING,
        MERGED
    }

    /** 会话不可变参数 + 可变状态。{@code storedPath} 仅 {@link Status#MERGED} 时非空。 */
    record SessionState(String uploadId, FileBizEnum biz, String originalName, long totalSize,
                        long chunkSize, int totalChunks, String sha256, Long ownerId, String ownerType,
                        long createTime, Status status, String storedPath) {
    }

    /**
     * 「刷新页面后同参数重新 init 应恢复同一会话」的查找键。
     * <p>含 {@code totalSize}/{@code totalChunks} 是刻意的：分片粒度变了，旧分片本就全部失效，
     * 必须开新会话而非复用。
     */
    record IndexKey(Long ownerId, FileBizEnum biz, String sha256, long totalSize, int totalChunks) {
    }

    /** 全量写入会话与已收分片（创建与「Redis 丢失后从磁盘重建」共用），并置 TTL。 */
    void save(SessionState state, Map<Integer, String> partHashes);

    /** 续期（活跃会话不因 TTL 到期被清）。 */
    void touch(String uploadId);

    /** 读取会话；不存在（未创建 / 已 TTL 过期 / 已 remove）返回 {@code null}。 */
    SessionState load(String uploadId);

    /** 记录一个已提交分片（幂等，重复 SADD 不重复计数）并续期。 */
    void addPart(String uploadId, int index, String chunkSha256);

    /**
     * 已收分片数量。
     * <p>对应 Redis {@code SCARD}（O(1)）—— 这是取代旧实现「每次 saveChunk 都全目录 list +
     * 逐文件 stat」的 O(N²) IO 的关键：上传最后一片时不再需要扫 410 个文件。
     */
    int partCount(String uploadId);

    /** 已收分片序号（升序）。 */
    Set<Integer> partIndexes(String uploadId);

    /** 已收分片序号 -> 分片 SHA-256；未记录摘要的片其值为 {@code null}。 */
    Map<Integer, String> partHashes(String uploadId);

    /** 标记为已合并并缓存产物路径，TTL 缩短为残影窗口，同时释放分片相关 key。 */
    void markMerged(String uploadId, String storedPath);

    /** 删除会话及其分片、摘要（abort 用）。 */
    void remove(String uploadId);

    /**
     * 建立「同 owner + 同 biz + 同内容 + 同分片参数 → uploadId」索引（set-if-absent 语义）。
     *
     * @return {@code true} 表示本次写入生效；{@code false} 表示已存在他人先建的索引
     *         （并发 init 同一文件时，后到者应放弃自己的会话并复用已有 uploadId）
     */
    boolean bind(IndexKey key, String uploadId);

    /** 查索引；未命中返回 {@code null}。 */
    String lookup(IndexKey key);
}
