package com.team1.codedock.domain.auth.dto;

import com.team1.codedock.domain.user.entity.User;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class LoginUserInfo {

    private Long id;
    private String email;
    private String username;
    private String avatarUrl;

    public static LoginUserInfo from(User user) {
        return LoginUserInfo.builder()
                .id(user.getId())
                .email(user.getEmail())
                .username(user.getUsername())
                .avatarUrl(user.getAvatarUrl())
                .build();
    }
}