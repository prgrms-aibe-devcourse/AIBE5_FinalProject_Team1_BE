package com.team1.codedock.domain.chat.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

public record ChannelMessageRestCreateRequest(
        @NotBlank(message = "메시지 내용은 필수입니다.")
        @Size(max = 4000, message = "메시지 내용은 4000자 이하로 입력해주세요.")
        String content,

        @Size(max = 10, message = "첨부파일은 한 번에 10개 이하로 추가할 수 있습니다.")
        List<@NotNull(message = "첨부파일 정보는 비어 있을 수 없습니다.") @Valid ThreadAttachmentRequest> attachments
) {

    public ChannelMessageRestCreateRequest(String content) {
        this(content, List.of());
    }
}
