package com.team1.codedock.domain.document.service;

import com.team1.codedock.domain.ai.service.GeminiClient;
import com.team1.codedock.domain.document.dto.DocumentAiGenerateRequest;
import com.team1.codedock.domain.document.dto.DocumentResponse;
import com.team1.codedock.domain.document.entity.Document;
import com.team1.codedock.domain.document.repository.DocumentRepository;
import com.team1.codedock.domain.github.entity.GithubRepository;
import com.team1.codedock.domain.github.repository.GithubRepositoryRepository;
import com.team1.codedock.domain.github.service.GithubApiClient;
import com.team1.codedock.domain.user.entity.User;
import com.team1.codedock.domain.user.repository.UserRepository;
import com.team1.codedock.domain.workspace.entity.WorkspaceMember;
import com.team1.codedock.domain.workspace.repository.WorkspaceMemberRepository;
import com.team1.codedock.global.exception.BusinessException;
import com.team1.codedock.global.exception.ErrorCode;
import com.team1.codedock.global.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Service
@RequiredArgsConstructor
public class DocumentAiService {

    private final DocumentRepository documentRepository;
    private final WorkspaceMemberRepository workspaceMemberRepository;
    private final UserRepository userRepository;
    private final GithubRepositoryRepository githubRepositoryRepository;
    private final GithubApiClient githubApiClient;
    private final GeminiClient geminiClient;

    @Transactional
    public DocumentResponse generateDocument(Long workspaceId, DocumentAiGenerateRequest request) {
        Long userId = SecurityUtils.getCurrentUserId();

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        WorkspaceMember member = workspaceMemberRepository
                .findByWorkspace_IdAndUser_IdAndIsActiveTrue(workspaceId, userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.WORKSPACE_MEMBER_NOT_FOUND));

        GithubRepository githubRepo = githubRepositoryRepository.findByWorkspaceId(workspaceId)
                .stream().findFirst()
                .orElseThrow(() -> new BusinessException(ErrorCode.GITHUB_REPO_NOT_FOUND));

        String owner = githubRepo.getOwner();
        String repo = githubRepo.getName();
        String branch = githubRepo.getDefaultBranch();
        String token = user.getGithubAccessToken();

        List<String> sources;
        List<String> commits;

        if ("release".equals(request.category())) {
            if (request.startDate() == null || request.endDate() == null) {
                throw new BusinessException(ErrorCode.INVALID_INPUT);
            }
            LocalDate startDate = request.startDate();
            LocalDate endDate = request.endDate();
            if (endDate.isBefore(startDate)) {
                throw new BusinessException(ErrorCode.INVALID_DATE_RANGE);
            }
            if (ChronoUnit.DAYS.between(startDate, endDate) > 6) {
                throw new BusinessException(ErrorCode.DATE_RANGE_TOO_LONG);
            }
            commits = githubApiClient.fetchCommits(owner, repo, branch, token, startDate, endDate);
            if (commits.isEmpty()) {
                throw new BusinessException(ErrorCode.NO_COMMITS_IN_RANGE);
            }
            sources = List.of();
        } else {
            if (request.topic() == null || request.topic().isBlank()) {
                throw new BusinessException(ErrorCode.TOPIC_REQUIRED);
            }
            sources = githubApiClient.fetchControllerSources(owner, repo, branch, token);
            if (sources.isEmpty()) {
                sources = githubApiClient.fetchSourcesByKeyword(owner, repo, branch, token, request.topic());
            }
            commits = List.of();
        }

        GeminiClient.DocumentGenerationResult result = geminiClient.generateDocument(
                sources, request.category(), request.topic(), commits);

        Document document = documentRepository.save(
                Document.createFromAi(member.getWorkspace(), member, result.title(), result.content(), result.category()));

        return DocumentResponse.from(document);
    }
}
