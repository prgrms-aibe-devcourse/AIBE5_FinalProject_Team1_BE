package com.team1.codedock.domain.github.repository;

import com.team1.codedock.domain.channel.entity.Channel;
import com.team1.codedock.domain.channel.repository.ChannelRepository;
import com.team1.codedock.domain.github.entity.GithubRepository;
import com.team1.codedock.domain.issue.entity.GithubIssue;
import com.team1.codedock.domain.issue.repository.GithubIssueRepository;
import com.team1.codedock.domain.pr.entity.GithubPullRequest;
import com.team1.codedock.domain.pr.repository.GithubPullRequestRepository;
import com.team1.codedock.domain.user.entity.User;
import com.team1.codedock.domain.user.repository.UserRepository;
import com.team1.codedock.domain.workspace.entity.Workspace;
import com.team1.codedock.domain.workspace.repository.WorkspaceRepository;
import com.team1.codedock.global.config.JpaConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@Import(JpaConfig.class)
class GithubRepositoryPersistenceTest {

    @Autowired
    private TestEntityManager em;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private WorkspaceRepository workspaceRepository;

    @Autowired
    private GithubRepositoryRepository githubRepositoryRepository;

    @Autowired
    private ChannelRepository channelRepository;

    @Autowired
    private GithubPullRequestRepository githubPullRequestRepository;

    @Autowired
    private GithubIssueRepository githubIssueRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @DisplayName("workspaceId와 githubRepoId로 GitHub repository를 조회한다")
    void findByWorkspaceIdAndGithubRepoId() {
        Workspace workspace = seedWorkspace("repo-find@test.com", "repo-find");
        GithubRepository repository = githubRepositoryRepository.saveAndFlush(repository(workspace, "123456", "team1", "codedock"));
        em.clear();

        Optional<GithubRepository> found =
                githubRepositoryRepository.findByWorkspaceIdAndGithubRepoId(workspace.getId(), "123456");

        assertThat(found).isPresent();
        assertThat(found.orElseThrow().getId()).isEqualTo(repository.getId());
        assertThat(githubRepositoryRepository.findByWorkspaceIdAndGithubRepoId(workspace.getId(), "missing"))
                .isEmpty();
    }

    @Test
    @DisplayName("repository id와 workspace id가 모두 맞을 때만 GitHub repository를 조회한다")
    void findByIdAndWorkspaceId() {
        Workspace workspace = seedWorkspace("repo-workspace@test.com", "repo-workspace");
        Workspace otherWorkspace = seedWorkspace("repo-workspace-other@test.com", "repo-workspace-other");
        GithubRepository repository = githubRepositoryRepository.saveAndFlush(
                repository(workspace, "123456", "team1", "codedock")
        );
        em.clear();

        assertThat(githubRepositoryRepository.findByIdAndWorkspaceId(repository.getId(), workspace.getId()))
                .isPresent();
        assertThat(githubRepositoryRepository.findByIdAndWorkspaceId(repository.getId(), otherWorkspace.getId()))
                .isEmpty();
    }

    @Test
    @DisplayName("레포지토리 현황 PR 쿼리는 open/approved만 세고 최신 수정 시각순으로 반환한다")
    void pullRequestOverviewQueries() {
        Workspace workspace = seedWorkspace("pr-overview@test.com", "pr-overview");
        GithubRepository repository = githubRepositoryRepository.saveAndFlush(
                repository(workspace, "123456", "team1", "codedock")
        );
        Channel channel = channelRepository.saveAndFlush(Channel.createRepository(workspace, repository, "codedock-repo"));
        GithubPullRequest oldOpen = githubPullRequestRepository.saveAndFlush(pullRequest(
                repository, channel, "pr-1", 1, "오래된 열린 PR", "open",
                LocalDateTime.of(2026, 6, 24, 10, 0)
        ));
        GithubPullRequest newestApproved = githubPullRequestRepository.saveAndFlush(pullRequest(
                repository, channel, "pr-2", 2, "최신 승인 PR", "approved",
                LocalDateTime.of(2026, 6, 25, 10, 0)
        ));
        GithubPullRequest closed = githubPullRequestRepository.saveAndFlush(pullRequest(
                repository, channel, "pr-3", 3, "닫힌 PR", "closed",
                LocalDateTime.of(2026, 6, 26, 10, 0)
        ));
        em.clear();

        long openCount = githubPullRequestRepository.countOpenByRepositoryId(repository.getId());
        List<GithubPullRequest> openPullRequests = githubPullRequestRepository.findOpenByRepositoryId(
                repository.getId(),
                PageRequest.of(0, 10)
        );
        List<GithubPullRequest> recentPullRequests = githubPullRequestRepository.findRecentByRepositoryId(
                repository.getId(),
                PageRequest.of(0, 10)
        );

        assertThat(openCount).isEqualTo(2L);
        assertThat(openPullRequests).extracting(GithubPullRequest::getId)
                .containsExactly(newestApproved.getId(), oldOpen.getId());
        assertThat(recentPullRequests).extracting(GithubPullRequest::getId)
                .containsExactly(closed.getId(), newestApproved.getId(), oldOpen.getId());
    }

