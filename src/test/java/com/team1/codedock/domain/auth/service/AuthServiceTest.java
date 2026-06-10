package com.team1.codedock.domain.auth.service;

import com.team1.codedock.domain.auth.dto.*;
import com.team1.codedock.domain.auth.entity.RefreshToken;
import com.team1.codedock.domain.auth.repository.RefreshTokenRepository;
import com.team1.codedock.domain.user.dto.UserResponse;
import com.team1.codedock.domain.user.entity.User;
import com.team1.codedock.domain.user.repository.UserRepository;
import com.team1.codedock.global.exception.BusinessException;
import com.team1.codedock.global.exception.ErrorCode;
import com.team1.codedock.global.security.JwtProvider;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock private UserRepository userRepository;
    @Mock private RefreshTokenRepository refreshTokenRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private JwtProvider jwtProvider;

    @InjectMocks
    private AuthService authService;

    // ── signup ────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("정상 회원가입: 저장된 유저 정보를 담은 SignupResponse를 반환한다")
    void signup_success() {
        SignupRequest req = signupRequest("test@test.com", "테스트", "password1");
        User savedUser = user(1L, "test@test.com", "테스트");

        when(userRepository.existsByEmail("test@test.com")).thenReturn(false);
        when(passwordEncoder.encode("password1")).thenReturn("hashed");
        when(userRepository.save(any(User.class))).thenReturn(savedUser);

        SignupResponse response = authService.signup(req);

        assertThat(response.getEmail()).isEqualTo("test@test.com");
        assertThat(response.getUserId()).isEqualTo(1L);
    }

    @Test
    @DisplayName("중복 이메일 회원가입: EMAIL_ALREADY_EXISTS 예외가 발생한다")
    void signup_duplicateEmail() {
        SignupRequest req = signupRequest("dup@test.com", "testuser", "password1");
        when(userRepository.existsByEmail("dup@test.com")).thenReturn(true);

        assertThatThrownBy(() -> authService.signup(req))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.EMAIL_ALREADY_EXISTS);
    }

    // ── login ─────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("정상 로그인: access/refresh 토큰이 포함된 LoginResponse를 반환한다")
    void login_success() {
        LoginRequest req = loginRequest("test@test.com", "password1");
        User user = user(1L, "test@test.com", "testuser");

        when(userRepository.findByEmail("test@test.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("password1", user.getPasswordHash())).thenReturn(true);
        when(jwtProvider.generateAccessToken(1L)).thenReturn("access-token");
        when(jwtProvider.generateRefreshToken(1L)).thenReturn("refresh-token");
        when(jwtProvider.getRefreshTokenExpiry()).thenReturn(604800000L);
        when(refreshTokenRepository.save(any(RefreshToken.class))).thenAnswer(i -> i.getArguments()[0]);

        LoginResponse response = authService.login(req);

        assertThat(response.getAccessToken()).isEqualTo("access-token");
        assertThat(response.getRefreshToken()).isEqualTo("refresh-token");
        assertThat(response.getUser().getEmail()).isEqualTo("test@test.com");
        verify(refreshTokenRepository).deleteByUser(user);
    }

    @Test
    @DisplayName("존재하지 않는 이메일 로그인: USER_NOT_FOUND 예외가 발생한다")
    void login_userNotFound() {
        LoginRequest req = loginRequest("nobody@test.com", "password1");
        when(userRepository.findByEmail("nobody@test.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.login(req))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.USER_NOT_FOUND);
    }

    @Test
    @DisplayName("틀린 비밀번호 로그인: UNAUTHORIZED 예외가 발생한다")
    void login_wrongPassword() {
        LoginRequest req = loginRequest("test@test.com", "wrongpw");
        User user = user(1L, "test@test.com", "testuser");

        when(userRepository.findByEmail("test@test.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("wrongpw", user.getPasswordHash())).thenReturn(false);

        assertThatThrownBy(() -> authService.login(req))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.UNAUTHORIZED);
    }

    // ── refresh ───────────────────────────────────────────────────────────────

    @Test
    @DisplayName("정상 토큰 재발급: 새로운 토큰 쌍을 반환하고 기존 토큰을 revoke한다")
    void refresh_success() {
        User user = user(1L, "test@test.com", "testuser");
        RefreshToken saved = refreshToken(user, "old-refresh-token");

        when(jwtProvider.validateRefreshToken("old-refresh-token")).thenReturn(true);
        when(refreshTokenRepository.findByToken("old-refresh-token")).thenReturn(Optional.of(saved));
        when(jwtProvider.generateAccessToken(1L)).thenReturn("new-access-token");
        when(jwtProvider.generateRefreshToken(1L)).thenReturn("new-refresh-token");
        when(jwtProvider.getRefreshTokenExpiry()).thenReturn(604800000L);
        when(refreshTokenRepository.save(any(RefreshToken.class))).thenAnswer(i -> i.getArguments()[0]);

        TokenResponse response = authService.refresh("old-refresh-token");

        assertThat(response.accessToken()).isEqualTo("new-access-token");
        assertThat(response.refreshToken()).isEqualTo("new-refresh-token");
        assertThat(saved.isRevoked()).isTrue();
        verify(refreshTokenRepository).save(any(RefreshToken.class));
    }

    @Test
    @DisplayName("JWT 서명 검증 실패: INVALID_TOKEN 예외가 발생한다")
    void refresh_invalidJwt() {
        when(jwtProvider.validateRefreshToken("bad-token")).thenReturn(false);

        assertThatThrownBy(() -> authService.refresh("bad-token"))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INVALID_TOKEN);
    }

    @Test
    @DisplayName("DB에 없는 토큰으로 재발급: INVALID_TOKEN 예외가 발생한다")
    void refresh_tokenNotInDb() {
        when(jwtProvider.validateRefreshToken("unknown-token")).thenReturn(true);
        when(refreshTokenRepository.findByToken("unknown-token")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.refresh("unknown-token"))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INVALID_TOKEN);
    }

    @Test
    @DisplayName("이미 revoke된 토큰으로 재발급: EXPIRED_TOKEN 예외가 발생한다")
    void refresh_revokedToken() {
        User user = user(1L, "test@test.com", "testuser");
        RefreshToken revokedToken = refreshToken(user, "revoked-token");
        revokedToken.revoke();

        when(jwtProvider.validateRefreshToken("revoked-token")).thenReturn(true);
        when(refreshTokenRepository.findByToken("revoked-token")).thenReturn(Optional.of(revokedToken));

        assertThatThrownBy(() -> authService.refresh("revoked-token"))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.EXPIRED_TOKEN);
    }

    // ── logout ────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("정상 로그아웃: 해당 유저의 모든 refresh token이 revoke된다")
    void logout_success() {
        User user = user(1L, "test@test.com", "testuser");
        RefreshToken token = refreshToken(user, "some-token");

        when(jwtProvider.validateRefreshToken("some-token")).thenReturn(true);
        when(refreshTokenRepository.findByToken("some-token")).thenReturn(Optional.of(token));

        authService.logout("some-token");

        verify(refreshTokenRepository).revokeAllByUser(user);
    }

    // ── issueTokens ───────────────────────────────────────────────────────────

    @Test
    @DisplayName("OAuth2 토큰 발급: access/refresh 토큰 쌍을 반환하고 기존 토큰을 revoke한다")
    void issueTokens_success() {
        User user = user(1L, "test@test.com", "testuser");

        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(jwtProvider.generateAccessToken(1L)).thenReturn("access-token");
        when(jwtProvider.generateRefreshToken(1L)).thenReturn("refresh-token");
        when(jwtProvider.getRefreshTokenExpiry()).thenReturn(604800000L);
        when(refreshTokenRepository.save(any(RefreshToken.class))).thenAnswer(i -> i.getArguments()[0]);

        TokenResponse response = authService.issueTokens(1L);

        assertThat(response.accessToken()).isEqualTo("access-token");
        assertThat(response.refreshToken()).isEqualTo("refresh-token");
        verify(refreshTokenRepository).revokeAllByUser(user);
    }

    // ── me ────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("내 정보 조회: 현재 유저 정보를 담은 UserResponse를 반환한다")
    void me_success() {
        User user = user(1L, "test@test.com", "testuser");
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));

        UserResponse response = authService.me(1L);

        assertThat(response.getId()).isEqualTo(1L);
        assertThat(response.getEmail()).isEqualTo("test@test.com");
        assertThat(response.getDisplayName()).isEqualTo("testuser");
    }

    @Test
    @DisplayName("존재하지 않는 유저 정보 조회: USER_NOT_FOUND 예외가 발생한다")
    void me_userNotFound() {
        when(userRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.me(99L))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.USER_NOT_FOUND);
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private static SignupRequest signupRequest(String email, String displayName, String password) {
        SignupRequest req = new SignupRequest();
        req.setEmail(email);
        req.setDisplayName(displayName);
        req.setPassword(password);
        return req;
    }

    private static LoginRequest loginRequest(String email, String password) {
        LoginRequest req = new LoginRequest();
        req.setEmail(email);
        req.setPassword(password);
        return req;
    }

    private static User user(Long id, String email, String displayName) {
        User user = User.create(email, "hashed-password", displayName);
        ReflectionTestUtils.setField(user, "id", id);
        return user;
    }

    private static RefreshToken refreshToken(User user, String token) {
        return RefreshToken.create(user, token, 604800000L);
    }
}