package com.team1.codedock.domain.channel.entity;

import com.team1.codedock.domain.github.entity.GithubRepository;
import com.team1.codedock.domain.workspace.entity.Workspace;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.lang.reflect.Constructor;

import static org.assertj.core.api.Assertions.assertThat;

class ChannelTest {

    @Test
    @DisplayName("기본 채널은 general 타입이고 삭제할 수 없다")
    void createGeneralChannel() {
        Workspace workspace = workspace(1L);

        Channel channel = Channel.createGeneral(workspace);

        assertThat(channel.getWorkspace()).isEqualTo(workspace);
        assertThat(channel.getGithubRepository()).isNull();
        assertThat(channel.getName()).isEqualTo(Channel.DEFAULT_GENERAL_NAME);
        assertThat(channel.getChannelType()).isEqualTo(Channel.TYPE_GENERAL);
        assertThat(channel.isDeletable()).isFalse();
        assertThat(channel.getDisplayOrder()).isZero();
    }

    @Test
    @DisplayName("커스텀 채널은 custom 타입이고 삭제할 수 있다")
    void createCustomChannel() {
        Workspace workspace = workspace(1L);

        Channel channel = Channel.createCustom(workspace, "team-chat", "Team chat");

        assertThat(channel.getWorkspace()).isEqualTo(workspace);
        assertThat(channel.getGithubRepository()).isNull();
        assertThat(channel.getName()).isEqualTo("team-chat");
        assertThat(channel.getChannelType()).isEqualTo(Channel.TYPE_CUSTOM);
        assertThat(channel.isDeletable()).isTrue();
        assertThat(channel.getDisplayOrder()).isZero();
        assertThat(channel.getDescription()).isEqualTo("Team chat");
    }

    @Test
    @DisplayName("레포지토리 채널은 repository 타입이고 GitHub repository와 연결된다")
    void createRepositoryChannel() {
        Workspace workspace = workspace(1L);
        GithubRepository githubRepository = githubRepository(10L, "codedock", "CodeDock repository");

        Channel channel = Channel.createRepository(workspace, githubRepository);

        assertThat(channel.getWorkspace()).isEqualTo(workspace);
        assertThat(channel.getGithubRepository()).isEqualTo(githubRepository);
        assertThat(channel.getName()).isEqualTo("codedock");
        assertThat(channel.getChannelType()).isEqualTo(Channel.TYPE_REPOSITORY);
        assertThat(channel.isDeletable()).isFalse();
        assertThat(channel.getDisplayOrder()).isZero();
        assertThat(channel.getDescription()).isEqualTo("CodeDock repository");
    }

    @Test
    @DisplayName("채널 생성 시 표시 순서를 지정할 수 있다")
    void createChannelWithDisplayOrder() {
        Workspace workspace = workspace(1L);
        GithubRepository githubRepository = githubRepository(10L, "codedock", "CodeDock repository");

        Channel general = Channel.createGeneral(workspace, 1);
        Channel custom = Channel.createCustom(workspace, "team-chat", null, 2);
        Channel repository = Channel.createRepository(workspace, githubRepository, "codedock", 3);

        assertThat(general.getDisplayOrder()).isEqualTo(1);
        assertThat(custom.getDisplayOrder()).isEqualTo(2);
        assertThat(repository.getDisplayOrder()).isEqualTo(3);
    }

    @Test
    @DisplayName("채널 표시 순서를 변경할 수 있다")
    void updateDisplayOrder() {
        Workspace workspace = workspace(1L);
        Channel channel = Channel.createCustom(workspace, "team-chat", null, 1);

        channel.updateDisplayOrder(7);

        assertThat(channel.getDisplayOrder()).isEqualTo(7);
    }

    private static Workspace workspace(Long id) {
        Workspace workspace = newInstance(Workspace.class);
        ReflectionTestUtils.setField(workspace, "id", id);
        return workspace;
    }

    private static GithubRepository githubRepository(Long id, String name, String description) {
        GithubRepository githubRepository = newInstance(GithubRepository.class);
        ReflectionTestUtils.setField(githubRepository, "id", id);
        ReflectionTestUtils.setField(githubRepository, "name", name);
        ReflectionTestUtils.setField(githubRepository, "description", description);
        return githubRepository;
    }

    private static <T> T newInstance(Class<T> type) {
        try {
            Constructor<T> constructor = type.getDeclaredConstructor();
            constructor.setAccessible(true);
            return constructor.newInstance();
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Failed to create test entity: " + type.getSimpleName(), e);
        }
    }
}
