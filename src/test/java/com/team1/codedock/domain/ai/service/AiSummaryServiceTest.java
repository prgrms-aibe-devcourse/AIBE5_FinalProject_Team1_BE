package com.team1.codedock.domain.ai.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.team1.codedock.domain.ai.dto.AiSummaryResponse;
import com.team1.codedock.domain.ai.entity.AiSummary;
import com.team1.codedock.domain.ai.repository.AiSummaryRepository;
import com.team1.codedock.domain.pr.entity.GithubPullRequest;
import com.team1.codedock.domain.pr.entity.PullRequestFile;
import com.team1.codedock.domain.pr.repository.GithubPullRequestRepository;
import com.team1.codedock.domain.pr.repository.PullRequestFileRepository;
import com.team1.codedock.domain.workspace.entity.WorkspaceMember;
import com.team1.codedock.domain.workspace.repository.WorkspaceMemberRepository;
import com.team1.codedock.global.exception.BusinessException;
import com.team1.codedock.global.exception.ErrorCode;
import com.team1.codedock.global.security.CustomUserDetails;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AiSummaryServiceTest {

    @Mock private AiSummaryRepository aiSummaryRepository;
    @Mock private GithubPullRequestRepository githubPullRequestRepository;
    @Mock private PullRequestFileRepository pullRequestFileRepository;
    @Mock private WorkspaceMemberRepository workspaceMemberRepository;
    @Mock private GeminiClient geminiClient;
    @Spy private ObjectMapper objectMapper = new ObjectMapper();

    private AiSummaryService aiSummaryService;

    @BeforeEach
    void setUp() {
        CustomUserDetails userDetails = mock(CustomUserDetails.class);
        lenient().when(userDetails.getUserId()).thenReturn(1L);
        Authentication auth = mock(Authentication.class);
        lenient().when(auth.isAuthenticated()).thenReturn(true);
        lenient().when(auth.getPrincipal()).thenReturn(userDetails);
        SecurityContextHolder.getContext().setAuthentication(auth);

        aiSummaryService = new AiSummaryService(
                aiSummaryRepository, githubPullRequestRepository, pullRequestFileRepository,
                workspaceMemberRepository, geminiClient, objectMapper
        );
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private GithubPullRequest mockPr() {
        GithubPullRequest pr = mock(GithubPullRequest.class);
        lenient().when(pr.getId()).thenReturn(1L);
        return pr;
    }

    private PullRequestFile mockFile(String filename, String patch) {
        PullRequestFile file = mock(PullRequestFile.class);
        lenient().when(file.getFilename()).thenReturn(filename);
        lenient().when(file.getPatch()).thenReturn(patch);
        return file;
    }

    private GeminiClient.PrAnalysisResult sampleAnalysisResult() {
        GeminiClient.PrFileFeedback feedback = new GeminiClient.PrFileFeedback(
                "TestFile.java", "src/TestFile.java", "높음",
                "취약점", "수정 방향", List.of("old code"), List.of("new code"), List.of("23번째 줄")
        );
        return new GeminiClient.PrAnalysisResult(
                "PR 요약", List.of("주의사항"), List.of("긍정적인 점"), "High", List.of(feedback)
        );
    }

    // ── generateSummary() ─────────────────────────────────────

    @Test
    @DisplayName("AI 분석 요약을 정상적으로 생성하고 completed 응답을 반환한다")
    void generateSummary_성공() {
        GithubPullRequest pr = mockPr();
        PullRequestFile file = mockFile("TestFile.java", "@@ -1,1 +1,1 @@");
        GeminiClient.PrAnalysisResult result = sampleAnalysisResult();

        when(workspaceMemberRepository.findByWorkspace_IdAndUser_IdAndIsActiveTrue(1L, 1L))
                .thenReturn(Optional.of(mock(WorkspaceMember.class)));
        when(githubPullRequestRepository.findByIdAndRepository_Workspace_Id(1L, 1L))
                .thenReturn(Optional.of(pr));
        when(aiSummaryRepository.findByGithubPullRequest_Id(1L)).thenReturn(Optional.empty());
        when(aiSummaryRepository.save(any(AiSummary.class))).thenAnswer(inv -> inv.getArgument(0));
        when(pullRequestFileRepository.findAllByGithubPullRequest_Id(1L)).thenReturn(List.of(file));
        when(geminiClient.generatePrAnalysis(any())).thenReturn(result);
        when(geminiClient.getModel()).thenReturn("gemini-2.0-flash");

        AiSummaryResponse response = aiSummaryService.generateSummary(1L, 1L);

        assertThat(response.status()).isEqualTo("completed");
        assertThat(response.riskLevel()).isEqualTo("High");
        assertThat(response.summaryText()).isEqualTo("PR 요약");
        verify(aiSummaryRepository).save(any(AiSummary.class));
        verify(aiSummaryRepository, never()).delete(any());
    }

    @Test
    @DisplayName("기존 AI 요약이 있으면 삭제 후 새로 생성한다")
    void generateSummary_기존_요약_있으면_삭제_후_재생성() {
        GithubPullRequest pr = mockPr();
        AiSummary existingSummary = mock(AiSummary.class);
        GeminiClient.PrAnalysisResult result = sampleAnalysisResult();

        when(workspaceMemberRepository.findByWorkspace_IdAndUser_IdAndIsActiveTrue(1L, 1L))
                .thenReturn(Optional.of(mock(WorkspaceMember.class)));
        when(githubPullRequestRepository.findByIdAndRepository_Workspace_Id(1L, 1L))
                .thenReturn(Optional.of(pr));
        when(aiSummaryRepository.findByGithubPullRequest_Id(1L)).thenReturn(Optional.of(existingSummary));
        when(aiSummaryRepository.save(any(AiSummary.class))).thenAnswer(inv -> inv.getArgument(0));
        when(pullRequestFileRepository.findAllByGithubPullRequest_Id(1L)).thenReturn(List.of());
        when(geminiClient.generatePrAnalysis(any())).thenReturn(result);
        when(geminiClient.getModel()).thenReturn("gemini-2.0-flash");

        AiSummaryResponse response = aiSummaryService.generateSummary(1L, 1L);

        verify(aiSummaryRepository).delete(existingSummary);
        verify(aiSummaryRepository).save(any(AiSummary.class));
        assertThat(response.status()).isEqualTo("completed");
    }

    @Test
    @DisplayName("워크스페이스 멤버가 없으면 예외가 발생한다")
    void generateSummary_워크스페이스_멤버_없으면_예외() {
        when(workspaceMemberRepository.findByWorkspace_IdAndUser_IdAndIsActiveTrue(1L, 1L))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> aiSummaryService.generateSummary(1L, 1L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining(ErrorCode.WORKSPACE_MEMBER_NOT_FOUND.getMessage());

        verify(aiSummaryRepository, never()).save(any());
    }

    @Test
    @DisplayName("PR을 찾을 수 없으면 예외가 발생한다")
    void generateSummary_PR_없으면_예외() {
        when(workspaceMemberRepository.findByWorkspace_IdAndUser_IdAndIsActiveTrue(1L, 1L))
                .thenReturn(Optional.of(mock(WorkspaceMember.class)));
        when(githubPullRequestRepository.findByIdAndRepository_Workspace_Id(1L, 1L))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> aiSummaryService.generateSummary(1L, 1L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining(ErrorCode.GITHUB_PR_NOT_FOUND.getMessage());

        verify(aiSummaryRepository, never()).save(any());
    }

    @Test
    @DisplayName("Gemini 호출이 실패하면 AI_ANALYSIS_FAILED 예외가 발생한다")
    void generateSummary_Gemini_실패_AI_ANALYSIS_FAILED_예외() {
        GithubPullRequest pr = mockPr();

        when(workspaceMemberRepository.findByWorkspace_IdAndUser_IdAndIsActiveTrue(1L, 1L))
                .thenReturn(Optional.of(mock(WorkspaceMember.class)));
        when(githubPullRequestRepository.findByIdAndRepository_Workspace_Id(1L, 1L))
                .thenReturn(Optional.of(pr));
        when(aiSummaryRepository.findByGithubPullRequest_Id(1L)).thenReturn(Optional.empty());
        when(aiSummaryRepository.save(any(AiSummary.class))).thenAnswer(inv -> inv.getArgument(0));
        when(pullRequestFileRepository.findAllByGithubPullRequest_Id(1L)).thenReturn(List.of());
        when(geminiClient.generatePrAnalysis(any())).thenThrow(new RuntimeException("Gemini 오류"));

        assertThatThrownBy(() -> aiSummaryService.generateSummary(1L, 1L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining(ErrorCode.AI_ANALYSIS_FAILED.getMessage());
    }

    @Test
    @DisplayName("patch가 null인 파일은 combinedDiff에서 제외된다")
    void generateSummary_patch_null인_파일_diff에서_제외() {
        GithubPullRequest pr = mockPr();
        PullRequestFile fileWithPatch = mockFile("A.java", "@@ -1 +1 @@");
        PullRequestFile fileNoPatch = mockFile("B.java", null);
        GeminiClient.PrAnalysisResult result = sampleAnalysisResult();

        when(workspaceMemberRepository.findByWorkspace_IdAndUser_IdAndIsActiveTrue(1L, 1L))
                .thenReturn(Optional.of(mock(WorkspaceMember.class)));
        when(githubPullRequestRepository.findByIdAndRepository_Workspace_Id(1L, 1L))
                .thenReturn(Optional.of(pr));
        when(aiSummaryRepository.findByGithubPullRequest_Id(1L)).thenReturn(Optional.empty());
        when(aiSummaryRepository.save(any(AiSummary.class))).thenAnswer(inv -> inv.getArgument(0));
        when(pullRequestFileRepository.findAllByGithubPullRequest_Id(1L))
                .thenReturn(List.of(fileWithPatch, fileNoPatch));
        when(geminiClient.generatePrAnalysis(any())).thenReturn(result);
        when(geminiClient.getModel()).thenReturn("gemini-2.0-flash");

        aiSummaryService.generateSummary(1L, 1L);

        ArgumentCaptor<String> diffCaptor = ArgumentCaptor.forClass(String.class);
        verify(geminiClient).generatePrAnalysis(diffCaptor.capture());
        assertThat(diffCaptor.getValue()).contains("A.java");
        assertThat(diffCaptor.getValue()).doesNotContain("B.java");
    }

    @Test
    @DisplayName("Gemini가 대문자 riskLevel을 반환하면 정규화하여 저장한다")
    void generateSummary_Gemini_대문자_riskLevel_정규화() {
        GithubPullRequest pr = mockPr();
        GeminiClient.PrAnalysisResult result = new GeminiClient.PrAnalysisResult(
                "요약", List.of(), List.of(), "HIGH", List.of()
        );

        when(workspaceMemberRepository.findByWorkspace_IdAndUser_IdAndIsActiveTrue(1L, 1L))
                .thenReturn(Optional.of(mock(WorkspaceMember.class)));
        when(githubPullRequestRepository.findByIdAndRepository_Workspace_Id(1L, 1L))
                .thenReturn(Optional.of(pr));
        when(aiSummaryRepository.findByGithubPullRequest_Id(1L)).thenReturn(Optional.empty());
        when(aiSummaryRepository.save(any(AiSummary.class))).thenAnswer(inv -> inv.getArgument(0));
        when(pullRequestFileRepository.findAllByGithubPullRequest_Id(1L)).thenReturn(List.of());
        when(geminiClient.generatePrAnalysis(any())).thenReturn(result);
        when(geminiClient.getModel()).thenReturn("gemini-2.0-flash");

        AiSummaryResponse response = aiSummaryService.generateSummary(1L, 1L);

        assertThat(response.riskLevel()).isEqualTo("High");
    }

    @Test
    @DisplayName("fileFeedbacks가 null이어도 completed 상태로 정상 완료된다")
    void generateSummary_fileFeedbacks_null이어도_완료됨() {
        GithubPullRequest pr = mockPr();
        GeminiClient.PrAnalysisResult result = new GeminiClient.PrAnalysisResult(
                "요약", List.of(), List.of(), "High", null
        );

        when(workspaceMemberRepository.findByWorkspace_IdAndUser_IdAndIsActiveTrue(1L, 1L))
                .thenReturn(Optional.of(mock(WorkspaceMember.class)));
        when(githubPullRequestRepository.findByIdAndRepository_Workspace_Id(1L, 1L))
                .thenReturn(Optional.of(pr));
        when(aiSummaryRepository.findByGithubPullRequest_Id(1L)).thenReturn(Optional.empty());
        when(aiSummaryRepository.save(any(AiSummary.class))).thenAnswer(inv -> inv.getArgument(0));
        when(pullRequestFileRepository.findAllByGithubPullRequest_Id(1L)).thenReturn(List.of());
        when(geminiClient.generatePrAnalysis(any())).thenReturn(result);
        when(geminiClient.getModel()).thenReturn("gemini-2.0-flash");

        AiSummaryResponse response = aiSummaryService.generateSummary(1L, 1L);

        assertThat(response.status()).isEqualTo("completed");
        assertThat(response.fileFeedbacks()).isNull();
    }

    @Test
    @DisplayName("Gemini가 영문 fileRisk를 반환하면 한국어로 정규화하여 저장한다")
    void generateSummary_fileFeedbacks_risk_정규화() {
        GithubPullRequest pr = mockPr();
        GeminiClient.PrFileFeedback feedback = new GeminiClient.PrFileFeedback(
                "Test.java", "src/Test.java", "HIGH", "취약점", "수정", List.of(), List.of(), List.of()
        );
        GeminiClient.PrAnalysisResult result = new GeminiClient.PrAnalysisResult(
                "요약", List.of(), List.of(), "High", List.of(feedback)
        );

        when(workspaceMemberRepository.findByWorkspace_IdAndUser_IdAndIsActiveTrue(1L, 1L))
                .thenReturn(Optional.of(mock(WorkspaceMember.class)));
        when(githubPullRequestRepository.findByIdAndRepository_Workspace_Id(1L, 1L))
                .thenReturn(Optional.of(pr));
        when(aiSummaryRepository.findByGithubPullRequest_Id(1L)).thenReturn(Optional.empty());
        when(aiSummaryRepository.save(any(AiSummary.class))).thenAnswer(inv -> inv.getArgument(0));
        when(pullRequestFileRepository.findAllByGithubPullRequest_Id(1L)).thenReturn(List.of());
        when(geminiClient.generatePrAnalysis(any())).thenReturn(result);
        when(geminiClient.getModel()).thenReturn("gemini-2.0-flash");

        AiSummaryResponse response = aiSummaryService.generateSummary(1L, 1L);

        assertThat(response.fileFeedbacks()).hasSize(1);
        assertThat(response.fileFeedbacks().get(0).risk()).isEqualTo("높음");
    }

    // ── getSummary() ──────────────────────────────────────────

    @Test
    @DisplayName("completed 상태의 AI 요약을 정상 조회하면 전체 필드를 반환한다")
    void getSummary_성공_completed_상태() throws Exception {
        GithubPullRequest pr = mockPr();
        GeminiClient.PrAnalysisResult result = sampleAnalysisResult();
        String summaryJson = objectMapper.writeValueAsString(result);
        AiSummary completedSummary = AiSummary.create(pr);
        completedSummary.complete(summaryJson, "High", "gemini-2.0-flash");

        when(githubPullRequestRepository.findByIdAndRepository_Workspace_Id(1L, 1L))
                .thenReturn(Optional.of(pr));
        when(aiSummaryRepository.findByGithubPullRequest_Id(1L)).thenReturn(Optional.of(completedSummary));

        AiSummaryResponse response = aiSummaryService.getSummary(1L, 1L);

        assertThat(response.status()).isEqualTo("completed");
        assertThat(response.riskLevel()).isEqualTo("High");
        assertThat(response.summaryText()).isEqualTo("PR 요약");
        assertThat(response.cautionItems()).containsExactly("주의사항");
        assertThat(response.fileFeedbacks()).hasSize(1);
    }

    @Test
    @DisplayName("pending 상태의 AI 요약 조회 시 상세 필드는 null로 반환한다")
    void getSummary_성공_pending_상태() {
        GithubPullRequest pr = mockPr();
        AiSummary pendingSummary = AiSummary.create(pr);

        when(githubPullRequestRepository.findByIdAndRepository_Workspace_Id(1L, 1L))
                .thenReturn(Optional.of(pr));
        when(aiSummaryRepository.findByGithubPullRequest_Id(1L)).thenReturn(Optional.of(pendingSummary));

        AiSummaryResponse response = aiSummaryService.getSummary(1L, 1L);

        assertThat(response.status()).isEqualTo("pending");
        assertThat(response.summaryText()).isNull();
        assertThat(response.cautionItems()).isNull();
        assertThat(response.positiveItems()).isNull();
        assertThat(response.fileFeedbacks()).isNull();
    }

    @Test
    @DisplayName("PR을 찾을 수 없으면 예외가 발생한다")
    void getSummary_PR_없으면_예외() {
        when(githubPullRequestRepository.findByIdAndRepository_Workspace_Id(1L, 1L))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> aiSummaryService.getSummary(1L, 1L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining(ErrorCode.GITHUB_PR_NOT_FOUND.getMessage());
    }

    @Test
    @DisplayName("AI 요약을 찾을 수 없으면 예외가 발생한다")
    void getSummary_요약_없으면_예외() {
        GithubPullRequest pr = mockPr();
        when(githubPullRequestRepository.findByIdAndRepository_Workspace_Id(1L, 1L))
                .thenReturn(Optional.of(pr));
        when(aiSummaryRepository.findByGithubPullRequest_Id(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> aiSummaryService.getSummary(1L, 1L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining(ErrorCode.AI_SUMMARY_NOT_FOUND.getMessage());
    }
}
