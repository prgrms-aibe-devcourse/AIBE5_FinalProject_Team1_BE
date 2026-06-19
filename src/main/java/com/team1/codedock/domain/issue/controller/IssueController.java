package com.team1.codedock.domain.issue.controller;

import com.team1.codedock.domain.issue.dto.IssueLocalStatusUpdateRequest;
import com.team1.codedock.domain.issue.dto.IssueResponse;
import com.team1.codedock.domain.issue.service.IssueService;
import com.team1.codedock.global.response.ApiResponse;
import com.team1.codedock.global.security.SecurityUtils;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping({
        "/api/workspaces/{workspaceId}/issues",
        "/api/v1/workspaces/{workspaceId}/issues"
})
public class IssueController {

    private final IssueService issueService;

    @GetMapping
    public ApiResponse<List<IssueResponse>> getIssues(@PathVariable Long workspaceId) {
        return ApiResponse.ok(issueService.getWorkspaceIssues(workspaceId, SecurityUtils.getCurrentUserId()));
    }

    @GetMapping("/{issueId}")
    public ApiResponse<IssueResponse> getIssue(
            @PathVariable Long workspaceId,
            @PathVariable Long issueId
    ) {
        return ApiResponse.ok(issueService.getIssue(workspaceId, issueId, SecurityUtils.getCurrentUserId()));
    }

    @PatchMapping("/{issueId}/local-status")
    public ApiResponse<IssueResponse> updateLocalStatus(
            @PathVariable Long workspaceId,
            @PathVariable Long issueId,
            @RequestBody @Valid IssueLocalStatusUpdateRequest request
    ) {
        return ApiResponse.ok(issueService.updateLocalStatus(workspaceId, issueId, SecurityUtils.getCurrentUserId(), request));
    }
}
