package com.team1.codedock.domain.chat.controller;

import com.team1.codedock.domain.chat.dto.MentionResponse;
import com.team1.codedock.domain.chat.service.MentionService;
import com.team1.codedock.global.response.ApiResponse;
import com.team1.codedock.global.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api")
public class MentionController {

    private final MentionService mentionService;

    @GetMapping("/workspaces/{workspaceId}/mentions")
    public ApiResponse<List<MentionResponse>> getMyMentions(
            @PathVariable Long workspaceId
    ) {
        return ApiResponse.ok(mentionService.getMyMentions(workspaceId, SecurityUtils.getCurrentUserId()));
    }

    @PatchMapping("/mentions/{mentionId}/read")
    public ApiResponse<MentionResponse> markMentionAsRead(
            @PathVariable Long mentionId
    ) {
        return ApiResponse.ok(mentionService.markMentionAsRead(mentionId, SecurityUtils.getCurrentUserId()));
    }

    @DeleteMapping("/mentions/{mentionId}")
    public ApiResponse<Void> deleteMention(
            @PathVariable Long mentionId
    ) {
        mentionService.deleteMention(mentionId, SecurityUtils.getCurrentUserId());
        return ApiResponse.ok();
    }
}
