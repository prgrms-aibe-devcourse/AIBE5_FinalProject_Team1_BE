package com.team1.codedock.domain.workspace.dto;

import jakarta.validation.constraints.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class InviteCreateRequest {

    @NotBlank
    @Email
    private String email;

    @NotBlank
    @Pattern(
            regexp = "admin|editor|viewer",
            message = "role must be one of: admin, editor, viewer"
    )
    private String role;

    @NotNull
    @Min(1)
    private Integer expiresInHours;
}