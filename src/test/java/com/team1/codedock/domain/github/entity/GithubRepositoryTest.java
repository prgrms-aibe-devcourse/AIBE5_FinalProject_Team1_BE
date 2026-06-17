package com.team1.codedock.domain.github.entity;

import com.team1.codedock.domain.workspace.entity.Workspace;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class GithubRepositoryTest {

    @Test
    @DisplayName("create()로 GithubRepository를 생성하면 모든 필드가 설정된다")
    void createGithubRepository() {
        Workspace workspace = mock(Workspace.class);

        GithubRepository repository = GithubRepository.create(
                workspace,
                "123456",
                "team1",
                "codedock",
                "team1/codedock",
                "https://github.com/team1/codedock",
                "CodeDock backend",
                true,
                "main"
        );

        assertThat(repository.getWorkspace()).isEqualTo(workspace);
        assertThat(repository.getGithubRepoId()).isEqualTo("123456");
        assertThat(repository.getOwner()).isEqualTo("team1");
        assertThat(repository.getName()).isEqualTo("codedock");
        assertThat(repository.getFullName()).isEqualTo("team1/codedock");
        assertThat(repository.getUrl()).isEqualTo("https://github.com/team1/codedock");
        assertThat(repository.getDescription()).isEqualTo("CodeDock backend");
        assertThat(repository.isPrivate()).isTrue();
        assertThat(repository.getDefaultBranch()).isEqualTo("main");
        assertThat(repository.isWebhookActive()).isFalse();
    }

    @Test
    @DisplayName("updateMetadata()로 GitHub repository 메타데이터를 갱신한다")
    void updateMetadata() {
        Workspace workspace = mock(Workspace.class);
        GithubRepository repository = GithubRepository.create(
                workspace,
                "123456",
                "team1",
                "codedock",
                "team1/codedock",
                "https://github.com/team1/codedock",
                "Old description",
                true,
                "main"
        );

        repository.updateMetadata(
                "team-one",
                "codedock-be",
                "team-one/codedock-be",
                "https://github.com/team-one/codedock-be",
                "New description",
                false,
                "develop"
        );

        assertThat(repository.getOwner()).isEqualTo("team-one");
        assertThat(repository.getName()).isEqualTo("codedock-be");
        assertThat(repository.getFullName()).isEqualTo("team-one/codedock-be");
        assertThat(repository.getUrl()).isEqualTo("https://github.com/team-one/codedock-be");
        assertThat(repository.getDescription()).isEqualTo("New description");
        assertThat(repository.isPrivate()).isFalse();
        assertThat(repository.getDefaultBranch()).isEqualTo("develop");
    }
}
