package com.team1.codedock.domain.ai.entity;

import com.team1.codedock.domain.pr.entity.GithubPullRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class AiSummaryTest {

    private GithubPullRequest pr;

    @BeforeEach
    void setUp() {
        pr = mock(GithubPullRequest.class);
    }

    // ── create() ──────────────────────────────────────────────

    @Test
    @DisplayName("create()로 AiSummary를 생성하면 githubPullRequest가 설정되고 status가 'pending'이다")
    void create_githubPullRequest_설정_및_status_pending() {
        AiSummary aiSummary = AiSummary.create(pr);

        assertThat(aiSummary.getGithubPullRequest()).isEqualTo(pr);
        assertThat(aiSummary.getStatus()).isEqualTo("pending");
    }

    @Test
    @DisplayName("create() 시 summary, riskLevel, modelVersion은 null로 초기화된다")
    void create_summary_riskLevel_modelVersion_초기값_null() {
        AiSummary aiSummary = AiSummary.create(pr);

        assertThat(aiSummary.getSummary()).isNull();
        assertThat(aiSummary.getRiskLevel()).isNull();
        assertThat(aiSummary.getModelVersion()).isNull();
    }

    // ── startProcessing() ─────────────────────────────────────

    @Test
    @DisplayName("startProcessing() 호출 시 status가 'processing'으로 변경된다")
    void startProcessing_status_processing으로_변경() {
        AiSummary aiSummary = AiSummary.create(pr);

        aiSummary.startProcessing();

        assertThat(aiSummary.getStatus()).isEqualTo("processing");
    }

    // ── complete() ────────────────────────────────────────────

    @Test
    @DisplayName("complete() 호출 시 모든 필드가 정상적으로 설정되고 status가 'completed'가 된다")
    void complete_모든_필드_정상_설정() {
        AiSummary aiSummary = AiSummary.create(pr);

        aiSummary.complete("{\"summaryText\":\"요약\"}", "High", "gemini-2.0-flash");

        assertThat(aiSummary.getSummary()).isEqualTo("{\"summaryText\":\"요약\"}");
        assertThat(aiSummary.getRiskLevel()).isEqualTo("High");
        assertThat(aiSummary.getModelVersion()).isEqualTo("gemini-2.0-flash");
        assertThat(aiSummary.getStatus()).isEqualTo("completed");
    }

    @Test
    @DisplayName("complete() 시 riskLevel이 null이어도 status는 'completed'가 된다")
    void complete_riskLevel_null이어도_status_completed() {
        AiSummary aiSummary = AiSummary.create(pr);

        aiSummary.complete("{}", null, "gemini-2.0-flash");

        assertThat(aiSummary.getRiskLevel()).isNull();
        assertThat(aiSummary.getStatus()).isEqualTo("completed");
    }
}
