package com.team1.codedock.domain.workspace.controller;

import com.team1.codedock.domain.workspace.dto.*;
import com.team1.codedock.domain.workspace.service.WorkspaceService;
import com.team1.codedock.global.response.ApiResponse;
import com.team1.codedock.global.security.SecurityUtils;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/v1/workspaces")
@RequiredArgsConstructor
public class WorkspaceController {

    private final WorkspaceService workspaceService;

    @PostMapping
    public ApiResponse<WorkspaceCreateResponse> createWorkspace(
            @RequestBody @Valid WorkspaceCreateRequest request) {
        return ApiResponse.ok(workspaceService.createWorkspace(request, SecurityUtils.getCurrentUserId()));
    }

    @GetMapping
    public ApiResponse<List<WorkspaceResponse>> getMyWorkspaces() {
        return ApiResponse.ok(workspaceService.getMyWorkspaces(SecurityUtils.getCurrentUserId()));
    }

    @GetMapping("/{workspaceId}")
    public ApiResponse<WorkspaceResponse> getWorkspace(@PathVariable Long workspaceId) {
        return ApiResponse.ok(workspaceService.getWorkspace(workspaceId, SecurityUtils.getCurrentUserId()));
    }

    @PatchMapping("/{workspaceId}")
    public ApiResponse<WorkspaceResponse> updateWorkspace(
            @PathVariable Long workspaceId,
            @RequestBody @Valid WorkspaceUpdateRequest request) {
        return ApiResponse.ok(workspaceService.updateWorkspace(workspaceId, request, SecurityUtils.getCurrentUserId()));
    }

    @PostMapping(value = "/{workspaceId}/logo", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<WorkspaceResponse> updateWorkspaceLogo(
            @PathVariable Long workspaceId,
            @RequestPart("file") MultipartFile file) {
        return ApiResponse.ok(workspaceService.updateWorkspaceLogo(
                workspaceId,
                file,
                SecurityUtils.getCurrentUserId()
        ));
    }

    @GetMapping("/{workspaceId}/members")
    public ApiResponse<List<WorkspaceMemberResponse>> getMembers(@PathVariable Long workspaceId) {
        return ApiResponse.ok(workspaceService.getMembers(workspaceId, SecurityUtils.getCurrentUserId()));
    }

    @PostMapping("/{workspaceId}/invites")
    public ApiResponse<InviteResponse> createInvite(
            @PathVariable Long workspaceId,
            @RequestBody @Valid InviteCreateRequest request) {
        return ApiResponse.ok(workspaceService.createInvite(workspaceId, request, SecurityUtils.getCurrentUserId()));
    }

    @GetMapping("/{workspaceId}/invites")
    public ApiResponse<List<InvitationResponse>> listInvitations(@PathVariable Long workspaceId) {
        return ApiResponse.ok(workspaceService.listInvitations(workspaceId, SecurityUtils.getCurrentUserId()));
    }

    @DeleteMapping("/{workspaceId}/invites/{invitationId}")
    public ApiResponse<Void> revokeInvitation(
            @PathVariable Long workspaceId,
            @PathVariable Long invitationId) {
        workspaceService.revokeInvitation(workspaceId, invitationId, SecurityUtils.getCurrentUserId());
        return ApiResponse.ok();
    }

    @PatchMapping("/{workspaceId}/members/{memberId}/role")
    public ApiResponse<Void> changeMemberRole(
            @PathVariable Long workspaceId,
            @PathVariable Long memberId,
            @RequestBody @Valid ChangeMemberRoleRequest request) {
        workspaceService.changeMemberRole(workspaceId, memberId, request.getRole(), SecurityUtils.getCurrentUserId());
        return ApiResponse.ok();
    }

    @DeleteMapping("/{workspaceId}")
    public ApiResponse<Void> deleteWorkspace(@PathVariable Long workspaceId) {
        workspaceService.deleteWorkspace(workspaceId, SecurityUtils.getCurrentUserId());
        return ApiResponse.ok();
    }

    @DeleteMapping("/{workspaceId}/members/{memberId}")
    public ApiResponse<Void> removeMember(
            @PathVariable Long workspaceId,
            @PathVariable Long memberId) {
        workspaceService.removeMember(workspaceId, memberId, SecurityUtils.getCurrentUserId());
        return ApiResponse.ok();
    }

    @DeleteMapping("/{workspaceId}/leave")
    public ApiResponse<Void> leaveWorkspace(@PathVariable Long workspaceId) {
        workspaceService.leaveWorkspace(workspaceId, SecurityUtils.getCurrentUserId());
        return ApiResponse.ok();
    }

    @PostMapping("/{workspaceId}/members/{memberId}/transfer-ownership")
    public ApiResponse<Void> transferOwnership(
            @PathVariable Long workspaceId,
            @PathVariable Long memberId) {
        workspaceService.transferOwnership(workspaceId, memberId, SecurityUtils.getCurrentUserId());
        return ApiResponse.ok();
    }

    @PatchMapping("/{workspaceId}/me/presence")
    public ApiResponse<Void> updatePresence(
            @PathVariable Long workspaceId,
            @RequestBody java.util.Map<String, String> body) {
        workspaceService.updatePresence(workspaceId, body.get("presence"), SecurityUtils.getCurrentUserId());
        return ApiResponse.ok();
    }
}
