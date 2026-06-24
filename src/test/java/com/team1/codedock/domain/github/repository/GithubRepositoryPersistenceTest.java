package com.team1.codedock.domain.github.repository;

import com.team1.codedock.domain.channel.entity.Channel;
import com.team1.codedock.domain.channel.repository.ChannelRepository;
import com.team1.codedock.domain.github.entity.GithubRepository;
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
import org.springframework.jdbc.core.JdbcTemplate;

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
}
