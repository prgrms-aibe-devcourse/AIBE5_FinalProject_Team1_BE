package com.team1.codedock.domain.issue.repository;

import com.team1.codedock.domain.channel.entity.Channel;
import com.team1.codedock.domain.channel.repository.ChannelRepository;
import com.team1.codedock.domain.github.entity.GithubRepository;
import com.team1.codedock.domain.github.repository.GithubRepositoryRepository;
import com.team1.codedock.domain.issue.entity.GithubIssue;
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
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@Import(JpaConfig.class)
class GithubIssueRepositoryPersistenceTest {

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
    private GithubIssueRepository githubIssueRepository;

    @Test
    @DisplayName("closed 이슈는 DB 저장 후 다시 조회해도 작업보드 done 상태를 유지한다")
    void closedIssue_persistsDoneLocalStatus() {
        Seed seed = seed("closed-persist@test.com", "closed-persist");
        GithubIssue closedIssue = githubIssueRepository.saveAndFlush(issue(
                seed.repository(),
                seed.channel(),
                "issue-1",
                1,
                "닫힌 이슈",
                "closed"
        ));
        em.clear();

        GithubIssue found = githubIssueRepository
                .findByIdAndRepository_Workspace_Id(closedIssue.getId(), seed.workspace().getId())
                .orElseThrow();

        assertThat(found.getState()).isEqualTo("closed");
        assertThat(found.getLocalStatus()).isEqualTo("done");
        assertThat(found.isClosed()).isTrue();
    }

    @Test
    @DisplayName("닫혔다가 다시 열린 이슈는 DB 저장 후 todo 상태로 재조회된다")
    void reopenedIssue_persistsTodoLocalStatus() {
        Seed seed = seed("reopen-persist@test.com", "reopen-persist");
        GithubIssue issue = githubIssueRepository.saveAndFlush(issue(
                seed.repository(),
                seed.channel(),
                "issue-2",
                2,
                "다시 열릴 이슈",
                "closed"
        ));
        em.clear();

        GithubIssue found = githubIssueRepository.findById(issue.getId()).orElseThrow();
        found.syncFromWebhook(
                "다시 열린 이슈",
                "본문",
                "open",
                "https://github.com/team/repo/issues/2",
                "[]",
                null,
                LocalDateTime.of(2026, 6, 25, 12, 0)
        );
        githubIssueRepository.saveAndFlush(found);
        em.clear();

        GithubIssue reopened = githubIssueRepository.findById(issue.getId()).orElseThrow();

        assertThat(reopened.getState()).isEqualTo("open");
        assertThat(reopened.getLocalStatus()).isEqualTo("todo");
        assertThat(reopened.getClosedAt()).isNull();
    }

    @Test
    @DisplayName("작업보드 필터 쿼리는 closed 이슈를 done으로만 조회하고 todo에서는 제외한다")
    void boardFilter_closedIssueOnlyAppearsInDone() {
        Seed seed = seed("board-filter@test.com", "board-filter");
        GithubIssue openIssue = githubIssueRepository.saveAndFlush(issue(
                seed.repository(),
                seed.channel(),
                "issue-3",
                3,
                "열린 이슈",
                "open"
        ));
        GithubIssue closedIssue = githubIssueRepository.saveAndFlush(issue(
                seed.repository(),
                seed.channel(),
                "issue-4",
                4,
                "닫힌 이슈",
                "closed"
        ));
        em.clear();

        Page<GithubIssue> todoPage = githubIssueRepository.findByWorkspaceWithFilters(
                seed.workspace().getId(),
                seed.repository().getId(),
                null,
                "todo",
                PageRequest.of(0, 10)
        );
        Page<GithubIssue> donePage = githubIssueRepository.findByWorkspaceWithFilters(
                seed.workspace().getId(),
                seed.repository().getId(),
                null,
                "done",
                PageRequest.of(0, 10)
        );

        assertThat(todoPage.getContent()).extracting(GithubIssue::getId)
                .containsExactly(openIssue.getId());
        assertThat(donePage.getContent()).extracting(GithubIssue::getId)
                .containsExactly(closedIssue.getId());
        assertThat(donePage.getContent().get(0).getState()).isEqualTo("closed");
    }

    @Test
    @DisplayName("작업보드 전체 목록 쿼리도 closed 이슈를 done 상태로 반환한다")
    void findAllByWorkspaceId_returnsClosedIssueAsDone() {
        Seed seed = seed("board-list@test.com", "board-list");
        githubIssueRepository.saveAndFlush(issue(
                seed.repository(),
                seed.channel(),
                "issue-5",
                5,
                "열린 이슈",
                "open"
        ));
        githubIssueRepository.saveAndFlush(issue(
                seed.repository(),
                seed.channel(),
                "issue-6",
                6,
                "닫힌 이슈",
                "closed"
        ));
        em.clear();

        List<GithubIssue> issues = githubIssueRepository.findAllByWorkspaceId(seed.workspace().getId());

        assertThat(issues)
                .extracting(GithubIssue::getState, GithubIssue::getLocalStatus)
                .contains(
                        org.assertj.core.groups.Tuple.tuple("closed", "done"),
                        org.assertj.core.groups.Tuple.tuple("open", "todo")
                );
    }

    private Seed seed(String email, String slug) {
        User user = userRepository.saveAndFlush(User.create(email, "pw", "오너"));
        Workspace workspace = workspaceRepository.saveAndFlush(Workspace.create(user, "팀", slug, null));
        GithubRepository repository = githubRepositoryRepository.saveAndFlush(GithubRepository.create(
                workspace,
                slug + "-repo",
                "team",
                "repo",
                "team/repo",
                "https://github.com/team/repo",
                null,
                false,
                "main"
        ));
        Channel channel = channelRepository.saveAndFlush(Channel.createRepository(workspace, repository, slug + "-repo"));
        return new Seed(workspace, repository, channel);
    }

    private GithubIssue issue(
            GithubRepository repository,
            Channel channel,
            String githubIssueId,
            int issueNumber,
            String title,
            String state
    ) {
        return GithubIssue.create(
                repository,
                channel,
                githubIssueId,
                issueNumber,
                title,
                "본문",
                state,
                "https://github.com/team/repo/issues/" + issueNumber,
                "octocat",
                "[]",
                "closed".equals(state) ? LocalDateTime.of(2026, 6, 25, 10, 0) : null,
                LocalDateTime.of(2026, 6, 24, 10, 0),
                LocalDateTime.of(2026, 6, 25, 11, 0)
        );
    }

    private record Seed(
            Workspace workspace,
            GithubRepository repository,
            Channel channel
    ) {
    }
}
