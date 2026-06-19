package com.team1.codedock.domain.github.dto;

public record GithubWebhookRegisterResponse(
        Long repositoryId,
        String webhookId,
        String webhookUrl,
        boolean active
) {}
