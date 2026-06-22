package com.team1.codedock.domain.channel.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public record ChannelOrderUpdateRequest(
        @NotEmpty(message = "채널 순서 목록은 비어 있을 수 없습니다.")
        List<@NotNull(message = "채널 id는 필수입니다.") Long> channelIds
) {
}
