package com.team1.codedock.domain.user.dto;

import com.team1.codedock.domain.user.entity.User;
import lombok.Builder;
import lombok.Getter;

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
                .build();
    }
}