package com.team1.codedock.domain.user.dto;

import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class UpdateProfileRequest {

    @Size(max = 100, message = "표시 이름은 100자 이하로 입력해주세요.")
    private String displayName;

    @Size(max = 50, message = "닉네임은 50자 이하로 입력해주세요.")
    private String nickname;

    @Size(max = 100, message = "개발자 유형은 100자 이하로 입력해주세요.")
    private String developerType;

    @Size(max = 160, message = "자기소개는 160자 이하로 입력해주세요.")
    private String bio;

    private String avatarUrl;
}