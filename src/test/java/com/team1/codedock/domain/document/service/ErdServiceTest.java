package com.team1.codedock.domain.document.service;

import com.team1.codedock.domain.ai.service.GeminiClient;
import com.team1.codedock.domain.document.dto.ErdDocumentResponse;
import com.team1.codedock.domain.document.dto.ErdTableResponse;
import com.team1.codedock.domain.document.entity.ErdDocument;
import com.team1.codedock.domain.document.entity.ErdTable;
import com.team1.codedock.domain.document.repository.ErdDocumentRepository;
import com.team1.codedock.domain.document.repository.ErdTableRepository;
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

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ErdServiceTest {

    @Mock private ErdDocumentRepository erdDocumentRepository;
    @Mock private ErdTableRepository erdTableRepository;
    @Mock private WorkspaceMemberRepository workspaceMemberRepository;
    @Mock private UserRepository userRepository;
    @Mock private GithubRepositoryRepository githubRepositoryRepository;
    @Mock private GithubApiClient githubApiClient;
    @Mock private GeminiClient geminiClient;

    @InjectMocks
    private ErdService erdService;

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
        when(user.getGithubAccessToken()).thenReturn("github-token");
        return user;
    }

    private WorkspaceMember mockMember() {
        Workspace workspace = mock(Workspace.class);
        WorkspaceMember member = mock(WorkspaceMember.class);
        when(member.getWorkspace()).thenReturn(workspace);
        return member;
    }

    private GithubRepository mockGithubRepo() {
        GithubRepository repo = mock(GithubRepository.class);
        when(repo.getOwner()).thenReturn("owner");
        when(repo.getName()).thenReturn("repo");
        when(repo.getDefaultBranch()).thenReturn("main");
        return repo;
    }

    private GeminiClient.ErdGenerationResult mockErdResult() {
        GeminiClient.ErdTableInfo table = new GeminiClient.ErdTableInfo("users", "{}", "유저 테이블");
        return new GeminiClient.ErdGenerationResult("erDiagram\n...", List.of(table));
    }

    // ── generateErd() ─────────────────────────────────────────

    @Test
    @DisplayName("처음 생성 시 ErdDocument를 새로 저장하고 ErdTable을 생성한다")
    void generateErd_성공_최초_생성() {
        User user = mockUser();
        WorkspaceMember member = mockMember();
        GithubRepository githubRepo = mockGithubRepo();
        GeminiClient.ErdGenerationResult result = mockErdResult();
        ErdDocument savedDoc = ErdDocument.create(member.getWorkspace(), member, "ERD", null, "erDiagram\n...");

        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(workspaceMemberRepository.findByWorkspace_IdAndUser_IdAndIsActiveTrue(1L, 1L)).thenReturn(Optional.of(member));
        when(githubRepositoryRepository.findByWorkspaceId(1L)).thenReturn(List.of(githubRepo));
        when(githubApiClient.fetchRepoSources(any(), any(), any(), any())).thenReturn(List.of("@Entity public class User {}"));
        when(geminiClient.generateErd(any())).thenReturn(result);
        when(erdDocumentRepository.findByWorkspace_IdAndDeletedAtIsNull(1L)).thenReturn(Optional.empty());
        when(erdDocumentRepository.save(any(ErdDocument.class))).thenReturn(savedDoc);

        ErdDocumentResponse response = erdService.generateErd(1L);

        assertThat(response.mermaidCode()).isEqualTo("erDiagram\n...");
        verify(erdDocumentRepository).save(any(ErdDocument.class));
        verify(erdTableRepository).deleteAllByWorkspace_Id(1L);
        verify(erdTableRepository).save(any(ErdTable.class));
    }

    @Test
    @DisplayName("재분석 시 기존 ErdDocument를 update하고 ErdTable을 재생성한다")
    void generateErd_성공_재분석_덮어쓰기() {
        User user = mockUser();
        WorkspaceMember member = mockMember();
        GithubRepository githubRepo = mockGithubRepo();
        GeminiClient.ErdGenerationResult result = mockErdResult();
        ErdDocument existingDoc = ErdDocument.create(member.getWorkspace(), member, "ERD", null, "old_code");

        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(workspaceMemberRepository.findByWorkspace_IdAndUser_IdAndIsActiveTrue(1L, 1L)).thenReturn(Optional.of(member));
        when(githubRepositoryRepository.findByWorkspaceId(1L)).thenReturn(List.of(githubRepo));
        when(githubApiClient.fetchRepoSources(any(), any(), any(), any())).thenReturn(List.of("@Entity public class User {}"));
        when(geminiClient.generateErd(any())).thenReturn(result);
        when(erdDocumentRepository.findByWorkspace_IdAndDeletedAtIsNull(1L)).thenReturn(Optional.of(existingDoc));

        ErdDocumentResponse response = erdService.generateErd(1L);

        assertThat(response.mermaidCode()).isEqualTo("erDiagram\n...");
        verify(erdDocumentRepository, never()).save(any(ErdDocument.class));
        verify(erdTableRepository).deleteAllByWorkspace_Id(1L);
        verify(erdTableRepository).save(any(ErdTable.class));
    }

    @Test
    @DisplayName("AI 응답에 tables가 null이면 ErdTable을 저장하지 않는다")
    void generateErd_테이블_null이면_저장_안함() {
        User user = mockUser();
        WorkspaceMember member = mockMember();
        GithubRepository githubRepo = mockGithubRepo();
        GeminiClient.ErdGenerationResult result = new GeminiClient.ErdGenerationResult("erDiagram\n...", null);
        ErdDocument savedDoc = ErdDocument.create(member.getWorkspace(), member, "ERD", null, "erDiagram\n...");

        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(workspaceMemberRepository.findByWorkspace_IdAndUser_IdAndIsActiveTrue(1L, 1L)).thenReturn(Optional.of(member));
        when(githubRepositoryRepository.findByWorkspaceId(1L)).thenReturn(List.of(githubRepo));
        when(githubApiClient.fetchRepoSources(any(), any(), any(), any())).thenReturn(List.of("@Entity public class User {}"));
        when(geminiClient.generateErd(any())).thenReturn(result);
        when(erdDocumentRepository.findByWorkspace_IdAndDeletedAtIsNull(1L)).thenReturn(Optional.empty());
        when(erdDocumentRepository.save(any(ErdDocument.class))).thenReturn(savedDoc);

        erdService.generateErd(1L);

        verify(erdTableRepository).deleteAllByWorkspace_Id(1L);
        verify(erdTableRepository, never()).save(any(ErdTable.class));
    }

    @Test
    @DisplayName("레포에서 소스를 찾을 수 없으면 ERD_SOURCE_NOT_FOUND 예외가 발생한다")
    void generateErd_소스_없으면_예외() {
        User user = mockUser();
        GithubRepository githubRepo = mockGithubRepo();

        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(workspaceMemberRepository.findByWorkspace_IdAndUser_IdAndIsActiveTrue(1L, 1L)).thenReturn(Optional.of(mock(WorkspaceMember.class)));
        when(githubRepositoryRepository.findByWorkspaceId(1L)).thenReturn(List.of(githubRepo));
        when(githubApiClient.fetchRepoSources(any(), any(), any(), any())).thenReturn(List.of());

        assertThatThrownBy(() -> erdService.generateErd(1L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining(ErrorCode.ERD_SOURCE_NOT_FOUND.getMessage());

        verify(geminiClient, never()).generateErd(any());
    }

    @Test
    @DisplayName("유저를 찾을 수 없으면 예외가 발생한다")
    void generateErd_유저_없으면_예외() {
        when(userRepository.findById(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> erdService.generateErd(1L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining(ErrorCode.USER_NOT_FOUND.getMessage());

        verify(erdDocumentRepository, never()).save(any());
    }

    @Test
    @DisplayName("워크스페이스 멤버를 찾을 수 없으면 예외가 발생한다")
    void generateErd_멤버_없으면_예외() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(mock(User.class)));
        when(workspaceMemberRepository.findByWorkspace_IdAndUser_IdAndIsActiveTrue(1L, 1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> erdService.generateErd(1L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining(ErrorCode.WORKSPACE_MEMBER_NOT_FOUND.getMessage());

        verify(erdDocumentRepository, never()).save(any());
    }

    @Test
    @DisplayName("GitHub 레포지토리를 찾을 수 없으면 예외가 발생한다")
    void generateErd_GitHub_레포_없으면_예외() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(mock(User.class)));
        when(workspaceMemberRepository.findByWorkspace_IdAndUser_IdAndIsActiveTrue(1L, 1L)).thenReturn(Optional.of(mock(WorkspaceMember.class)));
        when(githubRepositoryRepository.findByWorkspaceId(1L)).thenReturn(List.of());

        assertThatThrownBy(() -> erdService.generateErd(1L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining(ErrorCode.GITHUB_REPO_NOT_FOUND.getMessage());

        verify(erdDocumentRepository, never()).save(any());
    }

    // ── getErd() ──────────────────────────────────────────────

    @Test
    @DisplayName("ErdDocument를 정상적으로 조회한다")
    void getErd_성공() {
        WorkspaceMember member = mockMember();
        ErdDocument doc = ErdDocument.create(member.getWorkspace(), member, "ERD", null, "erDiagram\n...");
        when(erdDocumentRepository.findByWorkspace_IdAndDeletedAtIsNull(1L)).thenReturn(Optional.of(doc));

        ErdDocumentResponse response = erdService.getErd(1L);

        assertThat(response.mermaidCode()).isEqualTo("erDiagram\n...");
        assertThat(response.title()).isEqualTo("ERD");
    }

    @Test
    @DisplayName("ErdDocument가 없으면 예외가 발생한다")
    void getErd_없으면_예외() {
        when(erdDocumentRepository.findByWorkspace_IdAndDeletedAtIsNull(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> erdService.getErd(1L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining(ErrorCode.ERD_NOT_FOUND.getMessage());
    }

    // ── getErdTables() ────────────────────────────────────────

    @Test
    @DisplayName("ErdTable 목록을 정상적으로 조회한다")
    void getErdTables_성공() {
        WorkspaceMember member = mockMember();
        ErdTable table = ErdTable.create(member.getWorkspace(), member, "users", "{}", "유저 테이블");
        when(erdTableRepository.findAllByWorkspace_Id(1L)).thenReturn(List.of(table));

        List<ErdTableResponse> result = erdService.getErdTables(1L);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).tableName()).isEqualTo("users");
    }

    @Test
    @DisplayName("ErdTable이 없으면 빈 리스트를 반환한다")
    void getErdTables_빈_리스트() {
        when(erdTableRepository.findAllByWorkspace_Id(1L)).thenReturn(List.of());

        List<ErdTableResponse> result = erdService.getErdTables(1L);

        assertThat(result).isEmpty();
    }
}