    @Test
    @DisplayName("레포지토리 현황 Issue 쿼리는 open/high를 구분하고 최신 수정 시각순으로 반환한다")
    void issueOverviewQueries() {
        Workspace workspace = seedWorkspace("issue-overview@test.com", "issue-overview");
        GithubRepository repository = githubRepositoryRepository.saveAndFlush(
                repository(workspace, "123456", "team1", "codedock")
        );
        Channel channel = channelRepository.saveAndFlush(Channel.createRepository(workspace, repository, "codedock-repo"));
        GithubIssue openLow = githubIssueRepository.saveAndFlush(issue(
                repository, channel, "issue-1", 1, "낮은 위험 이슈", "open", "low",
                LocalDateTime.of(2026, 6, 24, 10, 0)
        ));
        GithubIssue openHigh = githubIssueRepository.saveAndFlush(issue(
                repository, channel, "issue-2", 2, "높은 위험 이슈", "open", "high",
                LocalDateTime.of(2026, 6, 25, 10, 0)
        ));
        GithubIssue closedHigh = githubIssueRepository.saveAndFlush(issue(
                repository, channel, "issue-3", 3, "닫힌 높은 위험 이슈", "closed", "high",
                LocalDateTime.of(2026, 6, 26, 10, 0)
        ));
        em.clear();

        long openCount = githubIssueRepository.countOpenByRepositoryId(repository.getId());
        long openHighCount = githubIssueRepository.countOpenHighPriorityByRepositoryId(repository.getId());
        List<GithubIssue> recentIssues = githubIssueRepository.findRecentByRepositoryId(
                repository.getId(),
                PageRequest.of(0, 10)
        );

        assertThat(openCount).isEqualTo(2L);
        assertThat(openHighCount).isEqualTo(1L);
        assertThat(recentIssues).extracting(GithubIssue::getId)
                .containsExactly(closedHigh.getId(), openHigh.getId(), openLow.getId());
    }

    @Test
    @DisplayName("repository 타입 채널만 GitHub repository 채널로 조회한다")
    void findRepositoryChannel() {
        Workspace workspace = seedWorkspace("channel-find@test.com", "channel-find");
        GithubRepository repository = githubRepositoryRepository.saveAndFlush(repository(workspace, "123456", "team1", "codedock"));
        Channel repositoryChannel = channelRepository.saveAndFlush(
                Channel.createRepository(workspace, repository, "codedock-repo")
        );
        channelRepository.saveAndFlush(Channel.createCustom(workspace, "custom-channel", null));
        em.clear();

        Optional<Channel> found = channelRepository.findRepositoryChannel(workspace.getId(), repository.getId());

        assertThat(found).isPresent();
        assertThat(found.orElseThrow().getId()).isEqualTo(repositoryChannel.getId());
        assertThat(found.get().getChannelType()).isEqualTo(Channel.TYPE_REPOSITORY);
    }

