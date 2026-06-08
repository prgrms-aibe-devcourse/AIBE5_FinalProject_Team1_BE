package com.team1.codedock.domain.auth.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class RefreshResponse {

    private String accessToken;
    private String refreshToken;
}