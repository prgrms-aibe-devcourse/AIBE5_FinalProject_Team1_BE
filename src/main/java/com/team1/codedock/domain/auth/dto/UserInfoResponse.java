package com.team1.codedock.domain.auth.dto;

import com.team1.codedock.domain.user.entity.User;

public record UserInfoResponse(
        Long id,
        String email,
        String username,
        String displayName,
        String avatarUrl,
        String githubUsername,
        boolean githubConnected
) {
    public static UserInfoResponse from(User user) {
        return new UserInfoResponse(
                user.getId(),
                user.getEmail(),
                user.getUsername(),
                user.getDisplayName(),
                user.getAvatarUrl(),
                user.getGithubUsername(),
                user.isGithubConnected()
        );
    }
}
