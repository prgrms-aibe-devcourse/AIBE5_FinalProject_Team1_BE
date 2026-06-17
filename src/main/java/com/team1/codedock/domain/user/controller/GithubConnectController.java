package com.team1.codedock.domain.user.controller;

import com.team1.codedock.domain.user.service.GithubConnectService;
import com.team1.codedock.global.response.ApiResponse;
import com.team1.codedock.global.security.SecurityUtils;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/users/me/github/connect")
@RequiredArgsConstructor
public class GithubConnectController {

    private final GithubConnectService githubConnectService;

    @PostMapping("/start")
    public ApiResponse<Map<String, String>> start() {
        String authorizeUrl = githubConnectService.buildAuthorizeUrl(SecurityUtils.getCurrentUserId());
        return ApiResponse.ok(Map.of("authorizeUrl", authorizeUrl));
    }

    @GetMapping("/callback")
    public void callback(@RequestParam(value = "code", required = false) String code,
                         @RequestParam(value = "state", required = false) String state,
                         HttpServletResponse response) throws IOException {
        response.sendRedirect(githubConnectService.completeConnect(code, state));
    }
}