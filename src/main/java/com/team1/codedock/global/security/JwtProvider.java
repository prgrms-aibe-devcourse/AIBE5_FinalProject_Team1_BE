package com.team1.codedock.global.security;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.UUID;

@Slf4j
@Component
public class JwtProvider {

    private final SecretKey secretKey;
    private final long accessTokenExpiry;
    private final long refreshTokenExpiry;

    public JwtProvider(
            @Value("${jwt.secret}") String secret,
            @Value("${jwt.access-token-expiry}") long accessTokenExpiry,
            @Value("${jwt.refresh-token-expiry}") long refreshTokenExpiry) {
        this.secretKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.accessTokenExpiry = accessTokenExpiry;
        this.refreshTokenExpiry = refreshTokenExpiry;
    }

    public String generateAccessToken(Long userId) {
        return Jwts.builder()
                .id(UUID.randomUUID().toString())
                .subject(String.valueOf(userId))
                .claim("type", "access")
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + accessTokenExpiry))
                .signWith(secretKey)
                .compact();
    }

    public String generateRefreshToken(Long userId) {
        return Jwts.builder()
                .id(UUID.randomUUID().toString())
                .subject(String.valueOf(userId))
                .claim("type", "refresh")
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + refreshTokenExpiry))
                .signWith(secretKey)
                .compact();
    }

    public Long getUserId(String token) {
        return Long.parseLong(parseClaims(token).getSubject());
    }

    /**
     * API 인증용 — access 타입 토큰만 통과.
     * refresh 토큰을 Authorization 헤더에 넣어도 거부됨.
     */
    public boolean validateAccessToken(String token) {
        return validateAccessTokenWithResult(token) == JwtValidationResult.VALID;
    }

    public JwtValidationResult validateAccessTokenWithResult(String token) {
        return validateTokenWithResult(token, "access");
    }

    public JwtValidationResult validateRefreshTokenWithResult(String token) {
        return validateTokenWithResult(token, "refresh");
    }

    private JwtValidationResult validateTokenWithResult(String token, String expectedType) {
        try {
            Claims claims = parseClaims(token);
            if (expectedType.equals(claims.get("type", String.class))) {
                return JwtValidationResult.VALID;
            }
            return JwtValidationResult.INVALID;
        } catch (ExpiredJwtException e) {
            log.debug("Expired {} token: {}", expectedType, e.getMessage());
            return JwtValidationResult.EXPIRED;
        } catch (JwtException | IllegalArgumentException e) {
            log.debug("Invalid {} token: {}", expectedType, e.getMessage());
            return JwtValidationResult.INVALID;
        }
    }

    /**
     * 재발급 전용 — refresh 타입 토큰만 통과.
     */
    public boolean validateRefreshToken(String token) {
        return validateRefreshTokenWithResult(token) == JwtValidationResult.VALID;
    }

    /** @deprecated validateAccessToken() 사용 권장 */
    @Deprecated
    public boolean validate(String token) {
        return validateAccessToken(token);
    }

    public long getRefreshTokenExpiry() {
        return refreshTokenExpiry;
    }

    private Claims parseClaims(String token) {
        return Jwts.parser()
                .verifyWith(secretKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}
