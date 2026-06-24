package com.team1.codedock.domain.attachment.dto;

/**
 * 업로드할 파일의 메타데이터. 브라우저가 S3로 직접 PUT 하기 전에 presigned URL을 요청할 때 사용.
 * fileSize는 서명에 content-length로 포함되어, 클라이언트가 다른 크기를 올리지 못하게 한다.
 */
public record PresignUploadRequest(
        String fileName,
        String contentType,
        long fileSize
) {
}
