package com.team1.codedock.domain.workspace.service;

import com.team1.codedock.global.exception.BusinessException;
import com.team1.codedock.global.exception.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

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
    @DisplayName("콘텐츠 타입이 없는 로고 파일은 거부한다")
    void storeWorkspaceLogoWithNullContentType() {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "logo.png",
                null,
                new byte[]{1, 2, 3}
        );

        assertThatThrownBy(() -> storageService.storeWorkspaceLogo(10L, file))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INVALID_INPUT);
    }

    @Test
    @DisplayName("정확히 1MB인 로고 파일은 허용한다")
    void storeWorkspaceLogoWithExactMaxSize() {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "logo.png",
                "image/png",
                new byte[1024 * 1024]
        );

        String logoUrl = storageService.storeWorkspaceLogo(10L, file);

        assertThat(logoUrl).startsWith("data:image/png;base64,");
    }

    @Test
    @DisplayName("SVG 로고 파일은 허용한다")
    void storeWorkspaceLogoWithSvgFile() {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "logo.svg",
                "image/svg+xml",
                "<svg />".getBytes(java.nio.charset.StandardCharsets.UTF_8)
        );

        String logoUrl = storageService.storeWorkspaceLogo(10L, file);

        assertThat(logoUrl).startsWith("data:image/svg+xml;base64,");
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

    @Test
    @DisplayName("로고 파일 바이트를 읽지 못하면 서버 오류로 변환한다")
    void storeWorkspaceLogoWithReadFailure() throws IOException {
        MultipartFile file = mock(MultipartFile.class);
        when(file.isEmpty()).thenReturn(false);
        when(file.getSize()).thenReturn(3L);
        when(file.getContentType()).thenReturn("image/png");
        when(file.getBytes()).thenThrow(new IOException("read failed"));

        assertThatThrownBy(() -> storageService.storeWorkspaceLogo(10L, file))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INTERNAL_SERVER_ERROR);
    }
}
