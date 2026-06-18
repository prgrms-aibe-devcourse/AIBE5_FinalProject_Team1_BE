package com.team1.codedock.domain.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
public class UpdateSkillsRequest {

    @NotNull(message = "기술 목록은 필수입니다.")
    private List<
            @NotBlank(message = "기술 이름은 비어 있을 수 없습니다.")
            @Size(max = 100, message = "기술 이름은 100자 이하로 입력해주세요.")
            String> skills;
}
