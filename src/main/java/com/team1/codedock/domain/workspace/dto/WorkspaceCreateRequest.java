package com.team1.codedock.domain.workspace.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class WorkspaceCreateRequest {

    @NotBlank
    @Size(max = 100)
    private String name;

    @NotBlank
    @Size(max = 120)
    private String slug;

    private String description;
}