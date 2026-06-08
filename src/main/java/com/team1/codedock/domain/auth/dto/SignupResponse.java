package com.team1.codedock.domain.auth.dto;

import com.team1.codedock.domain.user.entity.User;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class SignupResponse {

    private Long userId;
    private String email;
    private String username;

    public static SignupResponse from(User user) {
        return SignupResponse.builder()
                .userId(user.getId())
                .email(user.getEmail())
                .username(user.getUsername())
                .build();
    }
}