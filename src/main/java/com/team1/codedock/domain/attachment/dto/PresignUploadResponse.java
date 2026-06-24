package com.team1.codedock.domain.attachment.dto;

/**
 * presigned 업로드 정보.
 * - uploadUrl: 브라우저가 PUT으로 파일을 직접 올릴 S3 presigned URL(만료 있음)
 * - fileUrl: 업로드 후 실제로 접근/표시할 공개 URL(CloudFront 등)
 * - key: S3 객체 키
 * - contentType: 업로드 시 사용해야 하는 Content-Type(presign에 포함됨)
 */
public record PresignUploadResponse(
        String uploadUrl,
        String fileUrl,
        String key,
        String contentType
) {
}
