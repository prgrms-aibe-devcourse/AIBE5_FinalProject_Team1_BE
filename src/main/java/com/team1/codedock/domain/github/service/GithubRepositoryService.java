package com.team1.codedock.domain.github.service;

import com.team1.codedock.domain.github.dto.GithubConnectRequest;
import com.team1.codedock.domain.github.dto.GithubConnectResponse;
import com.team1.codedock.domain.github.dto.GithubRepoResponse;
import com.team1.codedock.domain.github.entity.GithubRepository;
import com.team1.codedock.domain.github.repository.GithubRepositoryRepository;
import com.team1.codedock.domain.user.entity.User;
import com.team1.codedock.domain.user.repository.UserRepository;
import com.team1.codedock.domain.workspace.entity.WorkspaceMember;
import com.team1.codedock.domain.workspace.repository.WorkspaceMemberRepository;
import com.team1.codedock.global.exception.BusinessException;
import com.team1.codedock.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
public class GithubRepositoryService {

    private final UserRepository userRepository;
    private final WorkspaceMemberRepository workspaceMemberRepository;
    private final GithubRepositoryRepository githubRepositoryRepository;
    private final GithubApiService githubApiService;

    public GithubConnectResponse connectRepository(Long workspaceId, Long userId, GithubConnectRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        WorkspaceMember member = workspaceMemberRepository
                .findByWorkspace_IdAndUser_IdAndIsActiveTrue(workspaceId, userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.WORKSPACE_MEMBER_NOT_FOUND));

        if (!githubRepositoryRepository.findByWorkspaceId(workspaceId).isEmpty()) {
            throw new BusinessException(ErrorCode.GITHUB_REPO_ALREADY_CONNECTED);
        }

        String token = user.getGithubAccessToken();
        if (token == null || token.isBlank()) {
            throw new BusinessException(ErrorCode.GITHUB_NOT_CONNECTED);
        }

        GithubRepoResponse repoInfo = githubApiService.getRepo(request.getOwner(), request.getRepo(), token);

        GithubRepository githubRepository = GithubRepository.create(
                member.getWorkspace(),
                String.valueOf(repoInfo.getId()),
                repoInfo.getOwner(),
                repoInfo.getName(),
                repoInfo.getFullName(),
                repoInfo.getHtmlUrl(),
                null,
                repoInfo.isPrivate(),
                repoInfo.getDefaultBranch()
        );

        GithubRepository saved = githubRepositoryRepository.save(githubRepository);

        return GithubConnectResponse.builder()
                .id(saved.getId())
                .owner(saved.getOwner())
                .name(saved.getName())
                .fullName(saved.getFullName())
                .url(saved.getUrl())
                .defaultBranch(saved.getDefaultBranch())
                .isPrivate(saved.isPrivate())
                .build();
    }
}
