package com.team1.codedock.domain.user.dto;

import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class UpdateProfileRequest {

    @Size(max = 100)
    private String displayName;

    @Size(max = 50)
    private String nickname;

    @Size(max = 100)
    private String developerType;

    @Size(max = 160)
    private String bio;

    private String avatarUrl;
}