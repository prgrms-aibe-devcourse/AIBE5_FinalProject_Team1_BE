package com.team1.codedock.domain.github.service;

import com.team1.codedock.domain.channel.dto.ChannelListResponse;
import com.team1.codedock.domain.channel.entity.Channel;
import com.team1.codedock.domain.channel.repository.ChannelRepository;
import com.team1.codedock.domain.github.dto.GithubConnectRequest;
import com.team1.codedock.domain.github.dto.GithubConnectResponse;
import com.team1.codedock.domain.github.dto.GithubRepoResponse;
import com.team1.codedock.domain.github.dto.GithubRepositoryLinkRequest;
import com.team1.codedock.domain.github.dto.GithubRepositoryResponse;
import com.team1.codedock.domain.github.entity.GithubRepository;
import com.team1.codedock.domain.github.repository.GithubRepositoryRepository;
import com.team1.codedock.domain.user.entity.User;
import com.team1.codedock.domain.user.repository.UserRepository;
import com.team1.codedock.domain.workspace.entity.Workspace;
import com.team1.codedock.domain.workspace.entity.WorkspaceMember;
import com.team1.codedock.domain.workspace.repository.WorkspaceMemberRepository;
import com.team1.codedock.domain.workspace.repository.WorkspaceRepository;
import com.team1.codedock.global.exception.BusinessException;
import com.team1.codedock.global.exception.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

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
class GithubRepositoryServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private WorkspaceRepository workspaceRepository;

    @Mock
    private WorkspaceMemberRepository workspaceMemberRepository;

    @Mock
    private GithubRepositoryRepository githubRepositoryRepository;

    @Mock
    private ChannelRepository channelRepository;

    @Mock
    private GithubApiService githubApiService;

    @InjectMocks
    private GithubRepositoryService githubRepositoryService;

    @BeforeEach
    void setUp() {
        WorkspaceMember manager = workspaceMember("admin");
        lenient().when(workspaceMemberRepository.findByWorkspace_IdAndUser_IdAndIsActiveTrue(10L, 100L))
                .thenReturn(Optional.of(manager));
    }

    @Test
    @DisplayName("GitHub OAuth 토큰으로 레포지토리를 연결하고 repository 채널을 생성한다")
    void connectRepository() {
        Workspace workspace = workspace(1L);
        User user = mockGithubUser();
        WorkspaceMember member = mockWorkspaceMember(workspace);

        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(workspaceMemberRepository.findByWorkspace_IdAndUser_IdAndIsActiveTrue(1L, 1L))
                .thenReturn(Optional.of(member));
        when(githubRepositoryRepository.findByWorkspaceId(1L)).thenReturn(List.of());
        when(githubApiService.getRepo("octocat", "hello-world", "github-token")).thenReturn(githubRepoResponse());
        when(githubRepositoryRepository.save(any(GithubRepository.class))).thenAnswer(invocation -> {
            GithubRepository repository = invocation.getArgument(0);
            ReflectionTestUtils.setField(repository, "id", 30L);
            return repository;
        });
        when(channelRepository.findRepositoryChannel(1L, 30L)).thenReturn(Optional.empty());
        when(channelRepository.save(any(Channel.class))).thenAnswer(invocation -> {
            Channel channel = invocation.getArgument(0);
            ReflectionTestUtils.setField(channel, "id", 40L);
            return channel;
        });

        GithubConnectResponse response = githubRepositoryService.connectRepository(1L, 1L, connectRequest());

        assertThat(response.getId()).isEqualTo(30L);
        assertThat(response.getChannelId()).isEqualTo(40L);
        assertThat(response.getOwner()).isEqualTo("octocat");
        assertThat(response.getName()).isEqualTo("hello-world");
        assertThat(response.getFullName()).isEqualTo("octocat/hello-world");
        assertThat(response.getDefaultBranch()).isEqualTo("main");
        verify(githubRepositoryRepository).save(any(GithubRepository.class));
        verify(channelRepository).save(any(Channel.class));
    }

    @Test
    @DisplayName("GitHub 연결 대상 유저가 없으면 예외가 발생한다")
    void connectRepositoryWithoutUser() {
        when(userRepository.findById(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> githubRepositoryService.connectRepository(1L, 1L, connectRequest()))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.USER_NOT_FOUND);

        verify(githubRepositoryRepository, never()).save(any(GithubRepository.class));
        verify(channelRepository, never()).save(any(Channel.class));
    }

    @Test
    @DisplayName("GitHub 연결 대상 워크스페이스 멤버가 없으면 예외가 발생한다")
    void connectRepositoryWithoutWorkspaceMember() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(mock(User.class)));
        when(workspaceMemberRepository.findByWorkspace_IdAndUser_IdAndIsActiveTrue(1L, 1L))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> githubRepositoryService.connectRepository(1L, 1L, connectRequest()))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.WORKSPACE_MEMBER_NOT_FOUND);

        verify(githubRepositoryRepository, never()).save(any(GithubRepository.class));
        verify(channelRepository, never()).save(any(Channel.class));
    }

    @Test
    @DisplayName("이미 연결된 GitHub 레포지토리가 있으면 중복 연결을 거부한다")
    void connectRepositoryAlreadyConnected() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(mock(User.class)));
        when(workspaceMemberRepository.findByWorkspace_IdAndUser_IdAndIsActiveTrue(1L, 1L))
                .thenReturn(Optional.of(mock(WorkspaceMember.class)));
        when(githubRepositoryRepository.findByWorkspaceId(1L)).thenReturn(List.of(mock(GithubRepository.class)));

        assertThatThrownBy(() -> githubRepositoryService.connectRepository(1L, 1L, connectRequest()))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.GITHUB_REPO_ALREADY_CONNECTED);

        verify(githubRepositoryRepository, never()).save(any(GithubRepository.class));
        verify(channelRepository, never()).save(any(Channel.class));
    }

    @Test
    @DisplayName("GitHub 토큰이 없으면 레포지토리 연결을 거부한다")
    void connectRepositoryWithoutGithubToken() {
        User user = mock(User.class);
        when(user.getGithubAccessToken()).thenReturn(null);
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(workspaceMemberRepository.findByWorkspace_IdAndUser_IdAndIsActiveTrue(1L, 1L))
                .thenReturn(Optional.of(mock(WorkspaceMember.class)));
        when(githubRepositoryRepository.findByWorkspaceId(1L)).thenReturn(List.of());

        assertThatThrownBy(() -> githubRepositoryService.connectRepository(1L, 1L, connectRequest()))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.GITHUB_NOT_CONNECTED);

        verify(githubRepositoryRepository, never()).save(any(GithubRepository.class));
        verify(channelRepository, never()).save(any(Channel.class));
    }

    @Test
    @DisplayName("GitHub API에서 레포지토리를 찾지 못하면 예외가 발생한다")
    void connectRepositoryWithMissingGithubRepository() {
        User user = mockGithubUser();
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(workspaceMemberRepository.findByWorkspace_IdAndUser_IdAndIsActiveTrue(1L, 1L))
                .thenReturn(Optional.of(mock(WorkspaceMember.class)));
        when(githubRepositoryRepository.findByWorkspaceId(1L)).thenReturn(List.of());
        when(githubApiService.getRepo(any(), any(), any()))
                .thenThrow(new BusinessException(ErrorCode.GITHUB_REPO_NOT_FOUND));

        assertThatThrownBy(() -> githubRepositoryService.connectRepository(1L, 1L, connectRequest()))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.GITHUB_REPO_NOT_FOUND);

        verify(githubRepositoryRepository, never()).save(any(GithubRepository.class));
        verify(channelRepository, never()).save(any(Channel.class));
    }

    @Test
    @DisplayName("관리자가 GitHub repository 링크를 새로 생성한다")
    void linkRepositoryCreatesNewRepository() {
        Workspace workspace = workspace(10L);
        GithubRepositoryLinkRequest request = linkRequest(" 123456 ", " team1 ", " codedock ");

        when(workspaceRepository.findById(10L)).thenReturn(Optional.of(workspace));
        when(githubRepositoryRepository.findByWorkspaceIdAndGithubRepoId(10L, "123456"))
                .thenReturn(Optional.empty());
        when(githubRepositoryRepository.save(any(GithubRepository.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        GithubRepository repository = githubRepositoryService.linkRepository(10L, 100L, request);

        assertThat(repository.getWorkspace()).isEqualTo(workspace);
        assertThat(repository.getGithubRepoId()).isEqualTo("123456");
        assertThat(repository.getOwner()).isEqualTo("team1");
        assertThat(repository.getName()).isEqualTo("codedock");
        assertThat(repository.getFullName()).isEqualTo("team1/codedock");
        assertThat(repository.getUrl()).isEqualTo("https://github.com/team1/codedock");
        assertThat(repository.isPrivate()).isTrue();
        assertThat(repository.getDefaultBranch()).isEqualTo("main");
        verify(githubRepositoryRepository).save(any(GithubRepository.class));
        verify(channelRepository, never()).save(any(Channel.class));
    }

    @Test
    @DisplayName("이미 연결된 GitHub repository는 중복 row를 만들지 않고 메타데이터만 갱신한다")
    void linkRepositoryUpdatesExistingRepository() {
        Workspace workspace = workspace(10L);
        GithubRepository existing = GithubRepository.create(
                workspace,
                "123456",
                "old-owner",
                "old-name",
                "old-owner/old-name",
                "https://github.com/old-owner/old-name",
                "Old description",
                false,
                "master"
        );

        when(workspaceRepository.findById(10L)).thenReturn(Optional.of(workspace));
        when(githubRepositoryRepository.findByWorkspaceIdAndGithubRepoId(10L, "123456"))
                .thenReturn(Optional.of(existing));

        GithubRepository repository = githubRepositoryService.linkRepository(10L, 100L, linkRequest("123456", "team1", "codedock"));

        assertThat(repository).isEqualTo(existing);
        assertThat(repository.getOwner()).isEqualTo("team1");
        assertThat(repository.getName()).isEqualTo("codedock");
        assertThat(repository.getFullName()).isEqualTo("team1/codedock");
        assertThat(repository.getUrl()).isEqualTo("https://github.com/team1/codedock");
        assertThat(repository.getDescription()).isEqualTo("CodeDock repository");
        assertThat(repository.isPrivate()).isTrue();
        assertThat(repository.getDefaultBranch()).isEqualTo("main");
        verify(githubRepositoryRepository, never()).save(any(GithubRepository.class));
        verify(channelRepository, never()).save(any(Channel.class));
    }

    @Test
    @DisplayName("GitHub repository 링크 응답 DTO를 반환한다")
    void linkRepositoryResponse() {
        Workspace workspace = workspace(10L);
        GithubRepository repository = GithubRepository.create(
                workspace,
                "123456",
                "team1",
                "codedock",
                "team1/codedock",
                "https://github.com/team1/codedock",
                "CodeDock repository",
                true,
                "main"
        );

        when(workspaceRepository.findById(10L)).thenReturn(Optional.of(workspace));
        when(githubRepositoryRepository.findByWorkspaceIdAndGithubRepoId(10L, "123456"))
                .thenReturn(Optional.of(repository));

        GithubRepositoryResponse response =
                githubRepositoryService.linkRepositoryResponse(10L, 100L, linkRequest("123456", "team1", "codedock"));

        assertThat(response.workspaceId()).isEqualTo(10L);
        assertThat(response.githubRepoId()).isEqualTo("123456");
        assertThat(response.owner()).isEqualTo("team1");
        assertThat(response.name()).isEqualTo("codedock");
    }

    @Test
    @DisplayName("연결된 GitHub repository 기준으로 repository 채널을 생성한다")
    void createRepositoryChannel() {
        Workspace workspace = workspace(10L);
        GithubRepositoryLinkRequest request = linkRequest("123456", "team1", "codedock");

        when(workspaceRepository.findById(10L)).thenReturn(Optional.of(workspace));
        when(githubRepositoryRepository.findByWorkspaceIdAndGithubRepoId(10L, "123456"))
                .thenReturn(Optional.empty());
        when(githubRepositoryRepository.save(any(GithubRepository.class))).thenAnswer(invocation -> {
            GithubRepository repository = invocation.getArgument(0);
            ReflectionTestUtils.setField(repository, "id", 30L);
            return repository;
        });
        when(channelRepository.findRepositoryChannel(10L, 30L)).thenReturn(Optional.empty());
        when(channelRepository.save(any(Channel.class))).thenAnswer(invocation -> {
            Channel channel = invocation.getArgument(0);
            ReflectionTestUtils.setField(channel, "id", 40L);
            return channel;
        });

        ChannelListResponse response = githubRepositoryService.createRepositoryChannel(10L, 100L, request);

        assertThat(response.id()).isEqualTo(40L);
        assertThat(response.workspaceId()).isEqualTo(10L);
        assertThat(response.githubRepositoryId()).isEqualTo(30L);
        assertThat(response.name()).isEqualTo("codedock");
        assertThat(response.channelType()).isEqualTo(Channel.TYPE_REPOSITORY);
        assertThat(response.isDeletable()).isFalse();
        assertThat(response.description()).isEqualTo("CodeDock repository");
        verify(channelRepository).save(any(Channel.class));
    }

    @Test
    @DisplayName("이미 repository 채널이 있으면 새로 만들지 않고 기존 채널을 재사용한다")
    void createRepositoryChannelReusesExistingChannel() {
        Workspace workspace = workspace(10L);
        GithubRepository repository = GithubRepository.create(
                workspace,
                "123456",
                "team1",
                "codedock",
                "team1/codedock",
                "https://github.com/team1/codedock",
                "CodeDock repository",
                true,
                "main"
        );
        ReflectionTestUtils.setField(repository, "id", 30L);
        Channel existingChannel = Channel.createRepository(workspace, repository);
        ReflectionTestUtils.setField(existingChannel, "id", 40L);

        when(workspaceRepository.findById(10L)).thenReturn(Optional.of(workspace));
        when(githubRepositoryRepository.findByWorkspaceIdAndGithubRepoId(10L, "123456"))
                .thenReturn(Optional.of(repository));
        when(channelRepository.findRepositoryChannel(10L, 30L)).thenReturn(Optional.of(existingChannel));

        ChannelListResponse response =
                githubRepositoryService.createRepositoryChannel(10L, 100L, linkRequest("123456", "team1", "codedock"));

        assertThat(response.id()).isEqualTo(40L);
        assertThat(response.githubRepositoryId()).isEqualTo(30L);
        assertThat(response.channelType()).isEqualTo(Channel.TYPE_REPOSITORY);
        verify(channelRepository, never()).save(any(Channel.class));
    }

    @Test
    @DisplayName("인증 사용자가 없으면 GitHub repository 링크를 거부한다")
    void linkRepositoryWithoutUser() {
        assertThatThrownBy(() -> githubRepositoryService.linkRepository(10L, null, linkRequest("123456", "team1", "codedock")))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.UNAUTHORIZED);

        verify(githubRepositoryRepository, never()).save(any(GithubRepository.class));
        verify(channelRepository, never()).save(any(Channel.class));
    }

    @Test
    @DisplayName("워크스페이스 멤버가 아니면 GitHub repository 링크를 거부한다")
    void linkRepositoryByNonWorkspaceMember() {
        when(workspaceMemberRepository.findByWorkspace_IdAndUser_IdAndIsActiveTrue(10L, 100L))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> githubRepositoryService.linkRepository(10L, 100L, linkRequest("123456", "team1", "codedock")))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.FORBIDDEN);

        verify(githubRepositoryRepository, never()).save(any(GithubRepository.class));
        verify(channelRepository, never()).save(any(Channel.class));
    }

    @Test
    @DisplayName("viewer 권한은 GitHub repository 링크를 거부한다")
    void linkRepositoryByViewer() {
        WorkspaceMember viewer = workspaceMember("viewer");
        when(workspaceMemberRepository.findByWorkspace_IdAndUser_IdAndIsActiveTrue(10L, 100L))
                .thenReturn(Optional.of(viewer));

        assertThatThrownBy(() -> githubRepositoryService.linkRepository(10L, 100L, linkRequest("123456", "team1", "codedock")))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.FORBIDDEN);

        verify(githubRepositoryRepository, never()).save(any(GithubRepository.class));
        verify(channelRepository, never()).save(any(Channel.class));
    }

    @Test
    @DisplayName("워크스페이스가 없으면 GitHub repository 링크를 거부한다")
    void linkRepositoryWithMissingWorkspace() {
        when(workspaceRepository.findById(10L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> githubRepositoryService.linkRepository(10L, 100L, linkRequest("123456", "team1", "codedock")))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.WORKSPACE_NOT_FOUND);

        verify(githubRepositoryRepository, never()).save(any(GithubRepository.class));
        verify(channelRepository, never()).save(any(Channel.class));
    }

    @Test
    @DisplayName("GitHub repository id가 비어 있으면 링크를 거부한다")
    void linkRepositoryWithBlankGithubRepoId() {
        Workspace workspace = workspace(10L);
        when(workspaceRepository.findById(10L)).thenReturn(Optional.of(workspace));

        assertThatThrownBy(() -> githubRepositoryService.linkRepository(10L, 100L, linkRequest(" ", "team1", "codedock")))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INVALID_INPUT);

        verify(githubRepositoryRepository, never()).save(any(GithubRepository.class));
        verify(channelRepository, never()).save(any(Channel.class));
    }

    private GithubConnectRequest connectRequest() {
        GithubConnectRequest request = new GithubConnectRequest();
        request.setOwner("octocat");
        request.setRepo("hello-world");
        return request;
    }

    private User mockGithubUser() {
        User user = mock(User.class);
        when(user.getGithubAccessToken()).thenReturn("github-token");
        return user;
    }

    private WorkspaceMember mockWorkspaceMember(Workspace workspace) {
        WorkspaceMember member = mock(WorkspaceMember.class);
        when(member.getWorkspace()).thenReturn(workspace);
        return member;
    }

    private GithubRepoResponse githubRepoResponse() {
        return GithubRepoResponse.builder()
                .id(12345L)
                .owner("octocat")
                .name("hello-world")
                .fullName("octocat/hello-world")
                .htmlUrl("https://github.com/octocat/hello-world")
                .isPrivate(false)
                .defaultBranch("main")
                .build();
    }

    private GithubRepositoryLinkRequest linkRequest(String githubRepoId, String owner, String name) {
        return new GithubRepositoryLinkRequest(
                githubRepoId,
                owner,
                name,
                owner.trim() + "/" + name.trim(),
                "https://github.com/" + owner.trim() + "/" + name.trim(),
                "CodeDock repository",
                true,
                "main"
        );
    }

    private Workspace workspace(Long id) {
        Workspace workspace = mock(Workspace.class);
        lenient().when(workspace.getId()).thenReturn(id);
        return workspace;
    }

    private WorkspaceMember workspaceMember(String authority) {
        WorkspaceMember member = mock(WorkspaceMember.class);
        lenient().when(member.getAuthority()).thenReturn(authority);
        return member;
    }
}
