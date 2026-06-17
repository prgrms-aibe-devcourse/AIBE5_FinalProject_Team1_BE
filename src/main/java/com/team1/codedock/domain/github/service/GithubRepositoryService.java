package com.team1.codedock.domain.github.service;

import com.team1.codedock.domain.channel.dto.ChannelListResponse;
import com.team1.codedock.domain.channel.entity.Channel;
import com.team1.codedock.domain.channel.repository.ChannelRepository;
import com.team1.codedock.domain.github.dto.GithubConnectRequest;
import com.team1.codedock.domain.github.dto.GithubConnectResponse;
import com.team1.codedock.domain.github.dto.GithubRepoResponse;
import com.team1.codedock.domain.github.dto.GithubRepositoryLinkRequest;
import com.team1.codedock.domain.github.dto.GithubRepositoryResponse;
import com.team1.codedock.domain.github.entity.GithubRepository;
import com.team1.codedock.domain.github.repository.GithubRepositoryRepository;
import com.team1.codedock.domain.user.entity.User;
import com.team1.codedock.domain.user.repository.UserRepository;
import com.team1.codedock.domain.workspace.entity.Workspace;
import com.team1.codedock.domain.workspace.entity.WorkspaceMember;
import com.team1.codedock.domain.workspace.repository.WorkspaceMemberRepository;
import com.team1.codedock.domain.workspace.repository.WorkspaceRepository;
import com.team1.codedock.global.exception.BusinessException;
import com.team1.codedock.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
public class GithubRepositoryService {

    private static final String AUTHORITY_OWNER = "owner";
    private static final String AUTHORITY_ADMIN = "admin";

    private final UserRepository userRepository;
    private final WorkspaceRepository workspaceRepository;
    private final WorkspaceMemberRepository workspaceMemberRepository;
    private final GithubRepositoryRepository githubRepositoryRepository;
    private final ChannelRepository channelRepository;
    private final GithubApiService githubApiService;

    public GithubConnectResponse connectRepository(Long workspaceId, Long userId, GithubConnectRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        WorkspaceMember member = findActiveWorkspaceMember(workspaceId, userId);

        if (!githubRepositoryRepository.findByWorkspaceId(workspaceId).isEmpty()) {
            throw new BusinessException(ErrorCode.GITHUB_REPO_ALREADY_CONNECTED);
        }

        String token = user.getGithubAccessToken();
        if (token == null || token.isBlank()) {
            throw new BusinessException(ErrorCode.GITHUB_NOT_CONNECTED);
        }

        GithubRepoResponse repoInfo = githubApiService.getRepo(request.getOwner(), request.getRepo(), token);
        GithubRepository saved = githubRepositoryRepository.save(GithubRepository.create(
                member.getWorkspace(),
                String.valueOf(repoInfo.getId()),
                repoInfo.getOwner(),
                repoInfo.getName(),
                repoInfo.getFullName(),
                repoInfo.getHtmlUrl(),
                null,
                repoInfo.isPrivate(),
                repoInfo.getDefaultBranch()
        ));
        Channel repositoryChannel = findOrCreateRepositoryChannel(saved);

        return GithubConnectResponse.builder()
                .id(saved.getId())
                .channelId(repositoryChannel.getId())
                .owner(saved.getOwner())
                .name(saved.getName())
                .fullName(saved.getFullName())
                .url(saved.getUrl())
                .defaultBranch(saved.getDefaultBranch())
                .isPrivate(saved.isPrivate())
                .build();
    }

    public GithubRepository linkRepository(Long workspaceId, Long userId, GithubRepositoryLinkRequest request) {
        validateRepositoryManager(workspaceId, userId);
        Workspace workspace = findWorkspace(workspaceId);

        String githubRepoId = normalizeRequired(request.githubRepoId(), "GitHub repository id is required.");
        String owner = normalizeRequired(request.owner(), "GitHub repository owner is required.");
        String name = normalizeRequired(request.name(), "GitHub repository name is required.");
        String fullName = normalizeRequired(request.fullName(), "GitHub repository full name is required.");
        String url = normalizeRequired(request.url(), "GitHub repository url is required.");
        String description = normalizeNullable(request.description());
        String defaultBranch = normalizeNullable(request.defaultBranch());

        return githubRepositoryRepository.findByWorkspaceIdAndGithubRepoId(workspaceId, githubRepoId)
                .map(repository -> {
                    // Same GitHub repo keeps one row per workspace and refreshes metadata.
                    repository.updateMetadata(owner, name, fullName, url, description, request.isPrivate(), defaultBranch);
                    return repository;
                })
                .orElseGet(() -> githubRepositoryRepository.save(GithubRepository.create(
                        workspace,
                        githubRepoId,
                        owner,
                        name,
                        fullName,
                        url,
                        description,
                        request.isPrivate(),
                        defaultBranch
                )));
    }

    public GithubRepositoryResponse linkRepositoryResponse(
            Long workspaceId,
            Long userId,
            GithubRepositoryLinkRequest request
    ) {
        return GithubRepositoryResponse.from(linkRepository(workspaceId, userId, request));
    }

    public ChannelListResponse createRepositoryChannel(
            Long workspaceId,
            Long userId,
            GithubRepositoryLinkRequest request
    ) {
        GithubRepository githubRepository = linkRepository(workspaceId, userId, request);
        return ChannelListResponse.from(findOrCreateRepositoryChannel(githubRepository));
    }

    private Channel findOrCreateRepositoryChannel(GithubRepository githubRepository) {
        Long workspaceId = githubRepository.getWorkspace().getId();

        // One linked GitHub repository must have exactly one repository channel.
        return channelRepository.findRepositoryChannel(workspaceId, githubRepository.getId())
                .orElseGet(() -> {
                    // Create only when the repository channel does not exist to prevent duplicates.
                    Channel channel = Channel.createRepository(githubRepository.getWorkspace(), githubRepository);
                    return channelRepository.save(channel);
                });
    }

    private Workspace findWorkspace(Long workspaceId) {
        return workspaceRepository.findById(workspaceId)
                .orElseThrow(() -> new BusinessException(ErrorCode.WORKSPACE_NOT_FOUND));
    }

    private WorkspaceMember findActiveWorkspaceMember(Long workspaceId, Long userId) {
        return workspaceMemberRepository.findByWorkspace_IdAndUser_IdAndIsActiveTrue(workspaceId, userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.WORKSPACE_MEMBER_NOT_FOUND));
    }

    private void validateRepositoryManager(Long workspaceId, Long userId) {
        if (userId == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }

        WorkspaceMember member = workspaceMemberRepository.findByWorkspace_IdAndUser_IdAndIsActiveTrue(workspaceId, userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.FORBIDDEN));

        if (!canManageRepository(member.getAuthority())) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
    }

    private boolean canManageRepository(String authority) {
        String normalizedAuthority = authority == null ? "" : authority.trim().toLowerCase();
        return AUTHORITY_OWNER.equals(normalizedAuthority) || AUTHORITY_ADMIN.equals(normalizedAuthority);
    }

    private String normalizeRequired(String value, String message) {
        String normalized = normalizeNullable(value);
        if (normalized == null) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, message);
        }
        return normalized;
    }

    private String normalizeNullable(String value) {
        if (value == null) {
            return null;
        }

        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }
}
