package com.team1.codedock.domain.attachment.controller;

import com.team1.codedock.domain.attachment.dto.PresignUploadRequest;
import com.team1.codedock.domain.attachment.dto.PresignUploadResponse;
import com.team1.codedock.domain.attachment.service.AttachmentUploadService;
import com.team1.codedock.global.response.ApiResponse;
import com.team1.codedock.global.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api")
public class AttachmentUploadController {

    private final AttachmentUploadService attachmentUploadService;

    // 인증된 사용자에게 S3 presigned PUT URL을 발급한다.
    @PostMapping("/attachments/presign")
    public ApiResponse<PresignUploadResponse> presign(@RequestBody PresignUploadRequest request) {
        SecurityUtils.getCurrentUserId(); // 인증 확인(미인증 시 예외)
        return ApiResponse.ok(attachmentUploadService.presign(request));
    }
}
