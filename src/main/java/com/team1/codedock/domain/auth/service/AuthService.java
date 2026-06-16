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
import com.team1.codedock.global.security.GithubLinkTokenProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtProvider jwtProvider;
    private final GithubLinkTokenProvider githubLinkTokenProvider;

    @Transactional
    public SignupResponse signup(SignupRequest request) {
        Long userId;
        try {
            userId = githubLinkTokenProvider.validateAndGetUserId(request.getGithubLinkToken());
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.INVALID_TOKEN);
        }
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        if (user.getPasswordHash() != null) {
            throw new BusinessException(ErrorCode.EMAIL_ALREADY_EXISTS);
        }
        userRepository.findByEmailIgnoreCase(request.getEmail())
                .filter(existing -> !existing.getId().equals(userId))
                .ifPresent(existing -> { throw new BusinessException(ErrorCode.EMAIL_ALREADY_EXISTS); });
        String hash = passwordEncoder.encode(request.getPassword());
        user.completeEmailSignup(request.getEmail(), hash, request.getDisplayName());
        return SignupResponse.from(user);
    }

    @Transactional
    public LoginResponse login(LoginRequest request) {
        User user = userRepository.findByEmailIgnoreCase(request.getEmail())
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
        user.updateLastLogin();
        refreshTokenRepository.deleteByUser(user);
        return buildLoginResponse(user);
    }

    @Transactional
    public TokenResponse refresh(String rawRefreshToken) {
        if (!jwtProvider.validateRefreshToken(rawRefreshToken)) {
            throw new BusinessException(ErrorCode.INVALID_TOKEN);
        }
        RefreshToken saved = refreshTokenRepository.findByToken(rawRefreshToken)
                .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_TOKEN));
        if (!saved.isValid()) {
            throw new BusinessException(ErrorCode.EXPIRED_TOKEN);
        }
        User user = saved.getUser();
        saved.revoke();
        String newAccess  = jwtProvider.generateAccessToken(user.getId());
        String newRefresh = jwtProvider.generateRefreshToken(user.getId());
        refreshTokenRepository.save(
                RefreshToken.create(user, newRefresh, jwtProvider.getRefreshTokenExpiry())
        );
        return new TokenResponse(newAccess, newRefresh);
    }

    @Transactional
    public void logout(String rawRefreshToken) {
        if (!jwtProvider.validateRefreshToken(rawRefreshToken)) {
            throw new BusinessException(ErrorCode.INVALID_TOKEN);
        }
        RefreshToken saved = refreshTokenRepository.findByToken(rawRefreshToken)
                .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_TOKEN));
        refreshTokenRepository.revokeAllByUser(saved.getUser());
    }

    @Transactional
    public TokenResponse issueTokens(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        refreshTokenRepository.revokeAllByUser(user);
        String accessToken  = jwtProvider.generateAccessToken(userId);
        String refreshToken = jwtProvider.generateRefreshToken(userId);
        refreshTokenRepository.save(
                RefreshToken.create(user, refreshToken, jwtProvider.getRefreshTokenExpiry())
        );
        return new TokenResponse(accessToken, refreshToken);
    }

    @Transactional(readOnly = true)
    public UserResponse me(Long currentUserId) {
        User user = userRepository.findById(currentUserId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        return UserResponse.from(user);
    }

    private LoginResponse buildLoginResponse(User user) {
        String accessToken = jwtProvider.generateAccessToken(user.getId());
        String rawRefresh  = jwtProvider.generateRefreshToken(user.getId());
        refreshTokenRepository.save(
                RefreshToken.create(user, rawRefresh, jwtProvider.getRefreshTokenExpiry())
        );
        return LoginResponse.builder()
                .accessToken(accessToken)
                .refreshToken(rawRefresh)
                .user(LoginUserInfo.from(user))
                .build();
    }
}