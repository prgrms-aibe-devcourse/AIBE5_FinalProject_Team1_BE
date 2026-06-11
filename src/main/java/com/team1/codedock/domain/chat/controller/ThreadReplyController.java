package com.team1.codedock.domain.chat.controller;

import com.team1.codedock.domain.chat.dto.ThreadReplyCreateRequest;
import com.team1.codedock.domain.chat.dto.ThreadReplyResponse;
import com.team1.codedock.domain.chat.dto.ThreadReplyUpdateRequest;
import com.team1.codedock.domain.chat.service.ThreadReplyService;
import com.team1.codedock.global.response.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/threads/{threadId}/replies")
public class ThreadReplyController {

    private final ThreadReplyService threadReplyService;

    @GetMapping
    public ApiResponse<List<ThreadReplyResponse>> getReplies(
            @PathVariable Long threadId,
            @RequestHeader(value = "X-User-Id", required = false) Long userId
    ) {
        return ApiResponse.ok(threadReplyService.getReplies(threadId, userId));
    }

    @PostMapping
    public ApiResponse<ThreadReplyResponse> createReply(
            @PathVariable Long threadId,
            @RequestHeader(value = "X-User-Id", required = false) Long userId,
            @Valid @RequestBody ThreadReplyCreateRequest request
    ) {
        return ApiResponse.ok(threadReplyService.createReply(threadId, userId, request));
    }

    @PatchMapping("/{replyId}")
    public ApiResponse<ThreadReplyResponse> updateReply(
            @PathVariable Long threadId,
            @PathVariable Long replyId,
            @RequestHeader(value = "X-User-Id", required = false) Long userId,
            @Valid @RequestBody ThreadReplyUpdateRequest request
    ) {
        return ApiResponse.ok(threadReplyService.updateReply(threadId, replyId, userId, request));
    }

    @DeleteMapping("/{replyId}")
    public ApiResponse<ThreadReplyResponse> deleteReply(
            @PathVariable Long threadId,
            @PathVariable Long replyId,
            @RequestHeader(value = "X-User-Id", required = false) Long userId
    ) {
        return ApiResponse.ok(threadReplyService.deleteReply(threadId, replyId, userId));
    }
}
