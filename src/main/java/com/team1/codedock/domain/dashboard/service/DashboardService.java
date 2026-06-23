package com.team1.codedock.domain.dashboard.service;

import com.team1.codedock.domain.dashboard.dto.DashboardSummaryResponse;
import com.team1.codedock.domain.dashboard.dto.WorkspaceDashboardResponse;
import com.team1.codedock.domain.issue.repository.IssueAssigneeRepository;
import com.team1.codedock.domain.pr.repository.GithubPullRequestRepository;
import com.team1.codedock.domain.pr.repository.PullRequestReviewRepository;
import com.team1.codedock.domain.pr.repository.PullRequestReviewRequestRepository;
import com.team1.codedock.domain.user.entity.User;
import com.team1.codedock.domain.workspace.entity.WorkspaceMember;
import com.team1.codedock.domain.workspace.repository.WorkspaceMemberRepository;
import com.team1.codedock.global.exception.BusinessException;
import com.team1.codedock.global.exception.ErrorCode;
import com.team1.codedock.domain.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class DashboardService {

    private final WorkspaceMemberRepository workspaceMemberRepository;
    private final UserRepository userRepository;
    private final IssueAssigneeRepository issueAssigneeRepository;
    private final GithubPullRequestRepository githubPullRequestRepository;
    private final PullRequestReviewRequestRepository pullRequestReviewRequestRepository;
    private final PullRequestReviewRepository pullRequestReviewRepository;

    public DashboardSummaryResponse getSummary(Long userId) {
        User user = findUser(userId);
        String githubUsername = user.getGithubUsername();
        List<WorkspaceMember> memberships = workspaceMemberRepository.findAllByUser_IdAndIsActiveTrue(userId);

        long openIssueCount = 0;
        long openPrCount = 0;
        long reviewRequestCount = 0;
        long receivedReviewCount = 0;

        for (WorkspaceMember member : memberships) {
            Long workspaceId = member.getWorkspace().getId();
            openIssueCount += issueAssigneeRepository.countOpenByUserIdAndWorkspaceId(userId, workspaceId);
            reviewRequestCount += pullRequestReviewRequestRepository.countByUserIdAndWorkspaceId(userId, workspaceId);
            if (githubUsername != null) {
                openPrCount += githubPullRequestRepository.countOpenByWorkspaceIdAndAuthor(workspaceId, githubUsername);
                receivedReviewCount += pullRequestReviewRepository.countOnOpenPrsByAuthorAndWorkspaceId(githubUsername, workspaceId);
            }
        }

        return DashboardSummaryResponse.of(openIssueCount, openPrCount, reviewRequestCount, receivedReviewCount);
    }

    public List<WorkspaceDashboardResponse> getWorkspaceStats(Long userId) {
        User user = findUser(userId);
        String githubUsername = user.getGithubUsername();
        List<WorkspaceMember> memberships = workspaceMemberRepository.findAllByUser_IdAndIsActiveTrue(userId);

        return memberships.stream()
                .map(member -> buildWorkspaceStats(member, userId, githubUsername))
                .toList();
    }

    private WorkspaceDashboardResponse buildWorkspaceStats(WorkspaceMember member, Long userId, String githubUsername) {
        Long workspaceId = member.getWorkspace().getId();

        long openIssueCount = issueAssigneeRepository.countOpenByUserIdAndWorkspaceId(userId, workspaceId);
        long reviewRequestCount = pullRequestReviewRequestRepository.countByUserIdAndWorkspaceId(userId, workspaceId);
        long openPrCount = githubUsername != null
                ? githubPullRequestRepository.countOpenByWorkspaceIdAndAuthor(workspaceId, githubUsername)
                : 0;
        long receivedReviewCount = githubUsername != null
                ? pullRequestReviewRepository.countOnOpenPrsByAuthorAndWorkspaceId(githubUsername, workspaceId)
                : 0;

        return new WorkspaceDashboardResponse(
                workspaceId,
                member.getWorkspace().getName(),
                member.getWorkspace().getLogoUrl(),
                openIssueCount,
                openPrCount,
                reviewRequestCount,
                receivedReviewCount
        );
    }

    private User findUser(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "사용자를 찾을 수 없습니다."));
    }
}
