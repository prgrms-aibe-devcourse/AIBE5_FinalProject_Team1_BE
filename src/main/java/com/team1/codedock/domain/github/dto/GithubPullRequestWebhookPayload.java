package com.team1.codedock.domain.github.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record GithubPullRequestWebhookPayload(
        String action,
        @JsonProperty("pull_request") PullRequestDto pullRequest,
        RepositoryDto repository
) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record PullRequestDto(
            long id,
            int number,
            String title,
            String body,
            String state,
            @JsonProperty("html_url") String htmlUrl,
            UserDto user,
            List<LabelDto> labels,
            @JsonProperty("requested_reviewers") List<UserDto> requestedReviewers,
            HeadDto head,
            BaseDto base,
            Integer additions,
            Integer deletions,
            @JsonProperty("changed_files") Integer changedFiles,
            Boolean merged,
            @JsonProperty("merged_at") Instant mergedAt,
            @JsonProperty("created_at") Instant createdAt,
            @JsonProperty("updated_at") Instant updatedAt
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record UserDto(String login) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record LabelDto(String name, String color) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record HeadDto(String ref) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record BaseDto(String ref) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record RepositoryDto(
            long id,
            String name,
            @JsonProperty("full_name") String fullName
    ) {}
}
