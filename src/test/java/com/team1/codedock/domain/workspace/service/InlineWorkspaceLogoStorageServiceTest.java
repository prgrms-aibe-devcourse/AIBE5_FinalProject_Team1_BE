package com.team1.codedock.domain.workspace.service;

import com.team1.codedock.global.exception.BusinessException;
import com.team1.codedock.global.exception.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class InlineWorkspaceLogoStorageServiceTest {

    private final InlineWorkspaceLogoStorageService storageService = new InlineWorkspaceLogoStorageService();

    @Test
    @DisplayName("이미지 파일을 data URL 형태의 임시 logoUrl로 변환한다")
    void storeWorkspaceLogo() {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "logo.png",
                "image/png",
                new byte[]{1, 2, 3}
        );

        String logoUrl = storageService.storeWorkspaceLogo(10L, file);

        assertThat(logoUrl).isEqualTo("data:image/png;base64,AQID");
    }

    @Test
    @DisplayName("빈 로고 파일은 거부한다")
    void storeWorkspaceLogoWithEmptyFile() {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "logo.png",
                "image/png",
                new byte[0]
        );

        assertThatThrownBy(() -> storageService.storeWorkspaceLogo(10L, file))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INVALID_INPUT);
    }

    @Test
    @DisplayName("이미지가 아닌 로고 파일은 거부한다")
    void storeWorkspaceLogoWithInvalidContentType() {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "logo.txt",
                "text/plain",
                new byte[]{1, 2, 3}
        );

        assertThatThrownBy(() -> storageService.storeWorkspaceLogo(10L, file))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INVALID_INPUT);
    }

    @Test
    @DisplayName("1MB를 초과하는 로고 파일은 거부한다")
    void storeWorkspaceLogoWithTooLargeFile() {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "logo.png",
                "image/png",
                new byte[1024 * 1024 + 1]
        );

        assertThatThrownBy(() -> storageService.storeWorkspaceLogo(10L, file))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INVALID_INPUT);
    }
}
