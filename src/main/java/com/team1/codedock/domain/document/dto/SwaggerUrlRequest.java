package com.team1.codedock.domain.document.dto;

import jakarta.validation.constraints.NotBlank;

public record SwaggerUrlRequest(@NotBlank String swaggerUrl) {}
