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

    @NotBlank(message = "워크스페이스 이름은 필수입니다.")
    @Size(max = 100, message = "워크스페이스 이름은 100자 이하로 입력해주세요.")
    private String name;

    @NotBlank(message = "워크스페이스 slug는 필수입니다.")
    @Size(max = 120, message = "워크스페이스 slug는 120자 이하로 입력해주세요.")
    private String slug;

    private String description;
}
