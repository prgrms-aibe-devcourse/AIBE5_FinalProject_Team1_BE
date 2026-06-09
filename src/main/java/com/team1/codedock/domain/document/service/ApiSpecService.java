package com.team1.codedock.domain.document.service;

import com.team1.codedock.domain.document.dto.ApiSpecCreateRequest;
import com.team1.codedock.domain.document.dto.ApiSpecResponse;
import com.team1.codedock.domain.document.dto.ApiSpecUpdateRequest;
import com.team1.codedock.domain.document.entity.ApiSpec;
import com.team1.codedock.domain.document.repository.ApiSpecRepository;
import com.team1.codedock.domain.issue.entity.GithubIssue;
import com.team1.codedock.domain.issue.repository.GithubIssueRepository;
import com.team1.codedock.domain.pr.entity.GithubPullRequest;
import com.team1.codedock.domain.pr.repository.GithubPullRequestRepository;
import com.team1.codedock.domain.workspace.entity.Workspace;
import com.team1.codedock.domain.workspace.entity.WorkspaceMember;
import com.team1.codedock.domain.workspace.repository.WorkspaceMemberRepository;
import com.team1.codedock.domain.workspace.repository.WorkspaceRepository;
import com.team1.codedock.global.exception.BusinessException;
import com.team1.codedock.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ApiSpecService {

    private final ApiSpecRepository apiSpecRepository;
    private final WorkspaceRepository workspaceRepository;
    private final WorkspaceMemberRepository workspaceMemberRepository;
    private final GithubIssueRepository githubIssueRepository;
    private final GithubPullRequestRepository githubPullRequestRepository;

    @Transactional
    public ApiSpecResponse createApiSpec(Long workspaceId, ApiSpecCreateRequest request) {
        Workspace workspace = workspaceRepository.findById(workspaceId)
                .orElseThrow(() -> new BusinessException(ErrorCode.WORKSPACE_NOT_FOUND));

        WorkspaceMember createdBy = workspaceMemberRepository.findByIdAndWorkspace_Id(request.createdByMemberId(), workspaceId)
                .orElseThrow(() -> new BusinessException(ErrorCode.WORKSPACE_MEMBER_NOT_FOUND));

        WorkspaceMember assignee = null;
        if (request.assigneeId() != null) {
            assignee = workspaceMemberRepository.findByIdAndWorkspace_Id(request.assigneeId(), workspaceId)
                    .orElseThrow(() -> new BusinessException(ErrorCode.WORKSPACE_MEMBER_NOT_FOUND));
        }

        GithubIssue relatedIssue = null;
        if (request.relatedIssueId() != null) {
            relatedIssue = githubIssueRepository.findByIdAndRepository_Workspace_Id(request.relatedIssueId(), workspaceId)
                    .orElseThrow(() -> new BusinessException(ErrorCode.GITHUB_ISSUE_NOT_FOUND));
        }

        GithubPullRequest relatedPr = null;
        if (request.relatedPrId() != null) {
            relatedPr = githubPullRequestRepository.findByIdAndRepository_Workspace_Id(request.relatedPrId(), workspaceId)
                    .orElseThrow(() -> new BusinessException(ErrorCode.GITHUB_PR_NOT_FOUND));
        }

        ApiSpec spec = ApiSpec.create(
                workspace, createdBy,
                request.title(), request.method(), request.endpoint(),
                request.groupName(), request.entityName(), request.summary(), request.description(),
                request.status(), assignee,
                request.pathParams(), request.headers(), request.queryParams(),
                request.requestBody(), request.responseBody(), request.responseStatus(),
                request.version(), request.sourceType(),
                relatedIssue, relatedPr, request.note()
        );

        return ApiSpecResponse.from(apiSpecRepository.save(spec));
    }

    public List<ApiSpecResponse> getApiSpecs(Long workspaceId, String groupName, String status) {
        List<ApiSpec> specs;
        if (groupName != null && !groupName.isBlank()) {
            specs = apiSpecRepository.findAllByWorkspace_IdAndGroupNameOrderByCreatedAtDesc(workspaceId, groupName);
        } else if (status != null && !status.isBlank()) {
            specs = apiSpecRepository.findAllByWorkspace_IdAndStatusOrderByCreatedAtDesc(workspaceId, status);
        } else {
            specs = apiSpecRepository.findAllByWorkspace_IdOrderByCreatedAtDesc(workspaceId);
        }
        return specs.stream().map(ApiSpecResponse::from).toList();
    }

    public ApiSpecResponse getApiSpec(Long workspaceId, Long apiSpecId) {
        ApiSpec spec = apiSpecRepository.findByIdAndWorkspace_Id(apiSpecId, workspaceId)
                .orElseThrow(() -> new BusinessException(ErrorCode.API_SPEC_NOT_FOUND));
        return ApiSpecResponse.from(spec);
    }

    @Transactional
    public ApiSpecResponse updateApiSpec(Long workspaceId, Long apiSpecId, ApiSpecUpdateRequest request) {
        ApiSpec spec = apiSpecRepository.findByIdAndWorkspace_Id(apiSpecId, workspaceId)
                .orElseThrow(() -> new BusinessException(ErrorCode.API_SPEC_NOT_FOUND));

        WorkspaceMember assignee = null;
        if (request.assigneeId() != null) {
            assignee = workspaceMemberRepository.findByIdAndWorkspace_Id(request.assigneeId(), workspaceId)
                    .orElseThrow(() -> new BusinessException(ErrorCode.WORKSPACE_MEMBER_NOT_FOUND));
        }

        GithubIssue relatedIssue = null;
        if (request.relatedIssueId() != null) {
            relatedIssue = githubIssueRepository.findByIdAndRepository_Workspace_Id(request.relatedIssueId(), workspaceId)
                    .orElseThrow(() -> new BusinessException(ErrorCode.GITHUB_ISSUE_NOT_FOUND));
        }

        GithubPullRequest relatedPr = null;
        if (request.relatedPrId() != null) {
            relatedPr = githubPullRequestRepository.findByIdAndRepository_Workspace_Id(request.relatedPrId(), workspaceId)
                    .orElseThrow(() -> new BusinessException(ErrorCode.GITHUB_PR_NOT_FOUND));
        }

        spec.update(
                request.title(), request.method(), request.endpoint(),
                request.groupName(), request.entityName(), request.summary(), request.description(),
                request.status(), assignee,
                request.pathParams(), request.headers(), request.queryParams(),
                request.requestBody(), request.responseBody(), request.responseStatus(),
                request.version(), request.sourceType(),
                relatedIssue, relatedPr, request.note()
        );

        return ApiSpecResponse.from(spec);
    }

    @Transactional
    public void deleteApiSpec(Long workspaceId, Long apiSpecId) {
        ApiSpec spec = apiSpecRepository.findByIdAndWorkspace_Id(apiSpecId, workspaceId)
                .orElseThrow(() -> new BusinessException(ErrorCode.API_SPEC_NOT_FOUND));
        apiSpecRepository.delete(spec);
    }
}
