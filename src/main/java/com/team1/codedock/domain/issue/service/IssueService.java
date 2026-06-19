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

@Service
@RequiredArgsConstructor
public class IssueService {

    private final GithubIssueRepository githubIssueRepository;
    private final IssueLabelRepository issueLabelRepository;
    private final IssueAssigneeRepository issueAssigneeRepository;
    private final WorkspaceMemberRepository workspaceMemberRepository;

    @Transactional(readOnly = true)
    public List<IssueResponse> getWorkspaceIssues(Long workspaceId, Long userId) {
        validateWorkspaceMember(workspaceId, userId);
        return githubIssueRepository.findAllByWorkspaceId(workspaceId).stream()
                .map(issue -> {
                    List<IssueLabel> labels = issueLabelRepository.findAllByGithubIssue_Id(issue.getId());
                    List<IssueAssignee> assignees = issueAssigneeRepository.findAllByGithubIssue_Id(issue.getId());
                    return IssueResponse.from(issue, labels, assignees);
                })
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
        issue.updateLocalStatus(request.localStatus());
        List<IssueLabel> labels = issueLabelRepository.findAllByGithubIssue_Id(issueId);
        List<IssueAssignee> assignees = issueAssigneeRepository.findAllByGithubIssue_Id(issueId);
        return IssueResponse.from(issue, labels, assignees);
    }

    private void validateWorkspaceMember(Long workspaceId, Long userId) {
        if (userId == null) throw new BusinessException(ErrorCode.UNAUTHORIZED);
        workspaceMemberRepository.findByWorkspace_IdAndUser_IdAndIsActiveTrue(workspaceId, userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.FORBIDDEN));
    }
}
