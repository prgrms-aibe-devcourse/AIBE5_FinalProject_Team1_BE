package com.team1.codedock.domain.issue.service;

import com.team1.codedock.domain.issue.dto.IssueLocalStatusUpdateRequest;
import com.team1.codedock.domain.issue.dto.IssueResponse;
import com.team1.codedock.domain.issue.entity.GithubIssue;
import com.team1.codedock.domain.issue.entity.IssueAssignee;
import com.team1.codedock.domain.issue.entity.IssueLabel;
import com.team1.codedock.domain.issue.repository.GithubIssueRepository;
import com.team1.codedock.domain.issue.repository.IssueAssigneeRepository;
import com.team1.codedock.domain.issue.repository.IssueLabelRepository;
import com.team1.codedock.domain.workspace.repository.WorkspaceMemberRepository;
import com.team1.codedock.global.exception.BusinessException;
import com.team1.codedock.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class IssueService {

    private static final String LOCAL_STATUS_DONE = "done";

    private final GithubIssueRepository githubIssueRepository;
    private final IssueLabelRepository issueLabelRepository;
    private final IssueAssigneeRepository issueAssigneeRepository;
    private final WorkspaceMemberRepository workspaceMemberRepository;

    @Transactional(readOnly = true)
    public List<IssueResponse> getWorkspaceIssues(Long workspaceId, Long userId) {
        validateWorkspaceMember(workspaceId, userId);

        List<GithubIssue> issues = githubIssueRepository.findAllByWorkspaceId(workspaceId);
        if (issues.isEmpty()) {
            return List.of();
        }

        // 이슈별 라벨/담당자를 각각 1회 배치 조회한 뒤 이슈 id로 묶어 매핑한다.
        // 기존의 이슈당 2쿼리(N+1)를 전체 2쿼리로 줄인다.
        List<Long> issueIds = issues.stream().map(GithubIssue::getId).toList();
        Map<Long, List<IssueLabel>> labelsByIssueId = issueLabelRepository.findAllByGithubIssue_IdIn(issueIds).stream()
                .collect(Collectors.groupingBy(label -> label.getGithubIssue().getId()));
        Map<Long, List<IssueAssignee>> assigneesByIssueId = issueAssigneeRepository.findAllByIssueIdInFetchUser(issueIds).stream()
                .collect(Collectors.groupingBy(assignee -> assignee.getGithubIssue().getId()));

        return issues.stream()
                .map(issue -> IssueResponse.from(
                        issue,
                        labelsByIssueId.getOrDefault(issue.getId(), List.of()),
                        assigneesByIssueId.getOrDefault(issue.getId(), List.of())))
                .toList();
    }

    @Transactional(readOnly = true)
    public IssueResponse getIssue(Long workspaceId, Long issueId, Long userId) {
        validateWorkspaceMember(workspaceId, userId);
        GithubIssue issue = githubIssueRepository.findByIdAndRepository_Workspace_Id(issueId, workspaceId)
                .orElseThrow(() -> new BusinessException(ErrorCode.GITHUB_ISSUE_NOT_FOUND));
        List<IssueLabel> labels = issueLabelRepository.findAllByGithubIssue_Id(issueId);
        List<IssueAssignee> assignees = issueAssigneeRepository.findAllByGithubIssue_Id(issueId);
        return IssueResponse.from(issue, labels, assignees);
    }

    @Transactional
    public IssueResponse updateLocalStatus(Long workspaceId, Long issueId, Long userId, IssueLocalStatusUpdateRequest request) {
        validateWorkspaceMember(workspaceId, userId);
        GithubIssue issue = githubIssueRepository.findByIdAndRepository_Workspace_Id(issueId, workspaceId)
                .orElseThrow(() -> new BusinessException(ErrorCode.GITHUB_ISSUE_NOT_FOUND));
        validateLocalStatusChange(issue, request.localStatus());
        issue.updateLocalStatus(request.localStatus());
        List<IssueLabel> labels = issueLabelRepository.findAllByGithubIssue_Id(issueId);
        List<IssueAssignee> assignees = issueAssigneeRepository.findAllByGithubIssue_Id(issueId);
        return IssueResponse.from(issue, labels, assignees);
    }

    private void validateLocalStatusChange(GithubIssue issue, String localStatus) {
        if (issue.isClosed() && !LOCAL_STATUS_DONE.equals(localStatus)) {
            throw new BusinessException(
                    ErrorCode.INVALID_INPUT,
                    "GitHub에서 닫힌 이슈는 완료 상태로만 유지할 수 있습니다."
            );
        }
    }

    private void validateWorkspaceMember(Long workspaceId, Long userId) {
        if (userId == null) throw new BusinessException(ErrorCode.UNAUTHORIZED);
        workspaceMemberRepository.findByWorkspace_IdAndUser_IdAndIsActiveTrue(workspaceId, userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.FORBIDDEN));
    }
}
