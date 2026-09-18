package com.xrq.xxq.module.file.cache;

import java.time.Duration;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import com.xrq.xxq.module.file.entity.FileBizEnum;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * {@link UploadProgressIndex} 的 Redis 实现。
 * <p>
 * Key 布局（{@code uploadId} 为服务端 UUID）：
 * <pre>
 * file:upload:{uploadId}          Hash   会话参数与状态（含 status / storedPath）
 * file:upload:{uploadId}:parts    Set    已提交分片序号
 * file:upload:{uploadId}:hashes   Hash   分片序号 -&gt; 分片 SHA-256
 * file:upload-idx:{ownerId}:{biz}:{sha256}:{totalSize}:{totalChunks}
 *                                 String -&gt; uploadId（刷新页面后同参数恢复）
 * </pre>
 * 会话 TTL 取 {@code file.upload-expire-hours}（默认 24h），每次活跃读写续期；
 * 合并成功后缩短为 {@link #MERGED_TTL}（残影窗口，供 complete 幂等返回）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RedisUploadProgressIndex implements UploadProgressIndex {

    private static final String SESSION_PREFIX = "file:upload:";
    private static final String PARTS_SUFFIX = ":parts";
    private static final String HASHES_SUFFIX = ":hashes";
    private static final String INDEX_PREFIX = "file:upload-idx:";

    /** 已合并会话的残影 TTL：足够客户端重试拿回同一结果，又不会长期占用内存。 */
    private static final Duration MERGED_TTL = Duration.ofHours(1);

    private static final String F_UPLOAD_ID = "uploadId";
    private static final String F_BIZ = "biz";
    private static final String F_ORIGINAL_NAME = "originalName";
    private static final String F_TOTAL_SIZE = "totalSize";
    private static final String F_CHUNK_SIZE = "chunkSize";
    private static final String F_TOTAL_CHUNKS = "totalChunks";
    private static final String F_SHA256 = "sha256";
    private static final String F_OWNER_ID = "ownerId";
    private static final String F_OWNER_TYPE = "ownerType";
    private static final String F_CREATE_TIME = "createTime";
    private static final String F_STATUS = "status";
    private static final String F_STORED_PATH = "storedPath";

    private final StringRedisTemplate redisTemplate;

    @Value("${file.upload-expire-hours:24}")
    private long uploadExpireHours;

    private Duration sessionTtl() {
        return Duration.ofHours(uploadExpireHours);
    }

    @Override
    public void save(SessionState state, Map<Integer, String> partHashes) {
        Duration ttl = sessionTtl();
        String key = SESSION_PREFIX + state.uploadId();

        Map<String, String> hash = new HashMap<>();
        hash.put(F_UPLOAD_ID, state.uploadId());
        hash.put(F_BIZ, state.biz().getCode());
        hash.put(F_ORIGINAL_NAME, state.originalName());
        hash.put(F_TOTAL_SIZE, String.valueOf(state.totalSize()));
        hash.put(F_CHUNK_SIZE, String.valueOf(state.chunkSize()));
        hash.put(F_TOTAL_CHUNKS, String.valueOf(state.totalChunks()));
        hash.put(F_SHA256, state.sha256());
        hash.put(F_OWNER_ID, state.ownerId() == null ? "" : String.valueOf(state.ownerId()));
        hash.put(F_OWNER_TYPE, state.ownerType() == null ? "" : state.ownerType());
        hash.put(F_CREATE_TIME, String.valueOf(state.createTime()));
        hash.put(F_STATUS, state.status().name());
        hash.put(F_STORED_PATH, state.storedPath() == null ? "" : state.storedPath());
        redisTemplate.opsForHash().putAll(key, hash);
        redisTemplate.expire(key, ttl);

        String partsKey = key + PARTS_SUFFIX;
        String hashesKey = key + HASHES_SUFFIX;
        if (partHashes != null && !partHashes.isEmpty()) {
            String[] members = partHashes.keySet().stream().map(String::valueOf).toArray(String[]::new);
            redisTemplate.opsForSet().add(partsKey, members);
            redisTemplate.expire(partsKey, ttl);

            Map<String, String> hashes = new HashMap<>();
            partHashes.forEach((i, sha) -> hashes.put(String.valueOf(i), sha == null ? "" : sha));
            redisTemplate.opsForHash().putAll(hashesKey, hashes);
            redisTemplate.expire(hashesKey, ttl);
        }
    }

    @Override
    public void touch(String uploadId) {
        Duration ttl = sessionTtl();
        String key = SESSION_PREFIX + uploadId;
        // 对不存在的 key 调用 expire 在 Redis 中是空操作，无需先判断存在性
        redisTemplate.expire(key, ttl);
        redisTemplate.expire(key + PARTS_SUFFIX, ttl);
        redisTemplate.expire(key + HASHES_SUFFIX, ttl);
    }

    @Override
    public SessionState load(String uploadId) {
        if (uploadId == null || uploadId.isBlank()) {
            return null;
        }
        Map<Object, Object> raw = redisTemplate.opsForHash().entries(SESSION_PREFIX + uploadId);
        if (raw == null || raw.isEmpty()) {
            return null;
        }
        String bizCode = str(raw, F_BIZ);
        FileBizEnum biz = FileBizEnum.fromCode(bizCode);
        if (biz == null) {
            // 枚举被改名/删除后的历史 key：视作脏数据，交由上层当会话不存在处理
            log.warn("上传会话 {} 的 biz 无法解析: {}", uploadId, bizCode);
            return null;
        }
        Status status;
        try {
            status = Status.valueOf(str(raw, F_STATUS));
        } catch (IllegalArgumentException | NullPointerException e) {
            status = Status.UPLOADING;
        }
        String storedPath = str(raw, F_STORED_PATH);
        return new SessionState(
                uploadId,
                biz,
                str(raw, F_ORIGINAL_NAME),
                parseLong(raw, F_TOTAL_SIZE, 0L),
                parseLong(raw, F_CHUNK_SIZE, 0L),
                (int) parseLong(raw, F_TOTAL_CHUNKS, 0L),
                str(raw, F_SHA256),
                parseLong(raw, F_OWNER_ID, null),
                emptyToNull(str(raw, F_OWNER_TYPE)),
                parseLong(raw, F_CREATE_TIME, 0L),
                status,
                (storedPath == null || storedPath.isEmpty()) ? null : storedPath);
    }

    @Override
    public void addPart(String uploadId, int index, String chunkSha256) {
        Duration ttl = sessionTtl();
        String key = SESSION_PREFIX + uploadId;
        String partsKey = key + PARTS_SUFFIX;
        String hashesKey = key + HASHES_SUFFIX;

        // SADD 天然幂等：同 index 重传不会重复计数
        redisTemplate.opsForSet().add(partsKey, String.valueOf(index));
        redisTemplate.expire(partsKey, ttl);
        if (chunkSha256 != null) {
            redisTemplate.opsForHash().put(hashesKey, String.valueOf(index), chunkSha256);
            redisTemplate.expire(hashesKey, ttl);
        }
    }

    @Override
    public int partCount(String uploadId) {
        Long size = redisTemplate.opsForSet().size(SESSION_PREFIX + uploadId + PARTS_SUFFIX);
        return size == null ? 0 : size.intValue();
    }

    @Override
    public Set<Integer> partIndexes(String uploadId) {
        Set<String> members = redisTemplate.opsForSet().members(SESSION_PREFIX + uploadId + PARTS_SUFFIX);
        if (members == null || members.isEmpty()) {
            return Set.of();
        }
        Set<Integer> indexes = new TreeSet<>();
        for (String m : members) {
            try {
                indexes.add(Integer.parseInt(m));
            } catch (NumberFormatException ignored) {
                // 脏成员跳过，不影响其余进度
            }
        }
        return indexes;
    }

    @Override
    public Map<Integer, String> partHashes(String uploadId) {
        Map<Object, Object> raw = redisTemplate.opsForHash()
                .entries(SESSION_PREFIX + uploadId + HASHES_SUFFIX);
        if (raw == null || raw.isEmpty()) {
            return Map.of();
        }
        Map<Integer, String> result = new LinkedHashMap<>();
        raw.forEach((k, v) -> {
            try {
                int i = Integer.parseInt(String.valueOf(k));
                String sha = String.valueOf(v);
                result.put(i, sha.isEmpty() ? null : sha);
            } catch (NumberFormatException ignored) {
                // 脏字段跳过，不影响其余摘要
            }
        });
        return result;
    }

    @Override
    public void markMerged(String uploadId, String storedPath) {
        String key = SESSION_PREFIX + uploadId;
        redisTemplate.opsForHash().put(key, F_STATUS, Status.MERGED.name());
        redisTemplate.opsForHash().put(key, F_STORED_PATH, storedPath);
        redisTemplate.expire(key, MERGED_TTL);
        // 已合并，分片进度不再有意义
        redisTemplate.delete(List.of(key + PARTS_SUFFIX, key + HASHES_SUFFIX));
    }

    @Override
    public void remove(String uploadId) {
        String key = SESSION_PREFIX + uploadId;
        redisTemplate.delete(List.of(key, key + PARTS_SUFFIX, key + HASHES_SUFFIX));
    }

    @Override
    public boolean bind(IndexKey key, String uploadId) {
        Boolean ok = redisTemplate.opsForValue()
                .setIfAbsent(indexKey(key), uploadId, sessionTtl());
        return Boolean.TRUE.equals(ok);
    }

    @Override
    public String lookup(IndexKey key) {
        return redisTemplate.opsForValue().get(indexKey(key));
    }

    private static String indexKey(IndexKey k) {
        return INDEX_PREFIX + k.ownerId() + ":" + k.biz().getCode() + ":" + k.sha256()
                + ":" + k.totalSize() + ":" + k.totalChunks();
    }

    private static String str(Map<Object, Object> raw, String field) {
        Object v = raw.get(field);
        return v == null ? null : v.toString();
    }

    private static String emptyToNull(String s) {
        return (s == null || s.isEmpty()) ? null : s;
    }

    private static long parseLong(Map<Object, Object> raw, String field, long fallback) {
        Long v = parseLong(raw, field, (Long) null);
        return v == null ? fallback : v;
    }

    private static Long parseLong(Map<Object, Object> raw, String field, Long fallback) {
        String s = str(raw, field);
        if (s == null || s.isEmpty()) {
            return fallback;
        }
        try {
            return Long.valueOf(s);
        } catch (NumberFormatException e) {
            return fallback;
        }
    }
}
