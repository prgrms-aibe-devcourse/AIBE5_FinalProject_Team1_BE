package com.team1.codedock.domain.github.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class GithubCollaboratorResponse {
    private String login;       // GitHub username
    private String avatarUrl;
    private String htmlUrl;
    private Long userId;        // null if not registered on our platform
    private String email;       // null if not registered
    private String displayName; // null if not registered
}
