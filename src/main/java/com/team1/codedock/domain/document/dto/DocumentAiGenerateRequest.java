package com.team1.codedock.domain.document.dto;

import jakarta.validation.constraints.NotBlank;

import java.time.LocalDate;

public record DocumentAiGenerateRequest(
        // 'manual' | 'faq' | 'release'
        @NotBlank String category,
        // manual·faq 전용: 작성할 주제 (null이면 전체 서비스 기준)
        String topic,
        // release 전용: 조회 시작일
        LocalDate startDate,
        // release 전용: 조회 종료일
        LocalDate endDate
) {}
