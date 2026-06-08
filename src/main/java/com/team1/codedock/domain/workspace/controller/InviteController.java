package com.team1.codedock.domain.workspace.controller;

import com.team1.codedock.domain.workspace.service.WorkspaceService;
import com.team1.codedock.global.response.ApiResponse;
import com.team1.codedock.global.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/invites")
@RequiredArgsConstructor
public class InviteController {

    private final WorkspaceService workspaceService;

    @PostMapping("/{inviteToken}/accept")
    public ApiResponse<Void> acceptInvite(@PathVariable String inviteToken) {
        workspaceService.acceptInvite(inviteToken, SecurityUtils.getCurrentUserId());
        return ApiResponse.ok();
    }
}