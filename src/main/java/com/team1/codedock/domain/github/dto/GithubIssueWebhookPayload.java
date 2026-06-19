package com.team1.codedock.domain.github.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record GithubIssueWebhookPayload(
        String action,
        IssueDto issue,
        RepositoryDto repository
) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record IssueDto(
            long id,
            int number,
            String title,
            String body,
            String state,
            @JsonProperty("html_url") String htmlUrl,
            UserDto user,
            List<LabelDto> labels,
            List<UserDto> assignees,
            @JsonProperty("created_at") Instant createdAt,
            @JsonProperty("updated_at") Instant updatedAt,
            @JsonProperty("closed_at") Instant closedAt
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record UserDto(String login) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record LabelDto(String name, String color) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record RepositoryDto(
            long id,
            String name,
            @JsonProperty("full_name") String fullName
    ) {}
}
