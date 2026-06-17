package com.team1.codedock.domain.github.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class GithubConnectResponse {
    private Long id;
    private Long channelId;
    private String owner;
    private String name;
    private String fullName;
    private String url;
    private String defaultBranch;
    private boolean isPrivate;
}
