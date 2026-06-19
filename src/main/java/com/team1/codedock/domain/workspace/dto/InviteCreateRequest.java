package com.team1.codedock.domain.workspace.dto;

import jakarta.validation.constraints.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class InviteCreateRequest {

    @NotBlank(message = "초대 이메일은 필수입니다.")
    @Email(message = "올바른 이메일 형식이 아닙니다.")
    private String email;

    @NotBlank(message = "역할은 필수입니다.")
    @Pattern(
            regexp = "admin|editor|viewer",
            message = "역할은 admin, editor, viewer 중 하나여야 합니다."
    )
    private String role;

    @Size(max = 100, message = "직무는 100자 이하여야 합니다.")
    private String position;

    @NotNull(message = "초대 만료 시간은 필수입니다.")
    @Min(value = 1, message = "초대 만료 시간은 1시간 이상이어야 합니다.")
    private Integer expiresInHours;
}
