package com.team1.codedock.domain.ai.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.team1.codedock.domain.ai.dto.AiSummaryResponse;
import com.team1.codedock.domain.ai.entity.AiSummary;
import com.team1.codedock.domain.ai.repository.AiSummaryRepository;
import com.team1.codedock.domain.github.entity.GithubRepository;
import com.team1.codedock.domain.github.service.GithubApiClient;
import com.team1.codedock.domain.pr.entity.PullRequestFile;
import com.team1.codedock.domain.pr.entity.GithubPullRequest;
import com.team1.codedock.domain.pr.repository.GithubPullRequestRepository;
import com.team1.codedock.domain.pr.repository.PullRequestFileRepository;
import com.team1.codedock.domain.workspace.repository.WorkspaceMemberRepository;
import com.team1.codedock.global.exception.BusinessException;
import com.team1.codedock.global.exception.ErrorCode;
import com.team1.codedock.global.security.CustomUserDetails;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class AiSummaryService {

    private final AiSummaryRepository aiSummaryRepository;
    private final GithubPullRequestRepository githubPullRequestRepository;
    private final PullRequestFileRepository pullRequestFileRepository;
    private final WorkspaceMemberRepository workspaceMemberRepository;
    private final GeminiClient geminiClient;
    private final GithubApiClient githubApiClient;
    private final ObjectMapper objectMapper;

    public AiSummaryResponse generateSummary(Long workspaceId, Long prId) {
        Long userId = getCurrentUserId();

        workspaceMemberRepository.findByWorkspace_IdAndUser_IdAndIsActiveTrue(workspaceId, userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.WORKSPACE_MEMBER_NOT_FOUND));

        GithubPullRequest pr = githubPullRequestRepository.findByIdAndRepository_Workspace_Id(prId, workspaceId)
                .orElseThrow(() -> new BusinessException(ErrorCode.GITHUB_PR_NOT_FOUND));

        return generateSummaryInternal(pr, prId);
    }

    public void generateSummaryForWebhook(Long prId) {
        try {
            // 이미 완료된 요약이 있으면 재생성하지 않는다(LLM 호출 비용 절약, 멱등).
            // 미완료/실패/빈 요약만 (재)생성해 sync 시 자동 복구되게 한다.
            boolean alreadyDone = aiSummaryRepository.findByGithubPullRequest_Id(prId)
                    .filter(s -> "completed".equals(s.getStatus()) && s.getSummary() != null)
                    .isPresent();
            if (alreadyDone) {
                return;
            }
            githubPullRequestRepository.findById(prId).ifPresent(pr -> generateSummaryInternal(pr, prId));
        } catch (Exception e) {
            log.warn("Webhook AI 요약 생성 실패 → prId={}", prId, e);
        }
    }

    private AiSummaryResponse generateSummaryInternal(GithubPullRequest pr, Long prId) {
        aiSummaryRepository.findByGithubPullRequest_Id(prId)
                .ifPresent(aiSummaryRepository::delete);

        AiSummary aiSummary = aiSummaryRepository.save(AiSummary.create(pr));
        aiSummary.startProcessing();

        List<PullRequestFile> files = pullRequestFileRepository.findAllByGithubPullRequest_Id(prId);
        // webhook 시점에 토큰/타이밍 문제로 파일이 저장되지 않았으면, 여기서 GitHub에서 보강한다.
        // (이 보강이 없으면 빈 diff로 분석돼 "AI 요약 정보 없음"이 영구히 남는다)
        if (files.isEmpty()) {
            files = fetchAndSavePullRequestFiles(pr, prId);
        }
        String combinedDiff = buildCombinedDiff(files);

        try {
            GeminiClient.PrAnalysisResult result = geminiClient.generatePrAnalysis(combinedDiff);
            GeminiClient.PrAnalysisResult normalized = normalizeResult(result);
            String summaryJson = objectMapper.writeValueAsString(normalized);
            aiSummary.complete(summaryJson, normalizeRiskLevel(normalized.riskLevel()), geminiClient.getModel());
        } catch (Exception e) {
            aiSummary.fail();
            throw new BusinessException(ErrorCode.AI_ANALYSIS_FAILED);
        }

        return toResponse(aiSummary);
    }

    @Transactional(readOnly = true)
    public AiSummaryResponse getSummary(Long workspaceId, Long prId) {
        githubPullRequestRepository.findByIdAndRepository_Workspace_Id(prId, workspaceId)
                .orElseThrow(() -> new BusinessException(ErrorCode.GITHUB_PR_NOT_FOUND));

        AiSummary aiSummary = aiSummaryRepository.findByGithubPullRequest_Id(prId)
                .orElseThrow(() -> new BusinessException(ErrorCode.AI_SUMMARY_NOT_FOUND));

        return toResponse(aiSummary);
    }

    // pull_request_files가 비어 있을 때 GitHub API에서 PR 파일 diff를 가져와 저장하고 반환한다.
    // 워크스페이스 멤버 중 유효한 GitHub 토큰을 사용한다(없으면 빈 목록).
    private List<PullRequestFile> fetchAndSavePullRequestFiles(GithubPullRequest pr, Long prId) {
        GithubRepository repo = pr.getRepository();
        if (repo == null || repo.getWorkspace() == null) {
            return List.of();
        }
        String token = workspaceMemberRepository
                .findAllByWorkspace_IdAndIsActiveTrue(repo.getWorkspace().getId())
                .stream()
                .map(m -> m.getUser().getGithubAccessToken())
                .filter(t -> t != null && !t.isBlank())
                .findFirst()
                .orElse(null);
        if (token == null) {
            log.warn("AI 요약용 PR 파일 보강 실패: GitHub 토큰 없음 → prId={}", prId);
            return List.of();
        }
        try {
            List<GithubApiClient.GithubPrFileItem> fetched = githubApiClient.fetchPullRequestFiles(
                    repo.getOwner(), repo.getName(), pr.getPrNumber(), token);
            if (fetched.isEmpty()) {
                return List.of();
            }
            List<PullRequestFile> toSave = fetched.stream()
                    .map(f -> PullRequestFile.create(pr, f.filename(), f.status(),
                            f.additions() != null ? f.additions() : 0,
                            f.deletions() != null ? f.deletions() : 0,
                            f.filename(), f.patch()))
                    .toList();
            return pullRequestFileRepository.saveAll(toSave);
        } catch (Exception e) {
            log.warn("AI 요약용 PR 파일 보강 실패 → prId={}", prId, e);
            return List.of();
        }
    }

    private String buildCombinedDiff(List<PullRequestFile> files) {
        return files.stream()
                .filter(f -> f.getPatch() != null)
                .map(f -> "### " + f.getFilename() + "\n" + f.getPatch())
                .collect(Collectors.joining("\n\n"));
    }

    private AiSummaryResponse toResponse(AiSummary aiSummary) {
        if (!"completed".equals(aiSummary.getStatus()) || aiSummary.getSummary() == null) {
            return new AiSummaryResponse(
                    aiSummary.getId(),
                    aiSummary.getGithubPullRequest().getId(),
                    aiSummary.getStatus(),
                    aiSummary.getRiskLevel(),
                    null, null, null, null,
                    aiSummary.getCreatedAt(),
                    aiSummary.getUpdatedAt()
            );
        }

        try {
            GeminiClient.PrAnalysisResult result = objectMapper.readValue(
                    aiSummary.getSummary(), GeminiClient.PrAnalysisResult.class);
            return new AiSummaryResponse(
                    aiSummary.getId(),
                    aiSummary.getGithubPullRequest().getId(),
                    aiSummary.getStatus(),
                    aiSummary.getRiskLevel(),
                    result.summaryText(),
                    result.cautionItems(),
                    result.positiveItems(),
                    result.fileFeedbacks(),
                    aiSummary.getCreatedAt(),
                    aiSummary.getUpdatedAt()
            );
        } catch (Exception e) {
            throw new RuntimeException("AI 요약 파싱 실패", e);
        }
    }

    private GeminiClient.PrAnalysisResult normalizeResult(GeminiClient.PrAnalysisResult result) {
        List<GeminiClient.PrFileFeedback> normalizedFiles = null;
        if (result.fileFeedbacks() != null) {
            normalizedFiles = result.fileFeedbacks().stream()
                    .map(f -> new GeminiClient.PrFileFeedback(
                            f.name(), f.path(), normalizeFileRisk(f.risk()),
                            f.vulnerability(), f.fix(),
                            f.currentCode(), f.recommendedCode(), f.findings()))
                    .toList();
        }
        return new GeminiClient.PrAnalysisResult(
                result.summaryText(), result.cautionItems(), result.positiveItems(),
                result.riskLevel(), normalizedFiles);
    }

    private String normalizeRiskLevel(String riskLevel) {
        if (riskLevel == null) return null;
        return switch (riskLevel.toUpperCase()) {
            case "HIGH" -> "High";
            case "MEDIUM" -> "Medium";
            case "LOW" -> "Low";
            default -> null;
        };
    }

    private String normalizeFileRisk(String risk) {
        if (risk == null) return null;
        return switch (risk.toUpperCase()) {
            case "HIGH" -> "High";
            case "MEDIUM" -> "Medium";
            case "LOW" -> "Low";
            default -> null;
        };
    }

    private Long getCurrentUserId() {
        CustomUserDetails userDetails = (CustomUserDetails) SecurityContextHolder
                .getContext().getAuthentication().getPrincipal();
        return userDetails.getUserId();
    }
}
