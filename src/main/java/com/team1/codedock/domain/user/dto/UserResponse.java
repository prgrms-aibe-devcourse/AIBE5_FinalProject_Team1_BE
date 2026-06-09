package com.team1.codedock.domain.user.dto;

import com.team1.codedock.domain.user.entity.User;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
public class UserResponse {

    private Long id;
    private String email;
    private String username;
    private String displayName;
    private String nickname;
    private String developerType;
    private String bio;
    private String avatarUrl;
    private boolean githubConnected;
    private String githubUsername;
    private String githubEmail;
    private LocalDateTime githubConnectedAt;

    public static UserResponse from(User user) {
        return UserResponse.builder()
                .id(user.getId())
                .email(user.getEmail())
                .username(user.getUsername())
                .displayName(user.getDisplayName())
                .nickname(user.getNickname())
                .developerType(user.getDeveloperType())
                .bio(user.getBio())
                .avatarUrl(user.getAvatarUrl())
                .githubConnected(user.isGithubConnected())
                .githubUsername(user.getGithubUsername())
                .githubEmail(user.getGithubEmail())
                .githubConnectedAt(user.getGithubConnectedAt())
                .build();
    }
}