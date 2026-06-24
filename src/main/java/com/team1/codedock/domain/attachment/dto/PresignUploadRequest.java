package com.team1.codedock.domain.attachment.dto;

/**
 * 업로드할 파일의 메타데이터. 브라우저가 S3로 직접 PUT 하기 전에 presigned URL을 요청할 때 사용.
 */
public record PresignUploadRequest(
        String fileName,
        String contentType
) {
}
