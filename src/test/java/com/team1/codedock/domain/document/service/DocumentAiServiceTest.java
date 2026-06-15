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

    // ── generateDocument() ────────────────────────────────────

    @Test
    @DisplayName("AI 문서를 정상적으로 생성하고 저장한다")
    void generateDocument_성공() {
        User user = mockUser();
        WorkspaceMember member = mockMember();
        GithubRepository githubRepo = mockGithubRepo();
        GeminiClient.DocumentGenerationResult result =
                new GeminiClient.DocumentGenerationResult("AI 문서 제목", "AI 문서 내용", "manual");
        Document savedDoc = Document.createFromAi(member.getWorkspace(), member, result.title(), result.content(), result.category());

        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(workspaceMemberRepository.findByWorkspace_IdAndUser_IdAndIsActiveTrue(1L, 1L)).thenReturn(Optional.of(member));
        when(githubRepositoryRepository.findByWorkspaceId(1L)).thenReturn(List.of(githubRepo));
        when(githubApiClient.fetchEntitySources(any(), any(), any(), any())).thenReturn(List.of("@Entity public class User {}"));
        when(geminiClient.generateDocument(any())).thenReturn(result);
        when(documentRepository.save(any(Document.class))).thenReturn(savedDoc);

        DocumentResponse response = documentAiService.generateDocument(1L);

        assertThat(response.generatedBy()).isEqualTo("AI");
        assertThat(response.title()).isEqualTo("AI 문서 제목");
        verify(documentRepository).save(any(Document.class));
    }

    @Test
    @DisplayName("유저를 찾을 수 없으면 예외가 발생한다")
    void generateDocument_유저_없으면_예외() {
        when(userRepository.findById(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> documentAiService.generateDocument(1L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining(ErrorCode.USER_NOT_FOUND.getMessage());

        verify(documentRepository, never()).save(any());
    }

    @Test
    @DisplayName("워크스페이스 멤버를 찾을 수 없으면 예외가 발생한다")
    void generateDocument_멤버_없으면_예외() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(mock(User.class)));
        when(workspaceMemberRepository.findByWorkspace_IdAndUser_IdAndIsActiveTrue(1L, 1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> documentAiService.generateDocument(1L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining(ErrorCode.WORKSPACE_MEMBER_NOT_FOUND.getMessage());

        verify(documentRepository, never()).save(any());
    }

    @Test
    @DisplayName("GitHub 레포지토리를 찾을 수 없으면 예외가 발생한다")
    void generateDocument_GitHub_레포_없으면_예외() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(mock(User.class)));
        when(workspaceMemberRepository.findByWorkspace_IdAndUser_IdAndIsActiveTrue(1L, 1L)).thenReturn(Optional.of(mock(WorkspaceMember.class)));
        when(githubRepositoryRepository.findByWorkspaceId(1L)).thenReturn(List.of());

        assertThatThrownBy(() -> documentAiService.generateDocument(1L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining(ErrorCode.GITHUB_REPO_NOT_FOUND.getMessage());

        verify(documentRepository, never()).save(any());
    }
}
