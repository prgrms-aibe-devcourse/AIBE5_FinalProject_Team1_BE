package com.team1.codedock.domain.github.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class GithubRepoResponse {
    private long id;
    private String name;
    private String fullName;
    private String owner;
    private boolean isPrivate;
    private String language;
    private String htmlUrl;
    private String defaultBranch;
    private String relation; // "owner" | "collaborator"
}
