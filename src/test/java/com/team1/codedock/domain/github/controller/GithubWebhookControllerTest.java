package com.team1.codedock.domain.github.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.team1.codedock.domain.github.dto.GithubIssueWebhookPayload;
import com.team1.codedock.domain.github.service.GithubWebhookRegistrationService;
import com.team1.codedock.domain.github.service.GithubWebhookService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.filter.CharacterEncodingFilter;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class GithubWebhookControllerTest {

    private static final Long REPOSITORY_ID = 20L;
    private static final String SIGNATURE = "sha256=test-signature";

    @Mock
    private GithubWebhookService githubWebhookService;
    @Mock
    private GithubWebhookRegistrationService githubWebhookRegistrationService;

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .standaloneSetup(new GithubWebhookController(
                        githubWebhookService,
                        githubWebhookRegistrationService,
                        objectMapper
                ))
                .addFilters(new CharacterEncodingFilter("UTF-8", true))
                .build();
    }

    @Test
    @DisplayName("issues 웹훅은 서명 검증 후 payload를 파싱해 이슈 이벤트 처리로 전달한다")
    void receiveIssueWebhook() throws Exception {
        byte[] rawBody = issueWebhookJson().getBytes(StandardCharsets.UTF_8);

        mockMvc.perform(post("/api/v1/github/webhooks/{repositoryId}", REPOSITORY_ID)
                        .header("X-GitHub-Event", "issues")
                        .header("X-Hub-Signature-256", SIGNATURE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(rawBody))
                .andExpect(status().isOk());

        ArgumentCaptor<byte[]> rawBodyCaptor = ArgumentCaptor.forClass(byte[].class);
        ArgumentCaptor<GithubIssueWebhookPayload> payloadCaptor =
                ArgumentCaptor.forClass(GithubIssueWebhookPayload.class);
        verify(githubWebhookService).verifySignature(eq(REPOSITORY_ID), eq(SIGNATURE), rawBodyCaptor.capture());
        verify(githubWebhookService).processIssueEvent(eq(REPOSITORY_ID), payloadCaptor.capture());

        assertThat(rawBodyCaptor.getValue()).isEqualTo(rawBody);
        assertThat(payloadCaptor.getValue().action()).isEqualTo("opened");
        assertThat(payloadCaptor.getValue().issue().id()).isEqualTo(9001L);
        assertThat(payloadCaptor.getValue().issue().number()).isEqualTo(7);
        assertThat(payloadCaptor.getValue().repository().fullName()).isEqualTo("team/repo");
    }

    @Test
    @DisplayName("issues가 아닌 웹훅은 서명만 검증하고 이슈 이벤트 처리를 호출하지 않는다")
    void receiveNonIssueWebhook() throws Exception {
        byte[] rawBody = "{\"ref\":\"refs/heads/main\"}".getBytes(StandardCharsets.UTF_8);

        mockMvc.perform(post("/api/v1/github/webhooks/{repositoryId}", REPOSITORY_ID)
                        .header("X-GitHub-Event", "push")
                        .header("X-Hub-Signature-256", SIGNATURE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(rawBody))
                .andExpect(status().isOk());

        verify(githubWebhookService).verifySignature(eq(REPOSITORY_ID), eq(SIGNATURE), any(byte[].class));
        verify(githubWebhookService, never()).processIssueEvent(any(), any());
    }

    @Test
    @DisplayName("issues 웹훅 payload 파싱이 실패하면 이슈 이벤트 처리를 호출하지 않는다")
    void receiveIssueWebhookWithInvalidPayload() throws Exception {
        byte[] rawBody = "{invalid-json".getBytes(StandardCharsets.UTF_8);

        mockMvc.perform(post("/api/v1/github/webhooks/{repositoryId}", REPOSITORY_ID)
                        .header("X-GitHub-Event", "issues")
                        .header("X-Hub-Signature-256", SIGNATURE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(rawBody))
                .andExpect(status().isOk());

        verify(githubWebhookService).verifySignature(eq(REPOSITORY_ID), eq(SIGNATURE), any(byte[].class));
        verify(githubWebhookService, never()).processIssueEvent(any(), any());
    }

    @Test
    @DisplayName("GitHub event 헤더가 없으면 서명만 검증하고 이슈 이벤트 처리를 호출하지 않는다")
    void receiveWebhookWithoutEventHeader() throws Exception {
        byte[] rawBody = issueWebhookJson().getBytes(StandardCharsets.UTF_8);

        mockMvc.perform(post("/api/v1/github/webhooks/{repositoryId}", REPOSITORY_ID)
                        .header("X-Hub-Signature-256", SIGNATURE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(rawBody))
                .andExpect(status().isOk());

        verify(githubWebhookService).verifySignature(eq(REPOSITORY_ID), eq(SIGNATURE), any(byte[].class));
        verify(githubWebhookService, never()).processIssueEvent(any(), any());
    }

    private static String issueWebhookJson() {
        return """
                {
                  "action": "opened",
                  "issue": {
                    "id": 9001,
                    "number": 7,
                    "title": "로그인 버그",
                    "body": "본문",
                    "state": "open",
                    "html_url": "https://github.com/team/repo/issues/7",
                    "user": {"login": "octocat"},
                    "labels": [{"name": "bug", "color": "ff0000"}],
                    "assignees": [{"login": "assignee"}],
                    "created_at": "2026-06-22T00:00:00Z",
                    "updated_at": "2026-06-22T01:00:00Z",
                    "closed_at": null
                  },
                  "repository": {
                    "id": 100,
                    "name": "repo",
                    "full_name": "team/repo"
                  }
                }
                """;
    }
}
