package com.team1.codedock.domain.workspace.service;

import org.springframework.web.multipart.MultipartFile;

public interface WorkspaceLogoStorageService {

    String storeWorkspaceLogo(Long workspaceId, MultipartFile file);
}
