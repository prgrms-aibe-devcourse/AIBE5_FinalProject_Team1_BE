package com.team1.codedock.domain.user.controller;

import com.team1.codedock.domain.user.dto.UpdateProfileRequest;
import com.team1.codedock.domain.user.dto.UpdateSkillsRequest;
import com.team1.codedock.domain.user.dto.UserResponse;
import com.team1.codedock.domain.user.service.UserService;
import com.team1.codedock.global.response.ApiResponse;
import com.team1.codedock.global.security.SecurityUtils;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @PatchMapping("/me")
    public ApiResponse<UserResponse> updateProfile(@RequestBody @Valid UpdateProfileRequest request) {
        return ApiResponse.ok(userService.updateProfile(SecurityUtils.getCurrentUserId(), request));
    }

    @PutMapping("/me/skills")
    public ApiResponse<List<String>> updateSkills(@RequestBody @Valid UpdateSkillsRequest request) {
        return ApiResponse.ok(userService.updateSkills(SecurityUtils.getCurrentUserId(), request));
    }

    @GetMapping("/me/skills")
    public ApiResponse<List<String>> getSkills() {
        return ApiResponse.ok(userService.getSkills(SecurityUtils.getCurrentUserId()));
    }

    @DeleteMapping("/me/github")
    public ApiResponse<UserResponse> disconnectGithub() {
        return ApiResponse.ok(userService.disconnectGithub(SecurityUtils.getCurrentUserId()));
    }

    @DeleteMapping("/me")
    public ApiResponse<Void> withdraw() {
        userService.withdraw(SecurityUtils.getCurrentUserId());
        return ApiResponse.ok();
    }

    @GetMapping("/{userId}")
    public ApiResponse<UserResponse> getUserById(@PathVariable Long userId) {
        return ApiResponse.ok(userService.getUserById(userId));
    }
}
