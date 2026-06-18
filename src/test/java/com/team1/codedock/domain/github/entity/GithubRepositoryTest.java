package com.team1.codedock.domain.github.entity;

import com.team1.codedock.domain.workspace.entity.Workspace;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class GithubRepositoryTest {

    @Test
    @DisplayName("create()로 GithubRepository를 생성하면 모든 필드가 정상적으로 설정된다")
    void create_성공_모든_필드_설정() {
        Workspace workspace = mock(Workspace.class);

        GithubRepository repo = GithubRepository.create(
                workspace,
                "12345",
                "octocat",
                "hello-world",
                "octocat/hello-world",
                "https://github.com/octocat/hello-world",
                "Hello World repo",
                false,
                "main"
        );

        assertThat(repo.getWorkspace()).isEqualTo(workspace);
        assertThat(repo.getGithubRepoId()).isEqualTo("12345");
        assertThat(repo.getOwner()).isEqualTo("octocat");
        assertThat(repo.getName()).isEqualTo("hello-world");
        assertThat(repo.getFullName()).isEqualTo("octocat/hello-world");
        assertThat(repo.getUrl()).isEqualTo("https://github.com/octocat/hello-world");
        assertThat(repo.getDescription()).isEqualTo("Hello World repo");
        assertThat(repo.isPrivate()).isFalse();
        assertThat(repo.getDefaultBranch()).isEqualTo("main");
        assertThat(repo.isWebhookActive()).isFalse();
    }
}
