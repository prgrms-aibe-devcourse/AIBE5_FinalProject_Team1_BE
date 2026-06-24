package com.team1.codedock.domain.github.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record GithubPullRequestReviewWebhookPayload(
        String action,
        ReviewDto review,
        @JsonProperty("pull_request") PullRequestDto pullRequest
) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ReviewDto(
            long id,
            UserDto user,
            String body,
            String state
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record PullRequestDto(
            long id,
            int number,
            String title,
            UserDto user
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record UserDto(String login) {}
}
