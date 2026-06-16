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
}
