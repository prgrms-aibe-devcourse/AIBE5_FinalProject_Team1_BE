package com.team1.codedock.domain.attachment.service;

import com.team1.codedock.domain.attachment.dto.PresignUploadRequest;
import com.team1.codedock.domain.attachment.dto.PresignUploadResponse;
import com.team1.codedock.global.exception.BusinessException;
import com.team1.codedock.global.exception.ErrorCode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

import java.time.Duration;
import java.util.Set;
import java.util.UUID;

/**
 * S3 presigned PUT URL을 발급한다. 브라우저가 이 URL로 파일을 직접 업로드하고,
 * 업로드 후에는 공개 URL(fileUrl)을 메시지 첨부로 저장한다. (서버는 바이너리를 거치지 않음)
 */
@Service
public class AttachmentUploadService {

    private static final Duration PRESIGN_TTL = Duration.ofMinutes(5);

    // 업로드 객체가 앱과 같은 도메인(CloudFront)에서 서빙되므로, 브라우저가 스크립트를 실행할 수 있는
    // 타입은 거부한다(같은 출처 XSS 방지). 이미지/문서/일반 파일은 허용.
    private static final Set<String> BLOCKED_CONTENT_TYPES = Set.of(
            "text/html",
            "application/xhtml+xml",
            "image/svg+xml",
            "text/javascript",
            "application/javascript",
            "application/x-msdownload"
    );

    private final boolean enabled;
    private final String bucket;
    private final String publicBaseUrl;
    private final String keyPrefix;
    private final Region region;
    private final long maxBytes;

    // presigner는 자격증명 체인을 사용하므로, 설정이 켜졌을 때만 지연 생성한다(미설정 시 부팅 영향 없음).
    private volatile S3Presigner presigner;

    public AttachmentUploadService(
            @Value("${app.upload.enabled:false}") boolean enabled,
            @Value("${app.upload.s3-bucket:}") String bucket,
            @Value("${app.upload.public-base-url:}") String publicBaseUrl,
            @Value("${app.upload.key-prefix:uploads}") String keyPrefix,
            @Value("${app.upload.region:ap-northeast-2}") String region,
            @Value("${app.upload.max-bytes:10485760}") long maxBytes
    ) {
        this.enabled = enabled;
        this.bucket = bucket == null ? "" : bucket.trim();
        this.publicBaseUrl = publicBaseUrl == null ? "" : publicBaseUrl.trim().replaceAll("/+$", "");
        this.keyPrefix = (keyPrefix == null || keyPrefix.isBlank()) ? "uploads" : keyPrefix.trim().replaceAll("(^/+)|(/+$)", "");
        this.region = Region.of((region == null || region.isBlank()) ? "ap-northeast-2" : region.trim());
        this.maxBytes = maxBytes > 0 ? maxBytes : 10_485_760L;
    }

    public PresignUploadResponse presign(PresignUploadRequest request) {
        if (!enabled || bucket.isBlank() || publicBaseUrl.isBlank()) {
            throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR, "파일 업로드가 설정되지 않았습니다.");
        }
        if (request == null || request.fileName() == null || request.fileName().isBlank()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "파일 이름이 필요합니다.");
        }
        // 파일 크기 검증: presign 단계에서 막아 S3에 큰 파일이 올라가지 않게 함
        if (request.fileSize() <= 0) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "파일 크기가 올바르지 않습니다.");
        }
        if (request.fileSize() > maxBytes) {
            throw new BusinessException(ErrorCode.INVALID_INPUT,
                    "파일이 너무 큽니다. 최대 " + (maxBytes / (1024 * 1024)) + "MB까지 업로드할 수 있습니다.");
        }

        String contentType = (request.contentType() == null || request.contentType().isBlank())
                ? "application/octet-stream"
                : request.contentType().trim();
        // "text/html; charset=utf-8" 같은 형태도 앞부분만 보고 차단
        String baseContentType = contentType.split(";", 2)[0].trim().toLowerCase();
        if (BLOCKED_CONTENT_TYPES.contains(baseContentType)) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "해당 형식의 파일은 업로드할 수 없습니다.");
        }

        String key = keyPrefix + "/" + UUID.randomUUID() + "/" + sanitizeFileName(request.fileName());

        // contentLength를 서명에 포함하면, 클라이언트는 정확히 이 크기로만 PUT 할 수 있다(우회 불가).
        PutObjectRequest objectRequest = PutObjectRequest.builder()
                .bucket(bucket)
                .key(key)
                .contentType(contentType)
                .contentLength(request.fileSize())
                .build();

        PutObjectPresignRequest presignRequest = PutObjectPresignRequest.builder()
                .signatureDuration(PRESIGN_TTL)
                .putObjectRequest(objectRequest)
                .build();

        PresignedPutObjectRequest presigned = presigner().presignPutObject(presignRequest);

        String fileUrl = publicBaseUrl + "/" + key;
        return new PresignUploadResponse(presigned.url().toString(), fileUrl, key, contentType);
    }

    private S3Presigner presigner() {
        S3Presigner local = presigner;
        if (local == null) {
            synchronized (this) {
                local = presigner;
                if (local == null) {
                    // 자격증명은 기본 체인(환경변수 AWS_ACCESS_KEY_ID/AWS_SECRET_ACCESS_KEY 등)에서 해석
                    local = S3Presigner.builder().region(region).build();
                    presigner = local;
                }
            }
        }
        return local;
    }

    // 키에 안전한 파일명만 남김(경로 조작/공백 방지)
    private String sanitizeFileName(String fileName) {
        String name = fileName.replace("\\", "/");
        name = name.substring(name.lastIndexOf('/') + 1);
        name = name.replaceAll("[^a-zA-Z0-9._-]", "_");
        if (name.isBlank()) {
            name = "file";
        }
        return name.length() > 120 ? name.substring(name.length() - 120) : name;
    }
}
