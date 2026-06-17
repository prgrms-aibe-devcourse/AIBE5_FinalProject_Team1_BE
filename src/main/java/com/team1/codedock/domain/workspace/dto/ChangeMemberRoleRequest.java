package com.team1.codedock.domain.workspace.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class ChangeMemberRoleRequest {

    @NotBlank(message = "역할은 필수입니다.")
    @Pattern(
            regexp = "admin|editor|viewer",
            message = "역할은 admin, editor, viewer 중 하나여야 합니다."
    )
    private String role;
}
