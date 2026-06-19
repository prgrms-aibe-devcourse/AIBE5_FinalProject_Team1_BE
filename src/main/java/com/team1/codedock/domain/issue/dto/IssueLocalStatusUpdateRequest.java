package com.team1.codedock.domain.issue.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record IssueLocalStatusUpdateRequest(
        @NotBlank
        @Pattern(regexp = "todo|in_progress|review|done|blocked", message = "localStatus는 todo, in_progress, review, done, blocked 중 하나여야 합니다.")
        String localStatus
) {}
