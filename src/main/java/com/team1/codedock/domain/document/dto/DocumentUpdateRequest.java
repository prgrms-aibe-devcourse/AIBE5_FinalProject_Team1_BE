package com.team1.codedock.domain.document.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record DocumentUpdateRequest(
        @NotBlank @Size(max = 255) String title,
        String content,
        // 'workspace' | 'private' | 'public'
        String visibility
) {}
