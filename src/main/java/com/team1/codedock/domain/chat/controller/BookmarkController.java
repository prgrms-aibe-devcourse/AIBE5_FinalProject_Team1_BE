package com.team1.codedock.domain.chat.controller;

import com.team1.codedock.domain.chat.dto.BookmarkResponse;
import com.team1.codedock.domain.chat.dto.BookmarkToggleResponse;
import com.team1.codedock.domain.chat.service.BookmarkService;
import com.team1.codedock.global.response.ApiResponse;
import com.team1.codedock.global.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api")
public class BookmarkController {

    private final BookmarkService bookmarkService;

    @PostMapping("/channels/{channelId}/messages/{messageId}/bookmark")
    public ApiResponse<BookmarkToggleResponse> toggleMessageBookmark(
            @PathVariable Long channelId,
            @PathVariable Long messageId
    ) {
        return ApiResponse.ok(bookmarkService.toggleMessageBookmark(channelId, messageId, SecurityUtils.getCurrentUserId()));
    }

    @GetMapping("/workspaces/{workspaceId}/bookmarks")
    public ApiResponse<List<BookmarkResponse>> getMyBookmarks(
            @PathVariable Long workspaceId
    ) {
        return ApiResponse.ok(bookmarkService.getMyBookmarks(workspaceId, SecurityUtils.getCurrentUserId()));
    }
}
