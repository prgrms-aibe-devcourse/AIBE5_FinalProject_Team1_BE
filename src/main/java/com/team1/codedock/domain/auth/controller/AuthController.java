package com.team1.codedock.domain.auth.controller;

import com.team1.codedock.domain.auth.dto.RefreshRequest;
import com.team1.codedock.domain.auth.dto.TokenResponse;
import com.team1.codedock.domain.auth.dto.UserInfoResponse;
import com.team1.codedock.domain.auth.service.AuthService;
import com.team1.codedock.domain.user.entity.User;
import com.team1.codedock.domain.user.repository.UserRepository;
import com.team1.codedock.global.exception.BusinessException;
import com.team1.codedock.global.exception.ErrorCode;
import com.team1.codedock.global.response.ApiResponse;
import com.team1.codedock.global.security.CustomUserDetails;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final UserRepository userRepository;

    /** 현재 로그인 유저 정보 조회 */
    @GetMapping("/me")
    public ResponseEntity<ApiResponse<UserInfoResponse>> me(
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        User user = userRepository.findById(userDetails.getUserId())
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        return ResponseEntity.ok(ApiResponse.ok(UserInfoResponse.from(user)));
    }

    /** Access Token 재발급 */
    @PostMapping("/refresh")
    public ResponseEntity<ApiResponse<TokenResponse>> refresh(
            @Valid @RequestBody RefreshRequest request) {
        TokenResponse tokens = authService.refresh(request.refreshToken());
        return ResponseEntity.ok(ApiResponse.ok(tokens));
    }

    /** 로그아웃 — refresh token 전부 revoke */
    @PostMapping("/logout")
    public ResponseEntity<ApiResponse<Void>> logout(
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        authService.logout(userDetails.getUserId());
        return ResponseEntity.ok(ApiResponse.ok());
    }
}
