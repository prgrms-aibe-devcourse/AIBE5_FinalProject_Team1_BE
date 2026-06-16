package com.team1.codedock.domain.workspace.dto;

import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class WorkspaceUpdateRequest {

    @Size(min = 1, max = 100)
    private String name;

    private String description;

    private String logoUrl;
}