package com.team1.codedock.domain.document.service;

import com.team1.codedock.domain.ai.service.GeminiClient;
import com.team1.codedock.domain.document.dto.ErdDocumentResponse;
import com.team1.codedock.domain.document.dto.ErdTableResponse;
import com.team1.codedock.domain.document.entity.ErdDocument;
import com.team1.codedock.domain.document.entity.ErdTable;
import com.team1.codedock.domain.document.repository.ErdDocumentRepository;
import com.team1.codedock.domain.document.repository.ErdTableRepository;
import com.team1.codedock.domain.github.repository.GithubRepositoryRepository;
import com.team1.codedock.domain.github.service.GithubApiClient;
import com.team1.codedock.domain.github.entity.GithubRepository;
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
@Transactional(readOnly = true)
public class ErdService {

    private final ErdDocumentRepository erdDocumentRepository;
    private final ErdTableRepository erdTableRepository;
    private final WorkspaceMemberRepository workspaceMemberRepository;
    private final UserRepository userRepository;
    private final GithubRepositoryRepository githubRepositoryRepository;
    private final GithubApiClient githubApiClient;
    private final GeminiClient geminiClient;

    @Transactional
    public ErdDocumentResponse generateErd(Long workspaceId) {
        Long userId = SecurityUtils.getCurrentUserId();

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        WorkspaceMember member = workspaceMemberRepository
                .findByWorkspace_IdAndUser_IdAndIsActiveTrue(workspaceId, userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.WORKSPACE_MEMBER_NOT_FOUND));

        List<GithubRepository> githubRepos = githubRepositoryRepository.findByWorkspaceId(workspaceId)
                .stream().limit(3).toList();
        if (githubRepos.isEmpty()) throw new BusinessException(ErrorCode.GITHUB_REPO_NOT_FOUND);

        String token = user.getGithubAccessToken();
        List<String> repoSources = githubRepos.stream()
                .flatMap(repo -> githubApiClient.fetchRepoSources(
                        repo.getOwner(), repo.getName(), repo.getDefaultBranch(), token).stream())
                .toList();

        if (repoSources.isEmpty()) {
            throw new BusinessException(ErrorCode.ERD_SOURCE_NOT_FOUND);
        }

        GeminiClient.ErdGenerationResult result = geminiClient.generateErd(repoSources);

        ErdDocument erdDocument = erdDocumentRepository
                .findByWorkspace_IdAndDeletedAtIsNull(workspaceId)
                .map(doc -> {
                    doc.update("ERD", null, result.mermaidCode());
                    return doc;
                })
                .orElseGet(() -> erdDocumentRepository.save(
                        ErdDocument.create(member.getWorkspace(), member, "ERD", null, result.mermaidCode())
                ));

        erdTableRepository.deleteAllByWorkspace_Id(workspaceId);
        if (result.tables() != null) {
            result.tables().forEach(t -> erdTableRepository.save(
                    ErdTable.create(member.getWorkspace(), member, t.tableName(), t.schemaDefinition(), t.description())
            ));
        }

        return ErdDocumentResponse.from(erdDocument);
    }

    public ErdDocumentResponse getErd(Long workspaceId) {
        ErdDocument doc = erdDocumentRepository
                .findByWorkspace_IdAndDeletedAtIsNull(workspaceId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ERD_NOT_FOUND));
        return ErdDocumentResponse.from(doc);
    }

    public List<ErdTableResponse> getErdTables(Long workspaceId) {
        return erdTableRepository.findAllByWorkspace_Id(workspaceId)
                .stream()
                .map(ErdTableResponse::from)
                .toList();
    }
}
