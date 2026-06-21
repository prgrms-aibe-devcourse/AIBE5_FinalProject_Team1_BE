package com.team1.codedock.global.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

class JwtProviderTest {

    private static final String SECRET = "test-secret-key-must-be-at-least-32-characters-long";
    private static final long ACCESS_TOKEN_EXPIRY = 3_600_000L;
    private static final long REFRESH_TOKEN_EXPIRY = 604_800_000L;

    private final JwtProvider jwtProvider = new JwtProvider(
            SECRET,
            ACCESS_TOKEN_EXPIRY,
            REFRESH_TOKEN_EXPIRY
    );

    @Test
    @DisplayName("refresh token includes unique jwt id")
    void generateRefreshTokenCreatesUniqueTokens() {
        Long userId = 1L;

        List<String> tokens = IntStream.range(0, 10)
                .mapToObj(ignored -> jwtProvider.generateRefreshToken(userId))
                .toList();

        assertThat(tokens).doesNotHaveDuplicates();
        assertThat(tokens).allSatisfy(token -> {
            assertThat(jwtProvider.validateRefreshToken(token)).isTrue();
            assertThat(jwtProvider.validateAccessToken(token)).isFalse();
            assertThat(jwtProvider.getUserId(token)).isEqualTo(userId);
        });
    }

    @Test
    @DisplayName("access token includes unique jwt id")
    void generateAccessTokenCreatesUniqueTokens() {
        Long userId = 1L;

        List<String> tokens = IntStream.range(0, 10)
                .mapToObj(ignored -> jwtProvider.generateAccessToken(userId))
                .toList();

        assertThat(tokens).doesNotHaveDuplicates();
        assertThat(tokens).allSatisfy(token -> {
            assertThat(jwtProvider.validateAccessToken(token)).isTrue();
            assertThat(jwtProvider.validateRefreshToken(token)).isFalse();
            assertThat(jwtProvider.getUserId(token)).isEqualTo(userId);
        });
    }

    @Test
    @DisplayName("access token 검증 결과는 정상, 만료, 무효 상태를 구분한다")
    void validateAccessTokenWithResultDistinguishesFailureReason() {
        JwtProvider expiredTokenProvider = new JwtProvider(SECRET, -1_000L, REFRESH_TOKEN_EXPIRY);
        String validAccessToken = jwtProvider.generateAccessToken(1L);
        String expiredAccessToken = expiredTokenProvider.generateAccessToken(1L);
        String refreshToken = jwtProvider.generateRefreshToken(1L);

        assertThat(jwtProvider.validateAccessTokenWithResult(validAccessToken))
                .isEqualTo(JwtValidationResult.VALID);
        assertThat(jwtProvider.validateAccessTokenWithResult(expiredAccessToken))
                .isEqualTo(JwtValidationResult.EXPIRED);
        assertThat(jwtProvider.validateAccessTokenWithResult(refreshToken))
                .isEqualTo(JwtValidationResult.INVALID);
        assertThat(jwtProvider.validateAccessTokenWithResult("not-a-jwt"))
                .isEqualTo(JwtValidationResult.INVALID);
    }

    @Test
    @DisplayName("refresh token 검증 결과는 정상, 만료, 무효 상태를 구분한다")
    void validateRefreshTokenWithResultDistinguishesFailureReason() {
        JwtProvider expiredTokenProvider = new JwtProvider(SECRET, ACCESS_TOKEN_EXPIRY, -1_000L);
        String validRefreshToken = jwtProvider.generateRefreshToken(1L);
        String expiredRefreshToken = expiredTokenProvider.generateRefreshToken(1L);
        String accessToken = jwtProvider.generateAccessToken(1L);

        assertThat(jwtProvider.validateRefreshTokenWithResult(validRefreshToken))
                .isEqualTo(JwtValidationResult.VALID);
        assertThat(jwtProvider.validateRefreshTokenWithResult(expiredRefreshToken))
                .isEqualTo(JwtValidationResult.EXPIRED);
        assertThat(jwtProvider.validateRefreshTokenWithResult(accessToken))
                .isEqualTo(JwtValidationResult.INVALID);
        assertThat(jwtProvider.validateRefreshTokenWithResult("not-a-jwt"))
                .isEqualTo(JwtValidationResult.INVALID);
    }
}
