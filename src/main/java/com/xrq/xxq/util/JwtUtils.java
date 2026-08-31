package com.xrq.xxq.util;

import com.xrq.xxq.config.JwtConfig;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;

@Component
public class JwtUtils {

    private final SecretKey key;
    private final JwtConfig config;

    public JwtUtils(JwtConfig config) {
        this.config = config;
        byte[] keyBytes = Base64.getDecoder().decode(config.getSecret());
        this.key = Keys.hmacShaKeyFor(keyBytes);
    }

    public String generateAccessToken(Long userId, String userType, String role, String tokenId) {
        Instant now = Instant.now();
        Instant expiration = now.plus(config.getAccessTokenExpiration());

        return Jwts.builder()
                .subject(userId.toString())
                .claim("userType", userType)
                .claim("role", role)
                .claim("tokenId", tokenId)
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiration))
                .signWith(key)
                .compact();
    }

    public Claims parseToken(String token) {
        return Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}
