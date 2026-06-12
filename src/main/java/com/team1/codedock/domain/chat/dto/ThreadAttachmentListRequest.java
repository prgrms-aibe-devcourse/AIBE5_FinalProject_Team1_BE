package com.team1.codedock.domain.chat.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

public record ThreadAttachmentListRequest(
        @NotEmpty
        @Size(max = 10)
        List<@NotNull @Valid ThreadAttachmentRequest> attachments
) {
}
