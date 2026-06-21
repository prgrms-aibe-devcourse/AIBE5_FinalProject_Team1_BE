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
import com.team1.codedock.domain.workspace.entity.Workspace;
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
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DocumentAiServiceTest {

    @Mock private DocumentRepository documentRepository;
    @Mock private WorkspaceMemberRepository workspaceMemberRepository;
    @Mock private UserRepository userRepository;
    @Mock private GithubRepositoryRepository githubRepositoryRepository;
    @Mock private GithubApiClient githubApiClient;
    @Mock private GeminiClient geminiClient;

    @InjectMocks
    private DocumentAiService documentAiService;

    @BeforeEach
    void setUp() {
        CustomUserDetails userDetails = mock(CustomUserDetails.class);
        lenient().when(userDetails.getUserId()).thenReturn(1L);
        Authentication auth = mock(Authentication.class);
        lenient().when(auth.isAuthenticated()).thenReturn(true);
        lenient().when(auth.getPrincipal()).thenReturn(userDetails);
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private User mockUser() {
        User user = mock(User.class);
        lenient().when(user.getGithubAccessToken()).thenReturn("github-token");
        return user;
    }

    private WorkspaceMember mockMember() {
        Workspace workspace = mock(Workspace.class);
        WorkspaceMember member = mock(WorkspaceMember.class);
        lenient().when(member.getWorkspace()).thenReturn(workspace);
        return member;
    }

    private GithubRepository mockGithubRepo() {
        GithubRepository repo = mock(GithubRepository.class);
        lenient().when(repo.getOwner()).thenReturn("owner");
        lenient().when(repo.getName()).thenReturn("repo");
        lenient().when(repo.getDefaultBranch()).thenReturn("main");
        return repo;
    }

    private void setupCommonMocks(User user, WorkspaceMember member, GithubRepository repo) {
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(workspaceMemberRepository.findByWorkspace_IdAndUser_IdAndIsActiveTrue(1L, 1L))
                .thenReturn(Optional.of(member));
        when(githubRepositoryRepository.findByWorkspaceId(1L)).thenReturn(List.of(repo));
    }

    // ── 기존 4개 (시그니처·목 수정) ─────────────────────────────

    @Test
    @DisplayName("AI 문서를 정상적으로 생성하고 저장한다")
    void generateDocument_성공() {
        WorkspaceMember member = mockMember();
        GeminiClient.DocumentGenerationResult result =
                new GeminiClient.DocumentGenerationResult("AI 문서 제목", "AI 문서 내용", "manual");
        Document savedDoc = Document.createFromAi(
                member.getWorkspace(), member, result.title(), result.content(), result.category());

        setupCommonMocks(mockUser(), member, mockGithubRepo());
        when(githubApiClient.fetchControllerSources(any(), any(), any(), any()))
                .thenReturn(List.of("@RestController public class UserController {}"));
        when(geminiClient.generateDocument(any(), any(), any(), any())).thenReturn(result);
        when(documentRepository.save(any(Document.class))).thenReturn(savedDoc);

        DocumentResponse response = documentAiService.generateDocument(
                1L, new DocumentAiGenerateRequest("manual", "user", null, null));

        assertThat(response.title()).isEqualTo("AI 문서 제목");
        assertThat(response.generatedBy()).isEqualTo("AI");
        verify(documentRepository).save(any(Document.class));
    }

    @Test
    @DisplayName("유저를 찾을 수 없으면 예외가 발생한다")
    void generateDocument_유저_없으면_예외() {
        when(userRepository.findById(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> documentAiService.generateDocument(
                1L, new DocumentAiGenerateRequest("manual", "user", null, null)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining(ErrorCode.USER_NOT_FOUND.getMessage());

        verify(documentRepository, never()).save(any());
    }

    @Test
    @DisplayName("워크스페이스 멤버를 찾을 수 없으면 예외가 발생한다")
    void generateDocument_멤버_없으면_예외() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(mock(User.class)));
        when(workspaceMemberRepository.findByWorkspace_IdAndUser_IdAndIsActiveTrue(1L, 1L))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> documentAiService.generateDocument(
                1L, new DocumentAiGenerateRequest("manual", "user", null, null)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining(ErrorCode.WORKSPACE_MEMBER_NOT_FOUND.getMessage());

        verify(documentRepository, never()).save(any());
    }

    @Test
    @DisplayName("GitHub 레포지토리를 찾을 수 없으면 예외가 발생한다")
    void generateDocument_GitHub_레포_없으면_예외() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(mock(User.class)));
        when(workspaceMemberRepository.findByWorkspace_IdAndUser_IdAndIsActiveTrue(1L, 1L))
                .thenReturn(Optional.of(mock(WorkspaceMember.class)));
        when(githubRepositoryRepository.findByWorkspaceId(1L)).thenReturn(List.of());

        assertThatThrownBy(() -> documentAiService.generateDocument(
                1L, new DocumentAiGenerateRequest("manual", "user", null, null)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining(ErrorCode.GITHUB_REPO_NOT_FOUND.getMessage());

        verify(documentRepository, never()).save(any());
    }

    // ── 신규 10개 ─────────────────────────────────────────────

    @Test
    @DisplayName("manual 요청에 topic이 null이면 TOPIC_REQUIRED 예외가 발생한다")
    void generateDocument_manual_topic_null_예외() {
        setupCommonMocks(mockUser(), mockMember(), mockGithubRepo());

        assertThatThrownBy(() -> documentAiService.generateDocument(
                1L, new DocumentAiGenerateRequest("manual", null, null, null)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining(ErrorCode.TOPIC_REQUIRED.getMessage());
    }

    @Test
    @DisplayName("manual 요청에 topic이 공백이면 TOPIC_REQUIRED 예외가 발생한다")
    void generateDocument_manual_topic_blank_예외() {
        setupCommonMocks(mockUser(), mockMember(), mockGithubRepo());

        assertThatThrownBy(() -> documentAiService.generateDocument(
                1L, new DocumentAiGenerateRequest("manual", "   ", null, null)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining(ErrorCode.TOPIC_REQUIRED.getMessage());
    }

    @Test
    @DisplayName("release 요청에 날짜가 null이면 INVALID_INPUT 예외가 발생한다")
    void generateDocument_release_날짜_null_예외() {
        setupCommonMocks(mockUser(), mockMember(), mockGithubRepo());

        assertThatThrownBy(() -> documentAiService.generateDocument(
                1L, new DocumentAiGenerateRequest("release", null, null, null)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining(ErrorCode.INVALID_INPUT.getMessage());
    }

    @Test
    @DisplayName("release 요청에서 endDate가 startDate보다 이전이면 INVALID_DATE_RANGE 예외가 발생한다")
    void generateDocument_release_종료일_이전_예외() {
        setupCommonMocks(mockUser(), mockMember(), mockGithubRepo());

        assertThatThrownBy(() -> documentAiService.generateDocument(
                1L, new DocumentAiGenerateRequest("release", null,
                        LocalDate.of(2025, 6, 10), LocalDate.of(2025, 6, 5))))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining(ErrorCode.INVALID_DATE_RANGE.getMessage());
    }

    @Test
    @DisplayName("release 요청에서 기간이 7일을 초과하면 DATE_RANGE_TOO_LONG 예외가 발생한다")
    void generateDocument_release_7일_초과_예외() {
        setupCommonMocks(mockUser(), mockMember(), mockGithubRepo());

        assertThatThrownBy(() -> documentAiService.generateDocument(
                1L, new DocumentAiGenerateRequest("release", null,
                        LocalDate.of(2025, 6, 1), LocalDate.of(2025, 6, 9))))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining(ErrorCode.DATE_RANGE_TOO_LONG.getMessage());
    }

    @Test
    @DisplayName("release 기간에 커밋이 없으면 NO_COMMITS_IN_RANGE 예외가 발생한다")
    void generateDocument_release_커밋_없음_예외() {
        setupCommonMocks(mockUser(), mockMember(), mockGithubRepo());
        when(githubApiClient.fetchCommits(any(), any(), any(), any(), any(), any()))
                .thenReturn(List.of());

        assertThatThrownBy(() -> documentAiService.generateDocument(
                1L, new DocumentAiGenerateRequest("release", null,
                        LocalDate.of(2025, 6, 1), LocalDate.of(2025, 6, 7))))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining(ErrorCode.NO_COMMITS_IN_RANGE.getMessage());
    }

    @Test
    @DisplayName("release 정상 흐름: 커밋 기반으로 문서를 생성한다")
    void generateDocument_release_정상() {
        WorkspaceMember member = mockMember();
        GeminiClient.DocumentGenerationResult result =
                new GeminiClient.DocumentGenerationResult("v1.0 릴리즈 노트", "변경 내용", "release");
        Document savedDoc = Document.createFromAi(
                member.getWorkspace(), member, result.title(), result.content(), result.category());

        setupCommonMocks(mockUser(), member, mockGithubRepo());
        when(githubApiClient.fetchCommits(any(), any(), any(), any(), any(), any()))
                .thenReturn(List.of("feat: 로그인 기능 추가", "fix: 버그 수정"));
        when(geminiClient.generateDocument(any(), any(), any(), any())).thenReturn(result);
        when(documentRepository.save(any(Document.class))).thenReturn(savedDoc);

        DocumentResponse response = documentAiService.generateDocument(
                1L, new DocumentAiGenerateRequest("release", null,
                        LocalDate.of(2025, 6, 1), LocalDate.of(2025, 6, 7)));

        assertThat(response.title()).isEqualTo("v1.0 릴리즈 노트");
        assertThat(response.category()).isEqualTo("release");
    }

    @Test
    @DisplayName("manual 정상 흐름: fetchControllerSources를 호출한다")
    void generateDocument_manual_SpringBoot_정상() {
        WorkspaceMember member = mockMember();
        GeminiClient.DocumentGenerationResult result =
                new GeminiClient.DocumentGenerationResult("사용자 매뉴얼", "내용", "manual");
        Document savedDoc = Document.createFromAi(
                member.getWorkspace(), member, result.title(), result.content(), result.category());

        setupCommonMocks(mockUser(), member, mockGithubRepo());
        when(githubApiClient.fetchControllerSources(any(), any(), any(), any()))
                .thenReturn(List.of("@RestController public class UserController {}"));
        when(geminiClient.generateDocument(any(), any(), any(), any())).thenReturn(result);
        when(documentRepository.save(any(Document.class))).thenReturn(savedDoc);

        documentAiService.generateDocument(
                1L, new DocumentAiGenerateRequest("manual", "user", null, null));

        verify(githubApiClient).fetchControllerSources(any(), any(), any(), any());
    }

    @Test
    @DisplayName("manual에서 컨트롤러 파일이 없으면 fetchSourcesByKeyword로 fallback한다")
    void generateDocument_manual_fallback_keyword() {
        WorkspaceMember member = mockMember();
        GeminiClient.DocumentGenerationResult result =
                new GeminiClient.DocumentGenerationResult("결제 매뉴얼", "내용", "manual");
        Document savedDoc = Document.createFromAi(
                member.getWorkspace(), member, result.title(), result.content(), result.category());

        setupCommonMocks(mockUser(), member, mockGithubRepo());
        when(githubApiClient.fetchControllerSources(any(), any(), any(), any())).thenReturn(List.of());
        when(githubApiClient.fetchSourcesByKeyword(any(), any(), any(), any(), eq("payment")))
                .thenReturn(List.of("def process_payment(): pass"));
        when(geminiClient.generateDocument(any(), any(), any(), any())).thenReturn(result);
        when(documentRepository.save(any(Document.class))).thenReturn(savedDoc);

        documentAiService.generateDocument(
                1L, new DocumentAiGenerateRequest("manual", "payment", null, null));

        verify(githubApiClient).fetchSourcesByKeyword(any(), any(), any(), any(), eq("payment"));
    }

    @Test
    @DisplayName("faq 정상 흐름: category 'faq'로 문서를 생성한다")
    void generateDocument_faq_정상() {
        WorkspaceMember member = mockMember();
        GeminiClient.DocumentGenerationResult result =
                new GeminiClient.DocumentGenerationResult("로그인 FAQ", "Q&A 내용", "faq");
        Document savedDoc = Document.createFromAi(
                member.getWorkspace(), member, result.title(), result.content(), result.category());

        setupCommonMocks(mockUser(), member, mockGithubRepo());
        when(githubApiClient.fetchControllerSources(any(), any(), any(), any()))
                .thenReturn(List.of("@RestController public class AuthController {}"));
        when(geminiClient.generateDocument(any(), any(), any(), any())).thenReturn(result);
        when(documentRepository.save(any(Document.class))).thenReturn(savedDoc);

        DocumentResponse response = documentAiService.generateDocument(
                1L, new DocumentAiGenerateRequest("faq", "login", null, null));

        assertThat(response.category()).isEqualTo("faq");
    }
}
