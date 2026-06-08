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

    @NotBlank
    @Pattern(
            regexp = "admin|editor|viewer",
            message = "role must be one of: admin, editor, viewer"
    )
    private String role;
}