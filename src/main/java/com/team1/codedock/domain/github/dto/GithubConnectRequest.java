package com.team1.codedock.domain.github.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class GithubConnectRequest {

    @NotBlank
    private String owner;

    @NotBlank
    private String repo;
}
