package com.team1.codedock.domain.workspace.service;

import com.team1.codedock.global.exception.BusinessException;
import com.team1.codedock.global.exception.ErrorCode;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Base64;
import java.util.Set;

@Service
public class InlineWorkspaceLogoStorageService implements WorkspaceLogoStorageService {

    private static final long MAX_LOGO_BYTES = 1024 * 1024;
    private static final Set<String> ALLOWED_CONTENT_TYPES = Set.of(
            "image/png",
            "image/jpeg",
            "image/webp",
            "image/gif"
    );

    @Override
    public String storeWorkspaceLogo(Long workspaceId, MultipartFile file) {
        validateLogoFile(file);

        try {
            String encoded = Base64.getEncoder().encodeToString(file.getBytes());
            return "data:" + file.getContentType() + ";base64," + encoded;
        } catch (IOException e) {
            throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR, "워크스페이스 로고 파일을 읽을 수 없습니다.");
        }
    }

    private void validateLogoFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "워크스페이스 로고 파일은 필수입니다.");
        }
        if (file.getSize() > MAX_LOGO_BYTES) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "워크스페이스 로고 파일은 1MB 이하만 업로드할 수 있습니다.");
        }
        String contentType = file.getContentType();
        if (contentType == null || !ALLOWED_CONTENT_TYPES.contains(contentType)) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "워크스페이스 로고는 이미지 파일만 업로드할 수 있습니다.");
        }
    }
}
