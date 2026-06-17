package com.team1.codedock.domain.github.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record GithubRepositoryLinkRequest(
        @NotBlank(message = "GitHub 레포지토리 ID는 필수입니다.")
        @Size(max = 100, message = "GitHub 레포지토리 ID는 100자 이하로 입력해주세요.")
        String githubRepoId,

        @NotBlank(message = "GitHub 레포지토리 소유자는 필수입니다.")
        @Size(max = 100, message = "GitHub 레포지토리 소유자는 100자 이하로 입력해주세요.")
        String owner,

        @NotBlank(message = "GitHub 레포지토리 이름은 필수입니다.")
        @Size(max = 120, message = "GitHub 레포지토리 이름은 120자 이하로 입력해주세요.")
        String name,

        @NotBlank(message = "GitHub 레포지토리 전체 이름은 필수입니다.")
        @Size(max = 255, message = "GitHub 레포지토리 전체 이름은 255자 이하로 입력해주세요.")
        String fullName,

        @NotBlank(message = "GitHub 레포지토리 URL은 필수입니다.")
        String url,

        String description,

        boolean isPrivate,

        @Size(max = 255, message = "기본 브랜치 이름은 255자 이하로 입력해주세요.")
        String defaultBranch
) {
}
