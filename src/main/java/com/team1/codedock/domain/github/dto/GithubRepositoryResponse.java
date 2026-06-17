package com.team1.codedock.domain.github.dto;

import com.team1.codedock.domain.github.entity.GithubRepository;

public record GithubRepositoryResponse(
        Long id,
        Long workspaceId,
        String githubRepoId,
        String owner,
        String name,
        String fullName,
        String url,
        String description,
        boolean isPrivate,
        String defaultBranch
) {
    public static GithubRepositoryResponse from(GithubRepository repository) {
        return new GithubRepositoryResponse(
                repository.getId(),
                repository.getWorkspace().getId(),
                repository.getGithubRepoId(),
                repository.getOwner(),
                repository.getName(),
                repository.getFullName(),
                repository.getUrl(),
                repository.getDescription(),
                repository.isPrivate(),
                repository.getDefaultBranch()
        );
    }
}
