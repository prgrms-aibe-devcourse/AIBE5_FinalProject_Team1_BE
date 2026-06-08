package com.team1.codedock.domain.auth.service;

import com.team1.codedock.domain.auth.dto.TokenResponse;
import com.team1.codedock.domain.auth.entity.RefreshToken;
import com.team1.codedock.domain.auth.repository.RefreshTokenRepository;
import com.team1.codedock.domain.user.entity.User;
import com.team1.codedock.domain.user.repository.UserRepository;
import com.team1.codedock.global.exception.BusinessException;
import com.team1.codedock.global.exception.ErrorCode;
import com.team1.codedock.global.security.JwtProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final JwtProvider jwtProvider;
    private final RefreshTokenRepository refreshTokenRepository;
    private final UserRepository userRepository;

    @Transactional
    public TokenResponse refresh(String rawRefreshToken) {
        RefreshToken saved = refreshTokenRepository.findByToken(rawRefreshToken)
                .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_TOKEN));

        if (!saved.isValid()) {
            throw new BusinessException(ErrorCode.EXPIRED_TOKEN);
        }

        User user = saved.getUser();
        saved.revoke();

        String newAccessToken  = jwtProvider.generateAccessToken(user.getId());
        String newRefreshToken = jwtProvider.generateRefreshToken(user.getId());
        refreshTokenRepository.save(
                RefreshToken.create(user, newRefreshToken, jwtProvider.getRefreshTokenExpiry())
        );

        return new TokenResponse(newAccessToken, newRefreshToken);
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

    @Transactional
    public void logout(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        refreshTokenRepository.revokeAllByUser(user);
    }
}
