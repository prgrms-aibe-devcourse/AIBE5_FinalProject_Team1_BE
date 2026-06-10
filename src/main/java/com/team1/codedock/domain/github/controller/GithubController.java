package com.team1.codedock.domain.github.controller;

import com.team1.codedock.domain.github.dto.GithubCollaboratorResponse;
import com.team1.codedock.domain.github.dto.GithubRepoResponse;
import com.team1.codedock.domain.github.service.GithubApiService;
import com.team1.codedock.global.response.ApiResponse;
import com.team1.codedock.global.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/github")
@RequiredArgsConstructor
public class GithubController {

    private final GithubApiService githubApiService;

    @GetMapping("/repos")
    public ApiResponse<List<GithubRepoResponse>> getMyRepos() {
        return ApiResponse.ok(githubApiService.getUserRepos(SecurityUtils.getCurrentUserId()));
    }

    @GetMapping("/repos/{owner}/{repo}/collaborators")
    public ApiResponse<List<GithubCollaboratorResponse>> getCollaborators(
            @PathVariable String owner,
            @PathVariable String repo) {
        return ApiResponse.ok(githubApiService.getRepoCollaborators(
                SecurityUtils.getCurrentUserId(), owner, repo));
    }
}
