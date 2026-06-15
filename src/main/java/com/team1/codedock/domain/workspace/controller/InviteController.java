package com.team1.codedock.domain.workspace.controller;

import com.team1.codedock.domain.workspace.service.WorkspaceService;
import com.team1.codedock.global.response.ApiResponse;
import com.team1.codedock.global.security.SecurityUtils;
import com.team1.codedock.domain.workspace.dto.ReceivedInviteResponse;
import java.util.List;
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

    @PostMapping("/{inviteToken}/reject")
    public ApiResponse<Void> rejectInvite(@PathVariable String inviteToken) {
        workspaceService.rejectInvite(inviteToken, SecurityUtils.getCurrentUserId());
        return ApiResponse.ok();
    }

    @GetMapping("/received")
    public ApiResponse<List<ReceivedInviteResponse>> receivedInvites() {
        return ApiResponse.ok(workspaceService.listReceivedInvites(SecurityUtils.getCurrentUserId()));
    }
}