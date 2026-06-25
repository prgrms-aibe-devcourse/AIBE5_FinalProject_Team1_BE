package com.team1.codedock.domain.issue.dto;

import com.team1.codedock.domain.channel.entity.Channel;
import com.team1.codedock.domain.github.entity.GithubRepository;
import com.team1.codedock.domain.issue.entity.GithubIssue;
import com.team1.codedock.domain.user.entity.User;
import com.team1.codedock.domain.workspace.entity.Workspace;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class IssueResponseTest {

    @Test
    @DisplayName("closed 이슈가 기존 DB에서 todo로 남아 있어도 응답은 done으로 보정한다")
    void from_closedIssueWithDirtyLocalStatus_returnsDone() {
        GithubIssue issue = issue("closed");
        ReflectionTestUtils.setField(issue, "localStatus", "todo");

        IssueResponse response = IssueResponse.from(issue, List.of(), List.of());

        assertThat(response.state()).isEqualTo("closed");
        assertThat(response.localStatus()).isEqualTo("done");
    }

    @Test
    @DisplayName("open 이슈의 localStatus가 비어 있으면 응답은 todo로 보정한다")
    void from_openIssueWithBlankLocalStatus_returnsTodo() {
        GithubIssue issue = issue("open");
        ReflectionTestUtils.setField(issue, "localStatus", " ");

        IssueResponse response = IssueResponse.from(issue, List.of(), List.of());

        assertThat(response.state()).isEqualTo("open");
        assertThat(response.localStatus()).isEqualTo("todo");
    }

    @Test
    @DisplayName("open 이슈의 수동 작업보드 상태는 응답에서 유지한다")
    void from_openIssueWithManualLocalStatus_keepsManualStatus() {
        GithubIssue issue = issue("open");
        issue.updateLocalStatus("review");

        IssueResponse response = IssueResponse.from(issue, List.of(), List.of());

        assertThat(response.state()).isEqualTo("open");
        assertThat(response.localStatus()).isEqualTo("review");
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
                "본문",
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
