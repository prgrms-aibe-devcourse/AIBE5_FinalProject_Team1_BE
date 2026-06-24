package com.team1.codedock.domain.dashboard.service;

import com.team1.codedock.domain.dashboard.dto.DashboardEventResponse;
import com.team1.codedock.domain.dashboard.dto.DashboardSummaryResponse;
import com.team1.codedock.domain.dashboard.dto.WorkspaceDashboardResponse;
import com.team1.codedock.domain.issue.repository.IssueAssigneeRepository;
import com.team1.codedock.domain.pr.repository.GithubPullRequestRepository;
import com.team1.codedock.domain.pr.repository.PullRequestReviewRepository;
import com.team1.codedock.domain.pr.repository.PullRequestReviewRequestRepository;
import com.team1.codedock.domain.user.entity.User;
import com.team1.codedock.domain.workspace.entity.WorkspaceEvent;
import com.team1.codedock.domain.workspace.entity.WorkspaceMember;
import com.team1.codedock.domain.workspace.entity.WorkspaceEventReadStatus;
import com.team1.codedock.domain.workspace.repository.WorkspaceEventReadStatusRepository;
import com.team1.codedock.domain.workspace.repository.WorkspaceEventRepository;
import com.team1.codedock.domain.workspace.repository.WorkspaceMemberRepository;
import com.team1.codedock.global.exception.BusinessException;
import com.team1.codedock.global.exception.ErrorCode;
import com.team1.codedock.domain.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class DashboardService {

    private static final int DASHBOARD_EVENT_LIMIT = 50;

    private final WorkspaceMemberRepository workspaceMemberRepository;
    private final UserRepository userRepository;
    private final IssueAssigneeRepository issueAssigneeRepository;
    private final GithubPullRequestRepository githubPullRequestRepository;
    private final PullRequestReviewRequestRepository pullRequestReviewRequestRepository;
    private final PullRequestReviewRepository pullRequestReviewRepository;
    private final WorkspaceEventRepository workspaceEventRepository;
    private final WorkspaceEventReadStatusRepository workspaceEventReadStatusRepository;

    public DashboardSummaryResponse getSummary(Long userId) {
        User user = findUser(userId);
        String githubUsername = user.getGithubUsername();
        List<Long> workspaceIds = workspaceMemberRepository.findAllByUser_IdAndIsActiveTrue(userId).stream()
                .map(m -> m.getWorkspace().getId())
                .toList();

        if (workspaceIds.isEmpty()) {
            return DashboardSummaryResponse.of(0, 0, 0, 0);
        }

        long openIssueCount = issueAssigneeRepository.countOpenByUserIdAndWorkspaceIdIn(userId, workspaceIds);
        long reviewRequestCount = pullRequestReviewRequestRepository.countByUserIdAndWorkspaceIdIn(userId, workspaceIds);
        long openPrCount = githubUsername != null
                ? githubPullRequestRepository.countOpenByAuthorAndWorkspaceIdIn(githubUsername, workspaceIds)
                : 0;
        long receivedReviewCount = githubUsername != null
                ? pullRequestReviewRepository.countOnOpenPrsByAuthorAndWorkspaceIdIn(githubUsername, workspaceIds)
                : 0;

        return DashboardSummaryResponse.of(openIssueCount, openPrCount, reviewRequestCount, receivedReviewCount);
    }

    public List<WorkspaceDashboardResponse> getWorkspaceStats(Long userId) {
        User user = findUser(userId);
        String githubUsername = user.getGithubUsername();
        List<WorkspaceMember> memberships = workspaceMemberRepository.findAllByUser_IdAndIsActiveTrue(userId);
        List<Long> workspaceIds = memberships.stream().map(m -> m.getWorkspace().getId()).toList();

        if (workspaceIds.isEmpty()) {
            return List.of();
        }

        Map<Long, Long> openIssueCounts = toCountMap(
                issueAssigneeRepository.countOpenGroupByWorkspaceId(userId, workspaceIds));
        Map<Long, Long> reviewRequestCounts = toCountMap(
                pullRequestReviewRequestRepository.countGroupByWorkspaceId(userId, workspaceIds));
        Map<Long, Long> openPrCounts = githubUsername != null
                ? toCountMap(githubPullRequestRepository.countOpenGroupByWorkspaceId(githubUsername, workspaceIds))
                : Map.of();
        Map<Long, Long> receivedReviewCounts = githubUsername != null
                ? toCountMap(pullRequestReviewRepository.countOnOpenPrsGroupByWorkspaceId(githubUsername, workspaceIds))
                : Map.of();

        return memberships.stream()
                .map(member -> {
                    Long wid = member.getWorkspace().getId();
                    return new WorkspaceDashboardResponse(
                            wid,
                            member.getWorkspace().getName(),
                            member.getWorkspace().getLogoUrl(),
                            openIssueCounts.getOrDefault(wid, 0L),
                            openPrCounts.getOrDefault(wid, 0L),
                            reviewRequestCounts.getOrDefault(wid, 0L),
                            receivedReviewCounts.getOrDefault(wid, 0L)
                    );
                })
                .toList();
    }

    public List<DashboardEventResponse> getEvents(Long userId) {
        User user = findUser(userId);
        String githubUsername = user.getGithubUsername();
        List<WorkspaceMember> memberships = workspaceMemberRepository.findAllByUser_IdAndIsActiveTrue(userId);
        List<Long> workspaceIds = memberships.stream()
                .map(m -> m.getWorkspace().getId())
                .toList();

        if (workspaceIds.isEmpty()) {
            return List.of();
        }

        List<WorkspaceEvent.EventType> broadcastTypes = List.of(
                WorkspaceEvent.EventType.PR_CREATED, WorkspaceEvent.EventType.ISSUE_CREATED);
        List<WorkspaceEvent.EventType> targetedTypes = List.of(
                WorkspaceEvent.EventType.PR_REVIEW, WorkspaceEvent.EventType.REPLY, WorkspaceEvent.EventType.MENTION);

        List<WorkspaceEvent> events = workspaceEventRepository
                .findDashboardEvents(workspaceIds, userId, broadcastTypes, targetedTypes,
                        PageRequest.of(0, DASHBOARD_EVENT_LIMIT)).stream()
                .filter(e -> {
                    if (e.getType() == WorkspaceEvent.EventType.PR_CREATED
                            || e.getType() == WorkspaceEvent.EventType.ISSUE_CREATED) {
                        if (githubUsername == null) return true;
                        return !githubUsername.equals(e.getActorName());
                    }
                    return true;
                })
                .toList();

        Set<Long> readEventIds = events.isEmpty() ? Set.of()
                : workspaceEventReadStatusRepository.findReadEventIdsByUserIdAndEventIds(
                        userId, events.stream().map(WorkspaceEvent::getId).toList());

        return events.stream()
                .map(e -> DashboardEventResponse.from(e, readEventIds.contains(e.getId())))
                .toList();
    }

    @Transactional
    public void markEventAsRead(Long eventId, Long userId) {
        List<WorkspaceMember> memberships = workspaceMemberRepository.findAllByUser_IdAndIsActiveTrue(userId);
        List<Long> workspaceIds = memberships.stream()
                .map(m -> m.getWorkspace().getId())
                .toList();

        WorkspaceEvent event = workspaceEventRepository.findByIdWithWorkspace(eventId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "이벤트를 찾을 수 없습니다."));

        boolean canRead = event.getTargetUserId() != null
                ? Objects.equals(event.getTargetUserId(), userId)
                : workspaceIds.contains(event.getWorkspace().getId());
        if (!canRead) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }

        if (!workspaceEventReadStatusRepository.existsByWorkspaceEventIdAndUserId(eventId, userId)) {
            workspaceEventReadStatusRepository.save(WorkspaceEventReadStatus.create(eventId, userId));
        }
    }

    private Map<Long, Long> toCountMap(List<Object[]> rows) {
        Map<Long, Long> map = new HashMap<>();
        for (Object[] row : rows) {
            map.put((Long) row[0], (Long) row[1]);
        }
        return map;
    }

    private User findUser(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "사용자를 찾을 수 없습니다."));
    }
}
