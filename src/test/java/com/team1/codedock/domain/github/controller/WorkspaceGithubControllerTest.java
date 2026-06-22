package com.team1.codedock.domain.github.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.team1.codedock.domain.channel.dto.ChannelListResponse;
import com.team1.codedock.domain.channel.entity.Channel;
import com.team1.codedock.domain.chat.dto.ChatEventResponse;
import com.team1.codedock.domain.chat.dto.ChatEventType;
import com.team1.codedock.domain.github.dto.GithubConnectRequest;
import com.team1.codedock.domain.github.dto.GithubConnectResponse;
import com.team1.codedock.domain.github.dto.GithubRepositoryLinkRequest;
import com.team1.codedock.domain.github.service.GithubRepositoryService;
import com.team1.codedock.global.exception.BusinessException;
import com.team1.codedock.global.exception.ErrorCode;
import com.team1.codedock.global.exception.GlobalExceptionHandler;
import com.team1.codedock.global.security.CustomUserDetails;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.filter.CharacterEncodingFilter;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class WorkspaceGithubControllerTest {

    private static final Long USER_ID = 100L;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Mock
    private GithubRepositoryService githubRepositoryService;

    @Mock
    private SimpMessagingTemplate messagingTemplate;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        CustomUserDetails userDetails = mock(CustomUserDetails.class);
        lenient().when(userDetails.getUserId()).thenReturn(USER_ID);
        List<SimpleGrantedAuthority> authorities = List.of(new SimpleGrantedAuthority("ROLE_USER"));
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(userDetails, null, authorities));

        mockMvc = MockMvcBuilders.standaloneSetup(new WorkspaceGithubController(githubRepositoryService, messagingTemplate))
                .setControllerAdvice(new GlobalExceptionHandler())
                .addFilters(new CharacterEncodingFilter("UTF-8", true))
                .build();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("GitHub 레포지토리 연결 성공 시 연결 정보와 channelId를 반환한다")
    void connectRepository() throws Exception {
        GithubConnectRequest request = new GithubConnectRequest();
        request.setOwner("team1");
        request.setRepo("codedock");
        GithubConnectResponse response = GithubConnectResponse.builder()
                .id(30L)
                .channelId(40L)
                .owner("team1")
                .name("codedock")
                .fullName("team1/codedock")
                .url("https://github.com/team1/codedock")
                .defaultBranch("main")
                .isPrivate(true)
                .build();

        when(githubRepositoryService.connectRepository(eq(10L), eq(USER_ID), any(GithubConnectRequest.class)))
                .thenReturn(response);

        mockMvc.perform(post("/api/v1/workspaces/{workspaceId}/github", 10L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(30L))
                .andExpect(jsonPath("$.data.channelId").value(40L))
                .andExpect(jsonPath("$.data.fullName").value("team1/codedock"));

        verify(githubRepositoryService).connectRepository(eq(10L), eq(USER_ID), any(GithubConnectRequest.class));
    }

    @Test
    @DisplayName("GitHub 연결 요청 owner가 공백이면 서비스를 호출하지 않고 400을 반환한다")
    void connectRepositoryWithBlankOwner() throws Exception {
        GithubConnectRequest request = new GithubConnectRequest();
        request.setOwner(" ");
        request.setRepo("codedock");

        mockMvc.perform(post("/api/workspaces/{workspaceId}/github", 10L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("C001"));

        verifyNoInteractions(githubRepositoryService);
    }

    @Test
    @DisplayName("GitHub 연결 요청 repo가 공백이면 서비스를 호출하지 않고 400을 반환한다")
    void connectRepositoryWithBlankRepo() throws Exception {
        GithubConnectRequest request = new GithubConnectRequest();
        request.setOwner("team1");
        request.setRepo(" ");

        mockMvc.perform(post("/api/workspaces/{workspaceId}/github", 10L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("C001"));

        verifyNoInteractions(githubRepositoryService);
    }

    @Test
    @DisplayName("GitHub 연결 대상 유저가 없으면 404를 반환한다")
    void connectRepositoryWithoutUser() throws Exception {
        when(githubRepositoryService.connectRepository(any(), any(), any()))
                .thenThrow(new BusinessException(ErrorCode.USER_NOT_FOUND));

        mockMvc.perform(post("/api/v1/workspaces/1/github")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"owner\":\"octocat\",\"repo\":\"hello-world\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("U001"));
    }

    @Test
    @DisplayName("GitHub 연결 대상 워크스페이스 접근 권한이 없으면 403을 반환한다")
    void connectRepositoryForbidden() throws Exception {
        when(githubRepositoryService.connectRepository(any(), any(), any()))
                .thenThrow(new BusinessException(ErrorCode.FORBIDDEN));

        mockMvc.perform(post("/api/v1/workspaces/1/github")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"owner\":\"octocat\",\"repo\":\"hello-world\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("C003"));
    }

    @Test
    @DisplayName("GitHub 계정이 연결되지 않았으면 400을 반환한다")
    void connectRepositoryWithoutGithubToken() throws Exception {
        GithubConnectRequest request = new GithubConnectRequest();
        request.setOwner("team1");
        request.setRepo("codedock");

        when(githubRepositoryService.connectRepository(eq(10L), eq(USER_ID), any(GithubConnectRequest.class)))
                .thenThrow(new BusinessException(ErrorCode.GITHUB_NOT_CONNECTED));

        mockMvc.perform(post("/api/workspaces/{workspaceId}/github", 10L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("G005"));
    }

    @Test
    @DisplayName("GitHub API 오류가 발생하면 502를 반환한다")
    void connectRepositoryGithubApiError() throws Exception {
        when(githubRepositoryService.connectRepository(any(), any(), any()))
                .thenThrow(new BusinessException(ErrorCode.GITHUB_API_ERROR));

        mockMvc.perform(post("/api/v1/workspaces/1/github")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"owner\":\"octocat\",\"repo\":\"hello-world\"}"))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("G006"));
    }

    @Test
    @DisplayName("repository 채널 생성 API는 인증 사용자 기준으로 요청을 서비스에 전달한다")
    void createRepositoryChannel() throws Exception {
        GithubRepositoryLinkRequest request = request();
        ChannelListResponse response = new ChannelListResponse(
                40L,
                10L,
                30L,
                "codedock",
                Channel.TYPE_REPOSITORY,
                false,
                "CodeDock repository",
                null,
                null,
                0L,
                0L
        );

        when(githubRepositoryService.createRepositoryChannel(eq(10L), eq(USER_ID), eq(request))).thenReturn(response);

        mockMvc.perform(post("/api/workspaces/{workspaceId}/github/repositories", 10L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(40L))
                .andExpect(jsonPath("$.data.workspaceId").value(10L))
                .andExpect(jsonPath("$.data.githubRepositoryId").value(30L))
                .andExpect(jsonPath("$.data.name").value("codedock"))
                .andExpect(jsonPath("$.data.channelType").value(Channel.TYPE_REPOSITORY))
                .andExpect(jsonPath("$.data.isDeletable").value(false));

        verify(githubRepositoryService).createRepositoryChannel(10L, USER_ID, request);
        assertChannelCreatedBroadcast(10L, response);
    }

    @Test
    @DisplayName("repository 채널 생성 API는 v1 경로도 지원한다")
    void createRepositoryChannelWithV1Path() throws Exception {
        GithubRepositoryLinkRequest request = request();
        ChannelListResponse response = new ChannelListResponse(
                40L,
                10L,
                30L,
                "codedock",
                Channel.TYPE_REPOSITORY,
                false,
                "CodeDock repository",
                null,
                null,
                0L,
                0L
        );

        when(githubRepositoryService.createRepositoryChannel(eq(10L), eq(USER_ID), eq(request))).thenReturn(response);

        mockMvc.perform(post("/api/v1/workspaces/{workspaceId}/github/repositories", 10L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.channelType").value(Channel.TYPE_REPOSITORY));

        verify(githubRepositoryService).createRepositoryChannel(10L, USER_ID, request);
        assertChannelCreatedBroadcast(10L, response);
    }

    @Test
    @DisplayName("repository 채널 생성 권한이 없으면 403을 반환한다")
    void createRepositoryChannelForbidden() throws Exception {
        GithubRepositoryLinkRequest request = request();
        when(githubRepositoryService.createRepositoryChannel(eq(10L), eq(USER_ID), eq(request)))
                .thenThrow(new BusinessException(ErrorCode.FORBIDDEN));

        mockMvc.perform(post("/api/workspaces/{workspaceId}/github/repositories", 10L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("C003"));

        verifyNoInteractions(messagingTemplate);
    }

    @Test
    @DisplayName("repository 채널명 충돌을 해결할 수 없으면 409를 반환한다")
    void createRepositoryChannelConflict() throws Exception {
        GithubRepositoryLinkRequest request = request();
        when(githubRepositoryService.createRepositoryChannel(eq(10L), eq(USER_ID), eq(request)))
                .thenThrow(new BusinessException(ErrorCode.CONFLICT));

        mockMvc.perform(post("/api/workspaces/{workspaceId}/github/repositories", 10L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("C006"));

        verifyNoInteractions(messagingTemplate);
    }

    @Test
    @DisplayName("repository 채널 생성 중 DB unique 충돌이 발생하면 409를 반환한다")
    void createRepositoryChannelDataIntegrityConflict() throws Exception {
        GithubRepositoryLinkRequest request = request();
        when(githubRepositoryService.createRepositoryChannel(eq(10L), eq(USER_ID), eq(request)))
                .thenThrow(new DataIntegrityViolationException("unique constraint violation"));

        mockMvc.perform(post("/api/workspaces/{workspaceId}/github/repositories", 10L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("C006"));

        verifyNoInteractions(messagingTemplate);
    }

    @Test
    @DisplayName("repository 채널 생성 요청의 GitHub repository id가 비어 있으면 400을 반환한다")
    void createRepositoryChannelWithBlankGithubRepoId() throws Exception {
        GithubRepositoryLinkRequest request = new GithubRepositoryLinkRequest(
                " ",
                "team1",
                "codedock",
                "team1/codedock",
                "https://github.com/team1/codedock",
                "CodeDock repository",
                true,
                "main"
        );

        mockMvc.perform(post("/api/workspaces/{workspaceId}/github/repositories", 10L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("C001"));

        verifyNoInteractions(githubRepositoryService);
    }

    @Test
    @DisplayName("repository 채널 생성 요청 필수 메타데이터가 비어 있으면 400을 반환한다")
    void createRepositoryChannelWithBlankRequiredMetadata() throws Exception {
        GithubRepositoryLinkRequest request = new GithubRepositoryLinkRequest(
                "123456",
                " ",
                "codedock",
                "team1/codedock",
                "https://github.com/team1/codedock",
                "CodeDock repository",
                true,
                "main"
        );

        mockMvc.perform(post("/api/workspaces/{workspaceId}/github/repositories", 10L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("C001"));

        verifyNoInteractions(githubRepositoryService);
    }

    @Test
    @DisplayName("repository 채널 생성 요청 defaultBranch가 너무 길면 400을 반환한다")
    void createRepositoryChannelWithOversizedDefaultBranch() throws Exception {
        GithubRepositoryLinkRequest request = new GithubRepositoryLinkRequest(
                "123456",
                "team1",
                "codedock",
                "team1/codedock",
                "https://github.com/team1/codedock",
                "CodeDock repository",
                true,
                "a".repeat(256)
        );

        mockMvc.perform(post("/api/workspaces/{workspaceId}/github/repositories", 10L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("C001"));

        verifyNoInteractions(githubRepositoryService);
    }

    @Test
    @DisplayName("repository 이름이 채널명 제한보다 길면 400을 반환한다")
    void createRepositoryChannelWithOversizedName() throws Exception {
        GithubRepositoryLinkRequest request = new GithubRepositoryLinkRequest(
                "123456",
                "team1",
                "a".repeat(Channel.MAX_NAME_LENGTH + 1),
                "team1/codedock",
                "https://github.com/team1/codedock",
                "CodeDock repository",
                true,
                "main"
        );

        mockMvc.perform(post("/api/workspaces/{workspaceId}/github/repositories", 10L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("C001"));

        verifyNoInteractions(githubRepositoryService);
    }

    private GithubRepositoryLinkRequest request() {
        return new GithubRepositoryLinkRequest(
                "123456",
                "team1",
                "codedock",
                "team1/codedock",
                "https://github.com/team1/codedock",
                "CodeDock repository",
                true,
                "main"
        );
    }

    private void assertChannelCreatedBroadcast(Long workspaceId, ChannelListResponse expectedPayload) {
        ArgumentCaptor<Object> payloadCaptor = ArgumentCaptor.forClass(Object.class);
        verify(messagingTemplate).convertAndSend(
                eq("/topic/workspaces/" + workspaceId + "/channels"),
                payloadCaptor.capture()
        );

        assertThat(payloadCaptor.getValue()).isInstanceOf(ChatEventResponse.class);
        ChatEventResponse<?> event = (ChatEventResponse<?>) payloadCaptor.getValue();
        assertThat(event.type()).isEqualTo(ChatEventType.CHANNEL_CREATED);
        assertThat(event.payload()).isEqualTo(expectedPayload);
    }
}
