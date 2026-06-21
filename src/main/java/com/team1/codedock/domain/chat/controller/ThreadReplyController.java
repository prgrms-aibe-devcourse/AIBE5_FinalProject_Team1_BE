package com.team1.codedock.domain.chat.controller;

import com.team1.codedock.domain.chat.dto.ChatEventResponse;
import com.team1.codedock.domain.chat.dto.ChatEventType;
import com.team1.codedock.domain.chat.dto.ThreadReplyCreateRequest;
import com.team1.codedock.domain.chat.dto.ThreadReplyResponse;
import com.team1.codedock.domain.chat.dto.ThreadReplyUpdateRequest;
import com.team1.codedock.domain.chat.service.ThreadReplyService;
import com.team1.codedock.global.response.ApiResponse;
import com.team1.codedock.global.security.SecurityUtils;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/threads/{threadId}/replies")
public class ThreadReplyController {

    private final ThreadReplyService threadReplyService;
    private final SimpMessagingTemplate messagingTemplate;

    @GetMapping
    public ApiResponse<List<ThreadReplyResponse>> getReplies(
            @PathVariable Long threadId
    ) {
        return ApiResponse.ok(threadReplyService.getReplies(threadId, SecurityUtils.getCurrentUserId()));
    }

    @PostMapping
    public ApiResponse<ThreadReplyResponse> createReply(
            @PathVariable Long threadId,
            @Valid @RequestBody ThreadReplyCreateRequest request
    ) {
        ThreadReplyResponse response = threadReplyService.createReply(threadId, SecurityUtils.getCurrentUserId(), request);
        broadcastThreadEvent(threadId, ChatEventType.THREAD_REPLY_CREATED, response);
        return ApiResponse.ok(response);
    }

    @PatchMapping("/{replyId}")
    public ApiResponse<ThreadReplyResponse> updateReply(
            @PathVariable Long threadId,
            @PathVariable Long replyId,
            @Valid @RequestBody ThreadReplyUpdateRequest request
    ) {
        ThreadReplyResponse response = threadReplyService.updateReply(threadId, replyId, SecurityUtils.getCurrentUserId(), request);
        broadcastThreadEvent(threadId, ChatEventType.THREAD_REPLY_UPDATED, response);
        return ApiResponse.ok(response);
    }

    @DeleteMapping("/{replyId}")
    public ApiResponse<ThreadReplyResponse> deleteReply(
            @PathVariable Long threadId,
            @PathVariable Long replyId
    ) {
        ThreadReplyResponse response = threadReplyService.deleteReply(threadId, replyId, SecurityUtils.getCurrentUserId());
        broadcastThreadEvent(threadId, ChatEventType.THREAD_REPLY_DELETED, response);
        return ApiResponse.ok(response);
    }

    private void broadcastThreadEvent(Long threadId, ChatEventType eventType, ThreadReplyResponse response) {
        ChatEventResponse<ThreadReplyResponse> event = ChatEventResponse.of(eventType, response);
        messagingTemplate.convertAndSend("/topic/threads/" + threadId + "/events", event);
        messagingTemplate.convertAndSend("/topic/threads/" + threadId, event);
    }
}
