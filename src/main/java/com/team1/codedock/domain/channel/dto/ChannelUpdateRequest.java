package com.team1.codedock.domain.channel.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ChannelUpdateRequest(
        @NotBlank(message = "Channel name must not be blank.")
        @Size(max = 120, message = "Channel name must be 120 characters or less.")
        String name,

        String description
) {
}
