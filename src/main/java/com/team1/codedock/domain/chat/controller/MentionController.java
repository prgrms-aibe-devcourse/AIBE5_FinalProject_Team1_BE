package com.team1.codedock.domain.chat.controller;

import com.team1.codedock.domain.chat.dto.ChatEventResponse;
import com.team1.codedock.domain.chat.dto.ChatEventType;
import com.team1.codedock.domain.chat.dto.MentionResponse;
import com.team1.codedock.domain.chat.service.MentionService;
import com.team1.codedock.global.response.ApiResponse;
import com.team1.codedock.global.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
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

    private static final String PERSONAL_NOTIFICATION_DESTINATION = "/queue/notifications";

    private final MentionService mentionService;
    private final SimpMessagingTemplate messagingTemplate;

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
    public ApiResponse<MentionResponse> deleteMention(
            @PathVariable Long mentionId
    ) {
        MentionResponse response = mentionService.deleteMention(mentionId, SecurityUtils.getCurrentUserId());
        ChatEventResponse<MentionResponse> event = ChatEventResponse.of(ChatEventType.MENTION_DELETED, response);

        // 멘션 삭제는 개인 알림 목록 동기화라 현재 사용자의 여러 세션에만 전송함
        messagingTemplate.convertAndSendToUser(
                SecurityUtils.getCurrentUserDestinationKey(),
                PERSONAL_NOTIFICATION_DESTINATION,
                event
        );
        return ApiResponse.ok(response);
    }
}
