package com.team1.codedock.domain.workspace.controller;

import com.team1.codedock.domain.workspace.dto.WorkspaceEventResponse;
import com.team1.codedock.domain.workspace.service.WorkspaceEventService;
import com.team1.codedock.global.response.ApiResponse;
import com.team1.codedock.global.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/events")
@RequiredArgsConstructor
public class WorkspaceEventController {

    private final WorkspaceEventService workspaceEventService;

    @GetMapping
    public ApiResponse<List<WorkspaceEventResponse>> getMyEvents() {
        return ApiResponse.ok(workspaceEventService.getEventsForUser(SecurityUtils.getCurrentUserId()));
    }
}
