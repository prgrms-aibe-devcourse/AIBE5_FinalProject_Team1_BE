package com.team1.codedock.domain.dashboard.controller;

import com.team1.codedock.domain.dashboard.dto.DashboardEventResponse;
import com.team1.codedock.domain.dashboard.dto.DashboardSummaryResponse;
import com.team1.codedock.domain.dashboard.dto.WorkspaceDashboardResponse;
import com.team1.codedock.domain.dashboard.service.DashboardService;
import com.team1.codedock.global.response.ApiResponse;
import com.team1.codedock.global.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/dashboard")
@RequiredArgsConstructor
public class DashboardController {

    private final DashboardService dashboardService;

    @GetMapping("/summary")
    public ApiResponse<DashboardSummaryResponse> getSummary() {
        return ApiResponse.ok(dashboardService.getSummary(SecurityUtils.getCurrentUserId()));
    }

    @GetMapping("/workspaces")
    public ApiResponse<List<WorkspaceDashboardResponse>> getWorkspaceStats() {
        return ApiResponse.ok(dashboardService.getWorkspaceStats(SecurityUtils.getCurrentUserId()));
    }

    @GetMapping("/events")
    public ApiResponse<List<DashboardEventResponse>> getEvents() {
        return ApiResponse.ok(dashboardService.getEvents(SecurityUtils.getCurrentUserId()));
    }

    @PatchMapping("/events/{eventId}/read")
    public ApiResponse<Void> markEventAsRead(@PathVariable Long eventId) {
        dashboardService.markEventAsRead(eventId, SecurityUtils.getCurrentUserId());
        return ApiResponse.ok();
    }
}
