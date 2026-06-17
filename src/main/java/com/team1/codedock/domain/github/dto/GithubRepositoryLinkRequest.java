package com.team1.codedock.domain.github.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record GithubRepositoryLinkRequest(
        @NotBlank(message = "GitHub repository id must not be blank.")
        @Size(max = 100, message = "GitHub repository id must be 100 characters or less.")
        String githubRepoId,

        @NotBlank(message = "GitHub repository owner must not be blank.")
        @Size(max = 100, message = "GitHub repository owner must be 100 characters or less.")
        String owner,

        @NotBlank(message = "GitHub repository name must not be blank.")
        @Size(max = 150, message = "GitHub repository name must be 150 characters or less.")
        String name,

        @NotBlank(message = "GitHub repository full name must not be blank.")
        @Size(max = 255, message = "GitHub repository full name must be 255 characters or less.")
        String fullName,

        @NotBlank(message = "GitHub repository url must not be blank.")
        String url,

        String description,

        boolean isPrivate,

        @Size(max = 255, message = "Default branch must be 255 characters or less.")
        String defaultBranch
) {
}
