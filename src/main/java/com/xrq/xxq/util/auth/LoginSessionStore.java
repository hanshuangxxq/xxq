package com.xrq.xxq.util.auth;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;

@Component
public class LoginSessionStore {

    private static final String PREFIX = "session:";
    private static final Duration TTL = Duration.ofDays(7);

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    public LoginSessionStore(StringRedisTemplate redisTemplate, ObjectMapper objectMapper) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
    }

    public void put(String tokenId, UserSession session) {
        try {
            String json = objectMapper.writeValueAsString(session);
            redisTemplate.opsForValue().set(PREFIX + tokenId, json, TTL);
        } catch (JacksonException e) {
            throw new RuntimeException("序列化 session 失败", e);
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

    public void remove(String tokenId) {
        redisTemplate.delete(PREFIX + tokenId);
    }
}
