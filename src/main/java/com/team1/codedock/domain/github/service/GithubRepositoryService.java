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

        WorkspaceMember member = validateRepositoryManager(workspaceId, userId);

        String token = user.getGithubAccessToken();
        if (token == null || token.isBlank()) {
            throw new BusinessException(ErrorCode.GITHUB_NOT_CONNECTED);
        }

        GithubRepoResponse repoInfo = githubApiService.getRepo(request.getOwner(), request.getRepo(), token);
        String githubRepoId = String.valueOf(repoInfo.getId());
        GithubRepository saved = githubRepositoryRepository.findByWorkspaceIdAndGithubRepoId(workspaceId, githubRepoId)
                .map(repository -> {
                    repository.updateMetadata(
                            repoInfo.getOwner(),
                            repoInfo.getName(),
                            repoInfo.getFullName(),
                            repoInfo.getHtmlUrl(),
                            null,
                            repoInfo.isPrivate(),
                            repoInfo.getDefaultBranch()
                    );
                    return repository;
                })
                .orElseGet(() -> githubRepositoryRepository.save(GithubRepository.create(
                        member.getWorkspace(),
                        githubRepoId,
                        repoInfo.getOwner(),
                        repoInfo.getName(),
                        repoInfo.getFullName(),
                        repoInfo.getHtmlUrl(),
                        null,
                        repoInfo.isPrivate(),
                        repoInfo.getDefaultBranch()
                )));
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

        String githubRepoId = normalizeRequired(request.githubRepoId(), "GitHub 레포지토리 ID는 필수입니다.");
        String owner = normalizeRequired(request.owner(), "GitHub 레포지토리 소유자는 필수입니다.");
        String name = normalizeRequired(request.name(), "GitHub 레포지토리 이름은 필수입니다.");
        validateRepositoryNameForChannel(name);
        String fullName = normalizeRequired(request.fullName(), "GitHub 레포지토리 전체 이름은 필수입니다.");
        String url = normalizeRequired(request.url(), "GitHub 레포지토리 URL은 필수입니다.");
        String description = normalizeNullable(request.description());
        String defaultBranch = normalizeNullable(request.defaultBranch());

        return githubRepositoryRepository.findByWorkspaceIdAndGithubRepoId(workspaceId, githubRepoId)
                .map(repository -> {
                    // 같은 GitHub 레포지토리는 워크스페이스마다 한 데이터만 유지하고 메타데이터만 갱신함.
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

        // 연결된 GitHub 레포지토리 하나당 레포지토리 채널도 반드시 하나만 존재해야 함.
        return channelRepository.findRepositoryChannel(workspaceId, githubRepository.getId())
                .orElseGet(() -> {
                    String channelName = resolveRepositoryChannelName(githubRepository);
                    // 레포지토리 채널이 없을 때만 생성해서 중복 채널을 방지함.
                    Channel channel = Channel.createRepository(githubRepository.getWorkspace(), githubRepository, channelName);
                    return channelRepository.save(channel);
                });
    }

    private Workspace findWorkspace(Long workspaceId) {
        return workspaceRepository.findById(workspaceId)
                .orElseThrow(() -> new BusinessException(ErrorCode.WORKSPACE_NOT_FOUND));
    }

    private WorkspaceMember validateRepositoryManager(Long workspaceId, Long userId) {
        if (userId == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }

        WorkspaceMember member = workspaceMemberRepository.findByWorkspace_IdAndUser_IdAndIsActiveTrue(workspaceId, userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.FORBIDDEN));

        if (!canManageRepository(member.getAuthority())) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }

        return member;
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

    private void validateRepositoryNameForChannel(String name) {
        if (name.length() > Channel.MAX_NAME_LENGTH) {
            throw new BusinessException(
                    ErrorCode.INVALID_INPUT,
                    "GitHub 레포지토리 이름은 " + Channel.MAX_NAME_LENGTH + "자 이하로 입력해주세요."
            );
        }
    }

    private String resolveRepositoryChannelName(GithubRepository githubRepository) {
        Long workspaceId = githubRepository.getWorkspace().getId();
        String baseName = truncateChannelName(githubRepository.getName());
        if (!channelNameExists(workspaceId, baseName)) {
            return baseName;
        }

        String owner = normalizeNullable(githubRepository.getOwner());
        if (owner != null) {
            String ownerCandidate = appendChannelNameSuffix(baseName, "-" + owner);
            if (!channelNameExists(workspaceId, ownerCandidate)) {
                return ownerCandidate;
            }
        }

        String repoId = normalizeNullable(githubRepository.getGithubRepoId());
        if (repoId != null) {
            String repoCandidate = appendChannelNameSuffix(baseName, "-repo-" + repoId);
            if (!channelNameExists(workspaceId, repoCandidate)) {
                return repoCandidate;
            }
        }

        throw new BusinessException(ErrorCode.CONFLICT, "워크스페이스에 이미 같은 이름의 레포지토리 채널이 존재합니다.");
    }

    private boolean channelNameExists(Long workspaceId, String name) {
        return channelRepository.countByWorkspaceIdAndNameIgnoreCase(workspaceId, name) > 0;
    }

    private String appendChannelNameSuffix(String baseName, String suffix) {
        String normalizedSuffix = truncateChannelName(suffix);
        int maxBaseLength = Channel.MAX_NAME_LENGTH - normalizedSuffix.length();
        if (maxBaseLength <= 0) {
            return normalizedSuffix;
        }
        return truncate(baseName, maxBaseLength) + normalizedSuffix;
    }

    private String truncateChannelName(String value) {
        return truncate(value, Channel.MAX_NAME_LENGTH);
    }

    private String truncate(String value, int maxLength) {
        if (value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }
}
