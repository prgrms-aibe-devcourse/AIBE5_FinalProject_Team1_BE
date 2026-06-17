package com.team1.codedock.domain.chat.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

public record ThreadAttachmentListRequest(
        @NotEmpty(message = "첨부파일은 최소 1개 이상 필요합니다.")
        @Size(max = 10, message = "첨부파일은 한 번에 10개 이하로 추가할 수 있습니다.")
        List<@NotNull(message = "첨부파일 정보는 비어 있을 수 없습니다.") @Valid ThreadAttachmentRequest> attachments
) {
}
