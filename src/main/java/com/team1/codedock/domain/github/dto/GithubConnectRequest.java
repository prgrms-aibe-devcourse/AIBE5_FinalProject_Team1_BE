package com.team1.codedock.domain.github.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class GithubConnectRequest {

    @NotBlank(message = "GitHub 레포지토리 소유자는 필수입니다.")
    private String owner;

    @NotBlank(message = "GitHub 레포지토리 이름은 필수입니다.")
    private String repo;
}