    @Test
    @DisplayName("같은 워크스페이스에 같은 채널명은 DB unique 제약으로 저장되지 않는다")
    void channelNameUniqueConstraint() {
        Workspace workspace = seedWorkspace("channel-unique@test.com", "channel-unique");
        channelRepository.saveAndFlush(Channel.createGeneral(workspace));

        assertThatThrownBy(() -> channelRepository.saveAndFlush(Channel.createCustom(workspace, "general", null)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("같은 워크스페이스에 같은 GitHub repo id는 DB unique 제약으로 저장되지 않는다")
    void githubRepositoryUniqueConstraint() {
        Workspace workspace = seedWorkspace("repo-unique@test.com", "repo-unique");
        githubRepositoryRepository.saveAndFlush(repository(workspace, "123456", "team1", "codedock"));

        assertThatThrownBy(() -> githubRepositoryRepository.saveAndFlush(
                repository(workspace, "123456", "other-team", "other-codedock")
        )).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("@DynamicUpdate: Hibernate 세션 밖에서 변경된 컬럼을 UPDATE 시 덮어쓰지 않는다")
    void githubRepository_dynamicUpdate_외부_변경_컬럼을_덮어쓰지_않는다() {
        Workspace workspace = seedWorkspace("dyn-update@test.com", "dyn-update");
        GithubRepository repo = githubRepositoryRepository.saveAndFlush(
                repository(workspace, "DYN001", "team", "old-name"));
        Long id = repo.getId();
        em.clear();

        // Hibernate가 webhook_id=null로 로드한 상태에서, JDBC로 외부 변경 (다른 세션 시뮬레이션)
        GithubRepository session = githubRepositoryRepository.findById(id).orElseThrow();
        jdbcTemplate.update("UPDATE github_repositories SET webhook_id = 'external-hook' WHERE id = ?", id);

        // Hibernate 세션에서는 name만 수정 (webhook_id는 dirty 아님)
        session.updateMetadata("team", "new-name", "team/new-name",
                "https://github.com/team/new-name", null, false, "main");
        githubRepositoryRepository.saveAndFlush(session);
        em.clear();

        // @DynamicUpdate 없으면 UPDATE SET ..., webhook_id=null 로 외부 변경이 덮어써진다
        GithubRepository result = githubRepositoryRepository.findById(id).orElseThrow();
        assertThat(result.getName()).isEqualTo("new-name");
        assertThat(result.getWebhookId()).isEqualTo("external-hook");
    }

    private Workspace seedWorkspace(String email, String slug) {
        User owner = userRepository.save(User.create(email, "hash", "Owner"));
        Workspace workspace = workspaceRepository.save(Workspace.create(owner, "Team", slug, null));
        em.flush();
        return workspace;
    }

    private GithubRepository repository(Workspace workspace, String githubRepoId, String owner, String name) {
        return GithubRepository.create(
                workspace,
                githubRepoId,
                owner,
                name,
                owner + "/" + name,
                "https://github.com/" + owner + "/" + name,
                null,
                true,
                "main"
        );
    }

    private GithubPullRequest pullRequest(
            GithubRepository repository,
            Channel channel,
            String githubPrId,
            Integer prNumber,
            String title,
            String state,
            LocalDateTime githubUpdatedAt
    ) {
        return GithubPullRequest.create(
                repository,
                channel,
                githubPrId,
                prNumber,
                title,
                "description",
                state,
                "https://github.com/team1/codedock/pull/" + prNumber,
                "jean2077",
                "feature/" + prNumber,
                "main",
                "[]",
                10,
                2,
                3,
                null,
                githubUpdatedAt.minusHours(1),
                githubUpdatedAt,
                "[]"
        );
    }

    private GithubIssue issue(
            GithubRepository repository,
            Channel channel,
            String githubIssueId,
            Integer issueNumber,
            String title,
            String state,
            String priority,
            LocalDateTime githubUpdatedAt
    ) {
        GithubIssue issue = GithubIssue.create(
                repository,
                channel,
                githubIssueId,
                issueNumber,
                title,
                "description",
                state,
                "https://github.com/team1/codedock/issues/" + issueNumber,
                "slyhyun",
                "[]",
                "closed".equals(state) ? githubUpdatedAt : null,
                githubUpdatedAt.minusHours(1),
                githubUpdatedAt
        );
        issue.applyClassification(priority, "bug");
        return issue;
    }
}
