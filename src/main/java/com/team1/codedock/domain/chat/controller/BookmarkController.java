package com.team1.codedock.domain.chat.controller;

import com.team1.codedock.domain.chat.dto.BookmarkResponse;
import com.team1.codedock.domain.chat.dto.BookmarkToggleResponse;
import com.team1.codedock.domain.chat.service.BookmarkService;
import com.team1.codedock.global.response.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api")
public class BookmarkController {

    private final BookmarkService bookmarkService;

    // 채널 메시지의 북마크 상태를 토글
    @PostMapping("/channels/{channelId}/messages/{messageId}/bookmark")
    public ApiResponse<BookmarkToggleResponse> toggleMessageBookmark(
            @PathVariable Long channelId,
            @PathVariable Long messageId,
            @RequestHeader(value = "X-User-Id", required = false) Long userId
    ) {
        return ApiResponse.ok(bookmarkService.toggleMessageBookmark(channelId, messageId, userId));
    }

    // 현재 사용자가 워크스페이스에서 저장한 북마크 메시지 목록을 조회함.
    @GetMapping("/workspaces/{workspaceId}/bookmarks")
    public ApiResponse<List<BookmarkResponse>> getMyBookmarks(
            @PathVariable Long workspaceId,
            @RequestHeader(value = "X-User-Id", required = false) Long userId
    ) {
        return ApiResponse.ok(bookmarkService.getMyBookmarks(workspaceId, userId));
    }
}
