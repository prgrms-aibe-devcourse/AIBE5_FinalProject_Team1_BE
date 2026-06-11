package com.team1.codedock.domain.document.service;

import com.team1.codedock.domain.ai.service.GeminiClient;
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
    public DocumentResponse generateDocument(Long workspaceId) {
        Long userId = SecurityUtils.getCurrentUserId();

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        WorkspaceMember member = workspaceMemberRepository
                .findByWorkspace_IdAndUser_IdAndIsActiveTrue(workspaceId, userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.WORKSPACE_MEMBER_NOT_FOUND));

        GithubRepository githubRepo = githubRepositoryRepository.findFirstByWorkspace_Id(workspaceId)
                .orElseThrow(() -> new BusinessException(ErrorCode.GITHUB_REPO_NOT_FOUND));

        List<String> entitySources = githubApiClient.fetchEntitySources(
                githubRepo.getOwner(),
                githubRepo.getName(),
                githubRepo.getDefaultBranch(),
                user.getGithubAccessToken()
        );

        GeminiClient.DocumentGenerationResult result = geminiClient.generateDocument(entitySources);

        Document document = documentRepository.save(
                Document.createFromAi(member.getWorkspace(), member, result.title(), result.content(), result.category())
        );

        return DocumentResponse.from(document);
    }
}
