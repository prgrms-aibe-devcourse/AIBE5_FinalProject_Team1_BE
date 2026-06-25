package com.team1.codedock.domain.issue.entity;

import com.team1.codedock.domain.channel.entity.Channel;
import com.team1.codedock.domain.github.entity.GithubRepository;
import com.team1.codedock.domain.user.entity.User;
import com.team1.codedock.domain.workspace.entity.Workspace;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class GithubIssueTest {

    @Test
    @DisplayName("신규 open 이슈는 작업보드 todo 상태로 생성된다")
    void create_openIssue_localStatusTodo() {
        GithubIssue issue = issue("open");

        assertThat(issue.getState()).isEqualTo("open");
        assertThat(issue.getLocalStatus()).isEqualTo("todo");
        assertThat(issue.isClosed()).isFalse();
    }

    @Test
    @DisplayName("신규 closed 이슈는 작업보드 done 상태로 생성된다")
    void create_closedIssue_localStatusDone() {
        GithubIssue issue = issue("closed");

        assertThat(issue.getState()).isEqualTo("closed");
        assertThat(issue.getLocalStatus()).isEqualTo("done");
        assertThat(issue.isClosed()).isTrue();
    }

    @Test
    @DisplayName("GitHub state 대소문자가 달라도 closed 이슈는 done 상태로 생성된다")
    void create_closedIssueCaseInsensitive_localStatusDone() {
        GithubIssue issue = issue("CLOSED");

        assertThat(issue.getState()).isEqualTo("CLOSED");
        assertThat(issue.getLocalStatus()).isEqualTo("done");
        assertThat(issue.isClosed()).isTrue();
    }

    @Test
    @DisplayName("GitHub state가 null이면 작업보드 기본 상태를 todo로 보정한다")
    void create_nullState_localStatusTodo() {
        GithubIssue issue = issue(null);

        assertThat(issue.getState()).isNull();
        assertThat(issue.getLocalStatus()).isEqualTo("todo");
        assertThat(issue.isClosed()).isFalse();
    }

    @Test
    @DisplayName("GitHub에서 닫힌 이슈는 기존 보드 상태와 무관하게 done으로 이동한다")
    void syncFromWebhook_closedIssue_forcesDone() {
        GithubIssue issue = issue("open");
        issue.updateLocalStatus("in_progress");

        issue.syncFromWebhook(
                "닫힌 이슈",
                "설명",
                "closed",
                "https://github.com/team/repo/issues/1",
                "[]",
                LocalDateTime.of(2026, 6, 25, 12, 0),
                LocalDateTime.of(2026, 6, 25, 12, 1)
        );

        assertThat(issue.getState()).isEqualTo("closed");
        assertThat(issue.getLocalStatus()).isEqualTo("done");
        assertThat(issue.getClosedAt()).isEqualTo(LocalDateTime.of(2026, 6, 25, 12, 0));
    }

    @Test
    @DisplayName("GitHub에서 다시 열린 이슈는 done에서 todo로 돌아온다")
    void syncFromWebhook_reopenedIssue_movesDoneBackToTodo() {
        GithubIssue issue = issue("closed");
        assertThat(issue.getLocalStatus()).isEqualTo("done");

        issue.syncFromWebhook(
                "다시 열린 이슈",
                "설명",
                "open",
                "https://github.com/team/repo/issues/1",
                "[]",
                null,
                LocalDateTime.of(2026, 6, 25, 12, 1)
        );

        assertThat(issue.getState()).isEqualTo("open");
        assertThat(issue.getLocalStatus()).isEqualTo("todo");
        assertThat(issue.isClosed()).isFalse();
    }

    @Test
    @DisplayName("열려 있는 이슈는 GitHub 동기화 후에도 사용자가 옮긴 작업보드 상태를 유지한다")
    void syncFromWebhook_openIssue_keepsManualLocalStatus() {
        GithubIssue issue = issue("open");
        issue.updateLocalStatus("review");

        issue.syncFromWebhook(
                "수정된 이슈",
                "설명",
                "open",
                "https://github.com/team/repo/issues/1",
                "[]",
                null,
                LocalDateTime.of(2026, 6, 25, 12, 1)
        );

        assertThat(issue.getState()).isEqualTo("open");
        assertThat(issue.getLocalStatus()).isEqualTo("review");
    }

    @Test
    @DisplayName("기존 localStatus가 깨져 있어도 열린 이슈 동기화 시 todo로 보정한다")
    void syncFromWebhook_openIssueWithBlankLocalStatus_fallsBackToTodo() {
        GithubIssue issue = issue("open");
        ReflectionTestUtils.setField(issue, "localStatus", " ");

        issue.syncFromWebhook(
                "열린 이슈",
                "설명",
                "open",
                "https://github.com/team/repo/issues/1",
                "[]",
                null,
                LocalDateTime.of(2026, 6, 25, 12, 1)
        );

        assertThat(issue.getState()).isEqualTo("open");
        assertThat(issue.getLocalStatus()).isEqualTo("todo");
    }

    @Test
    @DisplayName("closed 이슈의 effective localStatus는 DB 값이 어긋나도 done으로 해석한다")
    void getEffectiveLocalStatus_closedIssueWithDirtyLocalStatus_returnsDone() {
        GithubIssue issue = issue("closed");
        ReflectionTestUtils.setField(issue, "localStatus", "todo");

        assertThat(issue.getLocalStatus()).isEqualTo("todo");
        assertThat(issue.getEffectiveLocalStatus()).isEqualTo("done");
    }

    @Test
    @DisplayName("open 이슈의 effective localStatus는 비어 있으면 todo로 해석한다")
    void getEffectiveLocalStatus_openIssueWithBlankLocalStatus_returnsTodo() {
        GithubIssue issue = issue("open");
        ReflectionTestUtils.setField(issue, "localStatus", " ");

        assertThat(issue.getLocalStatus()).isBlank();
        assertThat(issue.getEffectiveLocalStatus()).isEqualTo("todo");
    }

    @Test
    @DisplayName("open 이슈의 effective localStatus는 사용자가 옮긴 컬럼을 유지한다")
    void getEffectiveLocalStatus_openIssueWithManualLocalStatus_returnsManualStatus() {
        GithubIssue issue = issue("open");
        issue.updateLocalStatus("blocked");

        assertThat(issue.getEffectiveLocalStatus()).isEqualTo("blocked");
    }

    private static GithubIssue issue(String state) {
        Workspace workspace = Workspace.create(User.create("owner@example.com", "pw", "오너"), "팀", "team", null);
        GithubRepository repository = GithubRepository.create(
                workspace,
                "100",
                "team",
                "repo",
                "team/repo",
                "https://github.com/team/repo",
                null,
                false,
                "main"
        );
        Channel channel = Channel.createRepository(workspace, repository, "repo");

        return GithubIssue.create(
                repository,
                channel,
                "9001",
                1,
                "이슈",
                "설명",
                state,
                "https://github.com/team/repo/issues/1",
                "octocat",
                "[]",
                "closed".equalsIgnoreCase(state) ? LocalDateTime.of(2026, 6, 25, 12, 0) : null,
                LocalDateTime.of(2026, 6, 24, 10, 0),
                LocalDateTime.of(2026, 6, 25, 11, 0)
        );
    }
}
