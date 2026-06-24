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
import java.util.UUID;

/**
 * S3 presigned PUT URL을 발급한다. 브라우저가 이 URL로 파일을 직접 업로드하고,
 * 업로드 후에는 공개 URL(fileUrl)을 메시지 첨부로 저장한다. (서버는 바이너리를 거치지 않음)
 */
@Service
public class AttachmentUploadService {

    private static final Duration PRESIGN_TTL = Duration.ofMinutes(5);

    private final boolean enabled;
    private final String bucket;
    private final String publicBaseUrl;
    private final String keyPrefix;
    private final Region region;

    // presigner는 자격증명 체인을 사용하므로, 설정이 켜졌을 때만 지연 생성한다(미설정 시 부팅 영향 없음).
    private volatile S3Presigner presigner;

    public AttachmentUploadService(
            @Value("${app.upload.enabled:false}") boolean enabled,
            @Value("${app.upload.s3-bucket:}") String bucket,
            @Value("${app.upload.public-base-url:}") String publicBaseUrl,
            @Value("${app.upload.key-prefix:uploads}") String keyPrefix,
            @Value("${app.upload.region:ap-northeast-2}") String region
    ) {
        this.enabled = enabled;
        this.bucket = bucket == null ? "" : bucket.trim();
        this.publicBaseUrl = publicBaseUrl == null ? "" : publicBaseUrl.trim().replaceAll("/+$", "");
        this.keyPrefix = (keyPrefix == null || keyPrefix.isBlank()) ? "uploads" : keyPrefix.trim().replaceAll("(^/+)|(/+$)", "");
        this.region = Region.of((region == null || region.isBlank()) ? "ap-northeast-2" : region.trim());
    }

    public PresignUploadResponse presign(PresignUploadRequest request) {
        if (!enabled || bucket.isBlank() || publicBaseUrl.isBlank()) {
            throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR, "파일 업로드가 설정되지 않았습니다.");
        }
        if (request == null || request.fileName() == null || request.fileName().isBlank()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "파일 이름이 필요합니다.");
        }

        String contentType = (request.contentType() == null || request.contentType().isBlank())
                ? "application/octet-stream"
                : request.contentType().trim();

        String key = keyPrefix + "/" + UUID.randomUUID() + "/" + sanitizeFileName(request.fileName());

        PutObjectRequest objectRequest = PutObjectRequest.builder()
                .bucket(bucket)
                .key(key)
                .contentType(contentType)
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
