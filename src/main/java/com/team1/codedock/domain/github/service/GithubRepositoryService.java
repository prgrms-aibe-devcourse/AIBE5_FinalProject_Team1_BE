package com.team1.codedock.domain.github.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.team1.codedock.domain.channel.dto.ChannelListResponse;
import com.team1.codedock.domain.channel.entity.Channel;
import com.team1.codedock.domain.channel.repository.ChannelRepository;
import com.team1.codedock.domain.github.dto.GithubConnectRequest;
import com.team1.codedock.domain.github.dto.GithubConnectResponse;
import com.team1.codedock.domain.github.dto.GithubRepoResponse;
import com.team1.codedock.domain.github.dto.GithubRepositoryOverviewResponse;
import com.team1.codedock.domain.github.dto.GithubRepositoryOverviewResponse.RepositoryActivityResponse;
import com.team1.codedock.domain.github.dto.GithubRepositoryOverviewResponse.RepositoryPullRequestSummaryResponse;
import com.team1.codedock.domain.github.dto.GithubRepositoryLinkRequest;
import com.team1.codedock.domain.github.dto.GithubRepositoryResponse;
import com.team1.codedock.domain.github.entity.GithubRepository;
import com.team1.codedock.domain.github.repository.GithubRepositoryRepository;
import com.team1.codedock.domain.issue.entity.GithubIssue;
import com.team1.codedock.domain.issue.repository.GithubIssueRepository;
import com.team1.codedock.domain.pr.entity.GithubPullRequest;
import com.team1.codedock.domain.pr.repository.GithubPullRequestRepository;
import com.team1.codedock.domain.user.entity.User;
import com.team1.codedock.domain.user.repository.UserRepository;
import com.team1.codedock.domain.workspace.entity.Workspace;
import com.team1.codedock.domain.workspace.entity.WorkspaceMember;
import com.team1.codedock.domain.workspace.repository.WorkspaceMemberRepository;
import com.team1.codedock.domain.workspace.repository.WorkspaceRepository;
import com.team1.codedock.global.exception.BusinessException;
import com.team1.codedock.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class GithubRepositoryService {

    private static final String AUTHORITY_OWNER = "owner";
    private static final String AUTHORITY_ADMIN = "admin";
    private static final ZoneId DASHBOARD_ZONE = ZoneId.of("Asia/Seoul");
    private static final int OVERVIEW_ACTIVITY_LIMIT = 5;
    private static final int OVERVIEW_OPEN_PR_LIMIT = 5;

    private final UserRepository userRepository;
    private final WorkspaceRepository workspaceRepository;
    private final WorkspaceMemberRepository workspaceMemberRepository;
    private final GithubRepositoryRepository githubRepositoryRepository;
    private final ChannelRepository channelRepository;
    private final GithubApiService githubApiService;
    private final GithubWebhookRegistrationService githubWebhookRegistrationService;
    private final GithubPullRequestRepository githubPullRequestRepository;
    private final GithubIssueRepository githubIssueRepository;
    private final ObjectMapper objectMapper;

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

        try {
            githubWebhookRegistrationService.registerWebhook(workspaceId, saved.getId(), userId);
        } catch (Exception e) {
            log.warn("Webhook 자동 등록 실패 (수동 등록 필요) → repoId={}, reason={}", saved.getId(), e.getMessage());
        }

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

    @Transactional(readOnly = true)
    public List<GithubConnectResponse> getWorkspaceRepositories(Long workspaceId, Long userId) {
        workspaceMemberRepository.findByWorkspace_IdAndUser_IdAndIsActiveTrue(workspaceId, userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.FORBIDDEN));

        return githubRepositoryRepository.findByWorkspaceId(workspaceId).stream()
                .map(repo -> {
                    Channel channel = channelRepository.findRepositoryChannel(workspaceId, repo.getId()).orElse(null);
                    return GithubConnectResponse.builder()
                            .id(repo.getId())
                            .channelId(channel != null ? channel.getId() : null)
                            .owner(repo.getOwner())
                            .name(repo.getName())
                            .fullName(repo.getFullName())
                            .url(repo.getUrl())
                            .defaultBranch(repo.getDefaultBranch())
                            .isPrivate(repo.isPrivate())
                            .build();
                })
                .toList();
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
        Channel channel = findOrCreateRepositoryChannel(githubRepository);

        try {
            githubWebhookRegistrationService.registerWebhook(workspaceId, githubRepository.getId(), userId);
        } catch (Exception e) {
            log.warn("Webhook 자동 등록 실패 (수동 등록 필요) → repoId={}, reason={}", githubRepository.getId(), e.getMessage());
        }

        return ChannelListResponse.from(channel);
    }

    @Transactional(readOnly = true)
    public GithubRepositoryOverviewResponse getRepositoryOverview(Long workspaceId, Long repositoryId, Long userId) {
        WorkspaceMember member = workspaceMemberRepository.findByWorkspace_IdAndUser_IdAndIsActiveTrue(workspaceId, userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.FORBIDDEN));
        GithubRepository repository = githubRepositoryRepository.findByIdAndWorkspaceId(repositoryId, workspaceId)
                .orElseThrow(() -> new BusinessException(ErrorCode.GITHUB_REPO_NOT_FOUND));
        Channel channel = channelRepository.findRepositoryChannel(workspaceId, repositoryId).orElse(null);

        List<GithubPullRequest> pullRequests = githubPullRequestRepository.findAllByRepository_IdOrderByGithubCreatedAtDesc(repositoryId);
        long todayCommitCount = countTodayCommits(pullRequests);
        long openPrCount = githubPullRequestRepository.countOpenByRepositoryId(repositoryId);
        long openIssueCount = githubIssueRepository.countOpenByRepositoryId(repositoryId);
        long highRiskCount = githubIssueRepository.countOpenHighPriorityByRepositoryId(repositoryId);
        long activeMemberCount = workspaceMemberRepository.countByWorkspaceAndIsActiveTrue(member.getWorkspace());

        List<RepositoryActivityResponse> recentActivities = buildRecentActivities(repositoryId);
        List<RepositoryPullRequestSummaryResponse> openPullRequests = githubPullRequestRepository
                .findOpenByRepositoryId(repositoryId, PageRequest.of(0, OVERVIEW_OPEN_PR_LIMIT))
                .stream()
                .map(this::toPullRequestSummary)
                .toList();

        return new GithubRepositoryOverviewResponse(
                repository.getId(),
                workspaceId,
                channel != null ? channel.getId() : null,
                repository.getOwner(),
                repository.getName(),
                repository.getFullName(),
                repository.getUrl(),
                repository.getDefaultBranch(),
                repository.getLastSyncedAt(),
                todayCommitCount,
                openPrCount,
                openIssueCount,
                highRiskCount,
                activeMemberCount,
                null,
                null,
                null,
                recentActivities,
                openPullRequests
        );
    }

    private List<RepositoryActivityResponse> buildRecentActivities(Long repositoryId) {
        List<RepositoryActivityCandidate> candidates = new ArrayList<>();
        githubPullRequestRepository.findRecentByRepositoryId(repositoryId, PageRequest.of(0, OVERVIEW_ACTIVITY_LIMIT))
                .forEach(pr -> candidates.add(new RepositoryActivityCandidate(
                        "PULL_REQUEST",
                        pr.getId(),
                        pr.getPrNumber(),
                        pr.getTitle(),
                        pr.getAuthor(),
                        pr.getState(),
                        firstNonNull(pr.getGithubUpdatedAt(), pr.getGithubCreatedAt(), pr.getUpdatedAt(), pr.getCreatedAt())
                )));
        githubIssueRepository.findRecentByRepositoryId(repositoryId, PageRequest.of(0, OVERVIEW_ACTIVITY_LIMIT))
                .forEach(issue -> candidates.add(new RepositoryActivityCandidate(
                        "ISSUE",
                        issue.getId(),
                        issue.getIssueNumber(),
                        issue.getTitle(),
                        issue.getAuthor(),
                        issue.getState(),
                        firstNonNull(issue.getGithubUpdatedAt(), issue.getGithubCreatedAt(), issue.getUpdatedAt(), issue.getCreatedAt())
                )));

        return candidates.stream()
                .sorted(Comparator.comparing(
                        RepositoryActivityCandidate::occurredAt,
                        Comparator.nullsLast(Comparator.reverseOrder())
                ))
                .limit(OVERVIEW_ACTIVITY_LIMIT)
                .map(candidate -> new RepositoryActivityResponse(
                        candidate.type(),
                        candidate.id(),
                        candidate.number(),
                        candidate.title(),
                        candidate.actor(),
                        candidate.state(),
                        candidate.occurredAt()
                ))
                .toList();
    }

    private RepositoryPullRequestSummaryResponse toPullRequestSummary(GithubPullRequest pullRequest) {
        return new RepositoryPullRequestSummaryResponse(
                pullRequest.getId(),
                pullRequest.getPrNumber(),
                pullRequest.getTitle(),
                pullRequest.getAuthor(),
                pullRequest.getState(),
                pullRequest.getChangedFilesCount(),
                pullRequest.getAdditions(),
                pullRequest.getDeletions(),
                firstNonNull(
                        pullRequest.getGithubUpdatedAt(),
                        pullRequest.getGithubCreatedAt(),
                        pullRequest.getUpdatedAt(),
                        pullRequest.getCreatedAt()
                )
        );
    }

    private long countTodayCommits(List<GithubPullRequest> pullRequests) {
        LocalDate today = LocalDate.now(DASHBOARD_ZONE);
        return pullRequests.stream()
                .map(GithubPullRequest::getCommitsJson)
                .filter(Objects::nonNull)
                .mapToLong(commitsJson -> countCommitsOnDate(commitsJson, today))
                .sum();
    }

    private long countCommitsOnDate(String commitsJson, LocalDate targetDate) {
        if (commitsJson == null || commitsJson.isBlank()) {
            return 0L;
        }

        try {
            JsonNode root = objectMapper.readTree(commitsJson);
            if (!root.isArray()) {
                return 0L;
            }

            long count = 0L;
            for (JsonNode commitNode : root) {
                LocalDate commitDate = parseCommitDate(commitNode.path("date").asText(null));
                if (targetDate.equals(commitDate)) {
                    count++;
                }
            }
            return count;
        } catch (Exception e) {
            log.debug("GitHub commit JSON 파싱 실패. commitsJson={}", commitsJson, e);
            return 0L;
        }
    }

    private LocalDate parseCommitDate(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }

        try {
            return Instant.parse(value).atZone(DASHBOARD_ZONE).toLocalDate();
        } catch (DateTimeParseException ignored) {
            // GitHub webhook 외 경로에서 offset 또는 local datetime 문자열이 들어와도 현황 집계가 깨지지 않게 함.
        }

        try {
            return OffsetDateTime.parse(value).atZoneSameInstant(DASHBOARD_ZONE).toLocalDate();
        } catch (DateTimeParseException ignored) {
            // OffsetDateTime 파싱 실패 시 마지막으로 LocalDateTime 형식을 시도함.
        }

        try {
            return LocalDateTime.parse(value).toLocalDate();
        } catch (DateTimeParseException ignored) {
            return null;
        }
    }

    @SafeVarargs
    private final <T> T firstNonNull(T... values) {
        for (T value : values) {
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    private Channel findOrCreateRepositoryChannel(GithubRepository githubRepository) {
        Long workspaceId = githubRepository.getWorkspace().getId();

        // 연결된 GitHub 레포지토리 하나당 레포지토리 채널도 반드시 하나만 존재해야 함.
        return channelRepository.findRepositoryChannel(workspaceId, githubRepository.getId())
                .orElseGet(() -> {
                    String channelName = resolveRepositoryChannelName(githubRepository);
                    // 레포지토리 채널이 없을 때만 생성해서 중복 채널을 방지함.
                    Channel channel = Channel.createRepository(
                            githubRepository.getWorkspace(),
                            githubRepository,
                            channelName,
                            nextDisplayOrder(workspaceId)
                    );
                    return channelRepository.save(channel);
                });
    }

    private int nextDisplayOrder(Long workspaceId) {
        return channelRepository.findMaxDisplayOrderByWorkspaceId(workspaceId) + 1;
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

    private record RepositoryActivityCandidate(
            String type,
            Long id,
            Integer number,
            String title,
            String actor,
            String state,
            LocalDateTime occurredAt
    ) {
    }
}
