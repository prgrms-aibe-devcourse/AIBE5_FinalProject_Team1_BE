package com.team1.codedock.domain.document.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record DocumentCreateRequest(
        @NotNull Long createdByMemberId,
        @NotBlank @Size(max = 255) String title,
        String content,
        // 'manual' | 'faq' | 'release'
        String category,
        // 'workspace' | 'private' | 'public'
        String visibility,
        Long relatedPrId
) {}
