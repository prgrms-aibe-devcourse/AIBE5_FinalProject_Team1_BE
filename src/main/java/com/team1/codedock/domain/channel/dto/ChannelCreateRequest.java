package com.team1.codedock.domain.channel.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ChannelCreateRequest(
        @NotBlank(message = "채널 이름은 필수입니다.")
        @Size(max = 120, message = "채널 이름은 120자 이하로 입력해주세요.")
        String name,

        String description
) {
}
