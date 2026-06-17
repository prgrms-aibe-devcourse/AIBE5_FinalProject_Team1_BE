package com.team1.codedock.global.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

@Component
public class GithubConnectTokenProvider {

    private static final String TOKEN_TYPE = "github_connect";
    private static final long CONNECT_TOKEN_EXPIRY_MS = 5 * 60 * 1000L;

    private final SecretKey secretKey;

    public GithubConnectTokenProvider(@Value("${jwt.secret}") String secret) {
        this.secretKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    public String generate(Long userId) {
        long now = System.currentTimeMillis();
        return Jwts.builder()
                .subject(String.valueOf(userId))
                .claim("type", TOKEN_TYPE)
                .issuedAt(new Date(now))
                .expiration(new Date(now + CONNECT_TOKEN_EXPIRY_MS))
                .signWith(secretKey)
                .compact();
    }

    public Long validateAndGetUserId(String token) {
        Claims claims = Jwts.parser()
                .verifyWith(secretKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
        if (!TOKEN_TYPE.equals(claims.get("type", String.class))) {
            throw new JwtException("github_connect 토큰이 아닙니다.");
        }
        return Long.parseLong(claims.getSubject());
    }
}