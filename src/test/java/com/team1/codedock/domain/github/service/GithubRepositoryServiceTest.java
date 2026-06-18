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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
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
import static org.mockito.Mockito.verifyNoInteractions;
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
        WorkspaceMember member = mockWorkspaceMember(workspace, "admin");

        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(workspaceMemberRepository.findByWorkspace_IdAndUser_IdAndIsActiveTrue(1L, 1L))
                .thenReturn(Optional.of(member));
        when(githubApiService.getRepo("octocat", "hello-world", "github-token")).thenReturn(githubRepoResponse());
        when(githubRepositoryRepository.findByWorkspaceIdAndGithubRepoId(1L, "12345"))
                .thenReturn(Optional.empty());
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
    @DisplayName("기존 GitHub repository와 channel이 있으면 재사용하고 메타데이터만 갱신한다")
    void connectRepositoryReusesExistingRepositoryAndChannel() {
        Workspace workspace = workspace(10L);
        WorkspaceMember member = workspaceMember("admin");
        User user = mock(User.class);
        GithubRepository existing = repository(
                workspace,
                "123456",
                "old-team",
                "old-codedock",
                "old-team/old-codedock",
                "https://github.com/old-team/old-codedock",
                "Old description",
                false,
                "master"
        );
        Channel existingChannel = Channel.createRepository(workspace, existing);
        ReflectionTestUtils.setField(existingChannel, "id", 40L);
        GithubConnectRequest request = new GithubConnectRequest();
        request.setOwner("team1");
        request.setRepo("codedock");
        GithubRepoResponse repoInfo = GithubRepoResponse.builder()
                .id(123456L)
                .owner("team1")
                .name("codedock")
                .fullName("team1/codedock")
                .htmlUrl("https://github.com/team1/codedock")
                .isPrivate(true)
                .defaultBranch("main")
                .build();

        when(userRepository.findById(100L)).thenReturn(Optional.of(user));
        when(user.getGithubAccessToken()).thenReturn("github-token");
        when(workspaceMemberRepository.findByWorkspace_IdAndUser_IdAndIsActiveTrue(10L, 100L))
                .thenReturn(Optional.of(member));
        when(githubApiService.getRepo("team1", "codedock", "github-token")).thenReturn(repoInfo);
        when(githubRepositoryRepository.findByWorkspaceIdAndGithubRepoId(10L, "123456"))
                .thenReturn(Optional.of(existing));
        when(channelRepository.findRepositoryChannel(10L, 30L)).thenReturn(Optional.of(existingChannel));

        GithubConnectResponse response = githubRepositoryService.connectRepository(10L, 100L, request);

        assertThat(response.getId()).isEqualTo(30L);
        assertThat(response.getChannelId()).isEqualTo(40L);
        assertThat(response.getOwner()).isEqualTo("team1");
        assertThat(response.getName()).isEqualTo("codedock");
        assertThat(response.getFullName()).isEqualTo("team1/codedock");
        assertThat(response.getUrl()).isEqualTo("https://github.com/team1/codedock");
        assertThat(response.getDefaultBranch()).isEqualTo("main");
        assertThat(response.isPrivate()).isTrue();
        assertThat(existing.getOwner()).isEqualTo("team1");
        assertThat(existing.getName()).isEqualTo("codedock");
        assertThat(existing.getFullName()).isEqualTo("team1/codedock");
        assertThat(existing.getUrl()).isEqualTo("https://github.com/team1/codedock");
        assertThat(existing.getDefaultBranch()).isEqualTo("main");
        assertThat(existing.isPrivate()).isTrue();
        verify(githubRepositoryRepository, never()).save(any(GithubRepository.class));
        verify(channelRepository, never()).save(any(Channel.class));
    }

    @Test
    @DisplayName("GitHub 연결 대상 유저가 없으면 workspace와 GitHub API를 건드리지 않는다")
    void connectRepositoryWithoutUser() {
        when(userRepository.findById(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> githubRepositoryService.connectRepository(1L, 1L, connectRequest()))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.USER_NOT_FOUND);

        verifyNoInteractions(githubApiService);
        verify(githubRepositoryRepository, never()).save(any(GithubRepository.class));
        verify(channelRepository, never()).save(any(Channel.class));
    }

    @Test
    @DisplayName("워크스페이스 멤버가 아니면 GitHub API 호출과 채널 생성을 하지 않는다")
    void connectRepositoryWithoutWorkspaceMember() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(mock(User.class)));
        when(workspaceMemberRepository.findByWorkspace_IdAndUser_IdAndIsActiveTrue(1L, 1L))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> githubRepositoryService.connectRepository(1L, 1L, connectRequest()))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.FORBIDDEN);

        verifyNoInteractions(githubApiService);
        verify(githubRepositoryRepository, never()).save(any(GithubRepository.class));
        verify(channelRepository, never()).save(any(Channel.class));
    }

    @Test
    @DisplayName("viewer 권한은 GitHub 연결 시 GitHub API 호출과 채널 생성을 하지 않는다")
    void connectRepositoryByViewerDoesNotCallGithubApiOrCreateChannel() {
        WorkspaceMember viewer = workspaceMember("viewer");
        User user = mock(User.class);
        GithubConnectRequest request = new GithubConnectRequest();
        request.setOwner("team1");
        request.setRepo("codedock");

        when(userRepository.findById(100L)).thenReturn(Optional.of(user));
        when(workspaceMemberRepository.findByWorkspace_IdAndUser_IdAndIsActiveTrue(10L, 100L))
                .thenReturn(Optional.of(viewer));

        assertThatThrownBy(() -> githubRepositoryService.connectRepository(10L, 100L, request))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.FORBIDDEN);

        verifyNoInteractions(githubApiService);
        verify(githubRepositoryRepository, never()).save(any(GithubRepository.class));
        verify(channelRepository, never()).save(any(Channel.class));
    }

    @Test
    @DisplayName("GitHub 토큰이 없으면 GitHub API 호출과 채널 생성을 하지 않는다")
    void connectRepositoryWithoutGithubToken() {
        User user = mock(User.class);
        WorkspaceMember member = workspaceMember("admin");
        when(user.getGithubAccessToken()).thenReturn(null);
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(workspaceMemberRepository.findByWorkspace_IdAndUser_IdAndIsActiveTrue(1L, 1L))
                .thenReturn(Optional.of(member));

        assertThatThrownBy(() -> githubRepositoryService.connectRepository(1L, 1L, connectRequest()))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.GITHUB_NOT_CONNECTED);

        verify(githubApiService, never()).getRepo(any(), any(), any());
        verify(githubRepositoryRepository, never()).save(any(GithubRepository.class));
        verify(channelRepository, never()).save(any(Channel.class));
    }

    @Test
    @DisplayName("GitHub API에서 레포지토리를 찾지 못하면 저장과 채널 생성을 하지 않는다")
    void connectRepositoryWithMissingGithubRepository() {
        User user = mockGithubUser();
        WorkspaceMember member = workspaceMember("admin");
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(workspaceMemberRepository.findByWorkspace_IdAndUser_IdAndIsActiveTrue(1L, 1L))
                .thenReturn(Optional.of(member));
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
        GithubRepository existing = repository(
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
        GithubRepository repository = repository(workspace);

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
    @DisplayName("기존 GitHub repository에 채널만 없으면 repository row 중복 없이 채널만 생성한다")
    void createRepositoryChannelForExistingRepositoryWithoutChannel() {
        Workspace workspace = workspace(10L);
        GithubRepository repository = repository(workspace);

        when(workspaceRepository.findById(10L)).thenReturn(Optional.of(workspace));
        when(githubRepositoryRepository.findByWorkspaceIdAndGithubRepoId(10L, "123456"))
                .thenReturn(Optional.of(repository));
        when(channelRepository.findRepositoryChannel(10L, 30L)).thenReturn(Optional.empty());
        when(channelRepository.save(any(Channel.class))).thenAnswer(invocation -> {
            Channel channel = invocation.getArgument(0);
            ReflectionTestUtils.setField(channel, "id", 40L);
            return channel;
        });

        ChannelListResponse response =
                githubRepositoryService.createRepositoryChannel(10L, 100L, linkRequest("123456", "team1", "codedock"));

        assertThat(response.id()).isEqualTo(40L);
        assertThat(response.githubRepositoryId()).isEqualTo(30L);
        assertThat(response.isDeletable()).isFalse();
        verify(githubRepositoryRepository, never()).save(any(GithubRepository.class));
        verify(channelRepository).save(any(Channel.class));
    }

    @Test
    @DisplayName("이미 repository 채널이 있으면 새로 만들지 않고 기존 채널을 재사용한다")
    void createRepositoryChannelReusesExistingChannel() {
        Workspace workspace = workspace(10L);
        GithubRepository repository = repository(workspace);
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
    @DisplayName("repository 채널 이름이 이미 있으면 owner suffix를 붙인다")
    void createRepositoryChannelUsesOwnerSuffixWhenNameConflicts() {
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
        when(channelRepository.countByWorkspaceIdAndNameIgnoreCase(10L, "codedock")).thenReturn(1L);
        when(channelRepository.countByWorkspaceIdAndNameIgnoreCase(10L, "codedock-team1")).thenReturn(0L);
        when(channelRepository.save(any(Channel.class))).thenAnswer(invocation -> {
            Channel channel = invocation.getArgument(0);
            ReflectionTestUtils.setField(channel, "id", 40L);
            return channel;
        });

        ChannelListResponse response = githubRepositoryService.createRepositoryChannel(10L, 100L, request);

        assertThat(response.name()).isEqualTo("codedock-team1");
        ArgumentCaptor<Channel> channelCaptor = ArgumentCaptor.forClass(Channel.class);
        verify(channelRepository).save(channelCaptor.capture());
        assertThat(channelCaptor.getValue().getName()).isEqualTo("codedock-team1");
    }

    @Test
    @DisplayName("base와 owner suffix가 모두 있으면 repository id suffix를 붙인다")
    void createRepositoryChannelUsesRepositoryIdSuffixWhenOwnerSuffixConflicts() {
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
        when(channelRepository.countByWorkspaceIdAndNameIgnoreCase(10L, "codedock")).thenReturn(1L);
        when(channelRepository.countByWorkspaceIdAndNameIgnoreCase(10L, "codedock-team1")).thenReturn(1L);
        when(channelRepository.countByWorkspaceIdAndNameIgnoreCase(10L, "codedock-repo-123456")).thenReturn(0L);
        when(channelRepository.save(any(Channel.class))).thenAnswer(invocation -> {
            Channel channel = invocation.getArgument(0);
            ReflectionTestUtils.setField(channel, "id", 40L);
            return channel;
        });

        ChannelListResponse response = githubRepositoryService.createRepositoryChannel(10L, 100L, request);

        assertThat(response.name()).isEqualTo("codedock-repo-123456");
        ArgumentCaptor<Channel> channelCaptor = ArgumentCaptor.forClass(Channel.class);
        verify(channelRepository).save(channelCaptor.capture());
        assertThat(channelCaptor.getValue().getName()).isEqualTo("codedock-repo-123456");
    }

    @Test
    @DisplayName("repository 채널 이름 후보가 모두 있으면 충돌 예외를 던진다")
    void createRepositoryChannelThrowsConflictWhenNameCandidatesExist() {
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
        when(channelRepository.countByWorkspaceIdAndNameIgnoreCase(10L, "codedock")).thenReturn(1L);
        when(channelRepository.countByWorkspaceIdAndNameIgnoreCase(10L, "codedock-team1")).thenReturn(1L);
        when(channelRepository.countByWorkspaceIdAndNameIgnoreCase(10L, "codedock-repo-123456")).thenReturn(1L);

        assertThatThrownBy(() -> githubRepositoryService.createRepositoryChannel(10L, 100L, request))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.CONFLICT);

        verify(channelRepository, never()).save(any(Channel.class));
    }

    @Test
    @DisplayName("viewer 권한은 repository 채널 생성 시 repository와 채널을 만들지 않는다")
    void createRepositoryChannelByViewerDoesNotCreateAnything() {
        WorkspaceMember viewer = workspaceMember("viewer");
        when(workspaceMemberRepository.findByWorkspace_IdAndUser_IdAndIsActiveTrue(10L, 100L))
                .thenReturn(Optional.of(viewer));

        assertThatThrownBy(() ->
                githubRepositoryService.createRepositoryChannel(10L, 100L, linkRequest("123456", "team1", "codedock")))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.FORBIDDEN);

        verify(githubRepositoryRepository, never()).save(any(GithubRepository.class));
        verify(channelRepository, never()).save(any(Channel.class));
    }

    @Test
    @DisplayName("선택 메타데이터가 공백이면 null로 정규화한다")
    void linkRepositoryNormalizesBlankOptionalMetadata() {
        Workspace workspace = workspace(10L);
        GithubRepositoryLinkRequest request = new GithubRepositoryLinkRequest(
                "123456",
                "team1",
                "codedock",
                "team1/codedock",
                "https://github.com/team1/codedock",
                " ",
                true,
                " "
        );

        when(workspaceRepository.findById(10L)).thenReturn(Optional.of(workspace));
        when(githubRepositoryRepository.findByWorkspaceIdAndGithubRepoId(10L, "123456"))
                .thenReturn(Optional.empty());
        when(githubRepositoryRepository.save(any(GithubRepository.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        GithubRepository repository = githubRepositoryService.linkRepository(10L, 100L, request);

        assertThat(repository.getDescription()).isNull();
        assertThat(repository.getDefaultBranch()).isNull();
    }

    @ParameterizedTest
    @ValueSource(strings = {"owner", "OWNER", "admin", " ADMIN "})
    @DisplayName("owner/admin 권한은 trim/lowercase 후 허용한다")
    void linkRepositoryAllowsNormalizedManagerAuthority(String authority) {
        Workspace workspace = workspace(10L);
        WorkspaceMember manager = workspaceMember(authority);
        when(workspaceMemberRepository.findByWorkspace_IdAndUser_IdAndIsActiveTrue(10L, 100L))
                .thenReturn(Optional.of(manager));
        when(workspaceRepository.findById(10L)).thenReturn(Optional.of(workspace));
        when(githubRepositoryRepository.findByWorkspaceIdAndGithubRepoId(10L, "123456"))
                .thenReturn(Optional.empty());
        when(githubRepositoryRepository.save(any(GithubRepository.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        GithubRepository repository =
                githubRepositoryService.linkRepository(10L, 100L, linkRequest("123456", "team1", "codedock"));

        assertThat(repository.getName()).isEqualTo("codedock");
        verify(githubRepositoryRepository).save(any(GithubRepository.class));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "editor", "viewer", "member"})
    @DisplayName("owner/admin이 아닌 권한은 정규화 후 거부한다")
    void linkRepositoryRejectsNonManagerAuthority(String authority) {
        WorkspaceMember member = workspaceMember(authority);
        when(workspaceMemberRepository.findByWorkspace_IdAndUser_IdAndIsActiveTrue(10L, 100L))
                .thenReturn(Optional.of(member));

        assertThatThrownBy(() -> githubRepositoryService.linkRepository(10L, 100L, linkRequest("123456", "team1", "codedock")))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.FORBIDDEN);

        verify(githubRepositoryRepository, never()).save(any(GithubRepository.class));
        verify(channelRepository, never()).save(any(Channel.class));
    }

    @Test
    @DisplayName("필수 repository 메타데이터가 공백이면 저장하지 않는다")
    void linkRepositoryRejectsBlankRequiredMetadata() {
        Workspace workspace = workspace(10L);
        when(workspaceRepository.findById(10L)).thenReturn(Optional.of(workspace));

        assertThatThrownBy(() -> githubRepositoryService.linkRepository(10L, 100L,
                new GithubRepositoryLinkRequest("123456", " ", "codedock", "team1/codedock",
                        "https://github.com/team1/codedock", null, true, "main")))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INVALID_INPUT);

        assertThatThrownBy(() -> githubRepositoryService.linkRepository(10L, 100L,
                new GithubRepositoryLinkRequest("123456", "team1", " ", "team1/codedock",
                        "https://github.com/team1/codedock", null, true, "main")))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INVALID_INPUT);

        assertThatThrownBy(() -> githubRepositoryService.linkRepository(10L, 100L,
                new GithubRepositoryLinkRequest("123456", "team1", "codedock", " ",
                        "https://github.com/team1/codedock", null, true, "main")))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INVALID_INPUT);

        assertThatThrownBy(() -> githubRepositoryService.linkRepository(10L, 100L,
                new GithubRepositoryLinkRequest("123456", "team1", "codedock", "team1/codedock",
                        " ", null, true, "main")))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INVALID_INPUT);

        verify(githubRepositoryRepository, never()).save(any(GithubRepository.class));
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

    @Test
    @DisplayName("repository 이름이 채널명 제한보다 길면 링크를 거부한다")
    void linkRepositoryRejectsRepositoryNameLongerThanChannelNameLimit() {
        Workspace workspace = workspace(10L);
        when(workspaceRepository.findById(10L)).thenReturn(Optional.of(workspace));

        assertThatThrownBy(() -> githubRepositoryService.linkRepository(10L, 100L,
                linkRequest("123456", "team1", "a".repeat(Channel.MAX_NAME_LENGTH + 1))))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INVALID_INPUT);

        verify(githubRepositoryRepository, never()).save(any(GithubRepository.class));
        verify(channelRepository, never()).save(any(Channel.class));
    }

    @Test
    @DisplayName("repository 이름이 채널명 제한과 같으면 허용한다")
    void linkRepositoryAllowsRepositoryNameAtChannelNameLimit() {
        Workspace workspace = workspace(10L);
        String name = "a".repeat(Channel.MAX_NAME_LENGTH);
        when(workspaceRepository.findById(10L)).thenReturn(Optional.of(workspace));
        when(githubRepositoryRepository.findByWorkspaceIdAndGithubRepoId(10L, "123456"))
                .thenReturn(Optional.empty());
        when(githubRepositoryRepository.save(any(GithubRepository.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        GithubRepository repository = githubRepositoryService.linkRepository(10L, 100L,
                linkRequest("123456", "team1", name));

        assertThat(repository.getName()).isEqualTo(name);
        verify(githubRepositoryRepository).save(any(GithubRepository.class));
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

    private WorkspaceMember mockWorkspaceMember(Workspace workspace, String authority) {
        WorkspaceMember member = mock(WorkspaceMember.class);
        when(member.getWorkspace()).thenReturn(workspace);
        lenient().when(member.getAuthority()).thenReturn(authority);
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

    private GithubRepository repository(Workspace workspace) {
        return repository(
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
    }

    private GithubRepository repository(
            Workspace workspace,
            String githubRepoId,
            String owner,
            String name,
            String fullName,
            String url,
            String description,
            boolean isPrivate,
            String defaultBranch
    ) {
        GithubRepository repository = GithubRepository.create(
                workspace,
                githubRepoId,
                owner,
                name,
                fullName,
                url,
                description,
                isPrivate,
                defaultBranch
        );
        ReflectionTestUtils.setField(repository, "id", 30L);
        return repository;
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
