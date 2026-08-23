package com.xrq.xxq.util.auth;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.time.LocalDateTime;

@Component
public class LoginSessionStore {

    private static final String PREFIX = "session:";
    /** 用户会话列表：user:sessions:{userId} -> List<tokenId>，头部为最新登录 */
    private static final String USER_PREFIX = "user:sessions:";
    private static final Duration TTL = Duration.ofDays(7);
    /** 每个用户最多并存的会话数（多端共存，超出时清理最早登录的会话） */
    private static final int MAX_SESSIONS_PER_USER = 5;

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    public LoginSessionStore(StringRedisTemplate redisTemplate, ObjectMapper objectMapper) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
    }

    /**
     * 登录或重建时写入会话，并纳入用户会话列表（多端共存）。
     * 超出 {@link #MAX_SESSIONS_PER_USER} 时，清理最早登录的会话。
     */
    public void login(Long userId, String tokenId, UserSession session) {
        redisTemplate.opsForValue().set(PREFIX + tokenId, writeJson(session), TTL);

        String listKey = USER_PREFIX + userId;
        // 先移除可能存在的旧条目（幂等：重建场景 tokenId 不变时把它挪到列表头部，避免重复）
        redisTemplate.opsForList().remove(listKey, 1, tokenId);
        redisTemplate.opsForList().leftPush(listKey, tokenId);

        // 清理超出上限的最早会话（列表尾部）
        Long size = redisTemplate.opsForList().size(listKey);
        if (size != null) {
            Long extra = size - MAX_SESSIONS_PER_USER;
            for (Long i = 0L; i < extra; i++) {
                String evicted = redisTemplate.opsForList().rightPop(listKey);
                if (evicted != null && !evicted.equals(tokenId)) {
                    redisTemplate.delete(PREFIX + evicted);
                }
            }
        }
        redisTemplate.expire(listKey, TTL);
    }

    /**
     * access token 仍有效但 Redis 无会话时（如后端/Redis 重启致数据丢失），
     * 基于 JWT claims 重建最小会话并写入，使后续请求命中会话而免重登。
     * <p>
     * 安全权衡：登出删除会话后，在 token 有效期内（默认 30m）仍可被重建访问。
     */
    public UserSession rebuildIfNeeded(String tokenId, Long userId, String userType, String role) {
        UserSession existing = get(tokenId);
        if (existing != null) {
            return existing;
        }
        UserSession session = new UserSession();
        session.setUserId(userId);
        session.setUserType(userType);
        session.setRole(role);
        session.setTokenId(tokenId);
        session.setLoginTime(LocalDateTime.now());
        login(userId, tokenId, session);
        return session;
    }

    /**
     * 刷新 token 时仅覆盖单个会话内容并续期，不调整会话列表顺序。
     * 同时续期用户会话列表，避免长期 refresh 后列表先于会话过期。
     */
    public void put(String tokenId, UserSession session) {
        redisTemplate.opsForValue().set(PREFIX + tokenId, writeJson(session), TTL);
        if (session.getUserId() != null) {
            redisTemplate.expire(USER_PREFIX + session.getUserId(), TTL);
        }
    }

    public UserSession get(String tokenId) {
        String json = redisTemplate.opsForValue().get(PREFIX + tokenId);
        if (json == null) {
            return null;
        }
        try {
            UserSession session = objectMapper.readValue(json, UserSession.class);
            session.setTokenId(tokenId);
            return session;
        } catch (JacksonException e) {
            throw new RuntimeException("反序列化 session 失败", e);
        }
    }

    /** 登出：删除会话并从用户会话列表移除。 */
    public void remove(String tokenId) {
        UserSession session = get(tokenId);
        redisTemplate.delete(PREFIX + tokenId);
        if (session != null && session.getUserId() != null) {
            redisTemplate.opsForList().remove(USER_PREFIX + session.getUserId(), 1, tokenId);
        }
    }

    private String writeJson(UserSession session) {
        try {
            return objectMapper.writeValueAsString(session);
        } catch (JacksonException e) {
            throw new RuntimeException("序列化 session 失败", e);
        }
    }
}
