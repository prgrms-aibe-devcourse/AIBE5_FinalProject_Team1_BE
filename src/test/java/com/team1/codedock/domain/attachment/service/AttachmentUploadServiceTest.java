package com.team1.codedock.domain.attachment.service;

import com.team1.codedock.domain.attachment.dto.PresignUploadRequest;
import com.team1.codedock.global.exception.BusinessException;
import com.team1.codedock.global.exception.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * presign의 검증 분기(S3Presigner 호출 이전)만 단위 테스트한다.
 * 성공 경로는 실제 자격증명/네트워크가 필요하므로 제외한다.
 */
class AttachmentUploadServiceTest {

    private AttachmentUploadService enabledService() {
        // enabled=true, 버킷/공개URL 설정됨, maxBytes=1000
        return new AttachmentUploadService(true, "team1-codedock-web", "https://cdn.example.com", "uploads", "ap-northeast-2", 1000L);
    }

    @Test
    @DisplayName("업로드가 비활성화면 presign이 거부된다")
    void rejectsWhenDisabled() {
        AttachmentUploadService service =
                new AttachmentUploadService(false, "", "", "uploads", "ap-northeast-2", 1000L);

        assertThatThrownBy(() -> service.presign(new PresignUploadRequest("a.png", "image/png", 100L)))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INTERNAL_SERVER_ERROR);
    }

    @Test
    @DisplayName("파일 크기가 0 이하이면 거부된다")
    void rejectsNonPositiveSize() {
        assertThatThrownBy(() -> enabledService().presign(new PresignUploadRequest("a.png", "image/png", 0L)))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INVALID_INPUT);
    }

    @Test
    @DisplayName("파일 크기가 최대치를 넘으면 거부된다")
    void rejectsTooLargeSize() {
        assertThatThrownBy(() -> enabledService().presign(new PresignUploadRequest("a.png", "image/png", 2000L)))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INVALID_INPUT);
    }

    @Test
    @DisplayName("위험한 콘텐츠 타입(html/svg 등)은 거부된다")
    void rejectsBlockedContentType() {
        assertThatThrownBy(() -> enabledService().presign(new PresignUploadRequest("x.html", "text/html; charset=utf-8", 100L)))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INVALID_INPUT);

        assertThatThrownBy(() -> enabledService().presign(new PresignUploadRequest("x.svg", "image/svg+xml", 100L)))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INVALID_INPUT);
    }

    @Test
    @DisplayName("파일 이름이 비어 있으면 거부된다")
    void rejectsBlankFileName() {
        assertThatThrownBy(() -> enabledService().presign(new PresignUploadRequest("  ", "image/png", 100L)))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INVALID_INPUT);
    }
}
