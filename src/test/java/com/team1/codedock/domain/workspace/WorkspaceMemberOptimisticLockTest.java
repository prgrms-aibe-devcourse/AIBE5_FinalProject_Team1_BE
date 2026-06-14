package com.team1.codedock.domain.workspace;

import com.team1.codedock.domain.user.entity.User;
import com.team1.codedock.domain.user.repository.UserRepository;
import com.team1.codedock.domain.workspace.entity.Workspace;
import com.team1.codedock.domain.workspace.entity.WorkspaceMember;
import com.team1.codedock.domain.workspace.repository.WorkspaceMemberRepository;
import com.team1.codedock.domain.workspace.repository.WorkspaceRepository;
import com.team1.codedock.global.config.JpaConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.dao.OptimisticLockingFailureException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@Import(JpaConfig.class)
class WorkspaceMemberOptimisticLockTest {

    @Autowired
    private TestEntityManager em;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private WorkspaceRepository workspaceRepository;

    @Autowired
    private WorkspaceMemberRepository workspaceMemberRepository;

    private Long seedMember(String email, String slug) {
        User owner = userRepository.save(User.create(email, "hash", "Owner"));
        Workspace workspace = workspaceRepository.save(Workspace.create(owner, "Team", slug, null));
        WorkspaceMember member = workspaceMemberRepository.save(WorkspaceMember.create(workspace, owner, "editor"));
        em.flush();
        return member.getId();
    }

    @Test
    @DisplayName("동일 멤버를 동시에 수정하면 두 번째 저장은 낙관적 락 충돌로 실패한다")
    void concurrentAuthorityChange_throwsOptimisticLockingFailure() {
        Long memberId = seedMember("owner1@test.com", "team-slug-1");
        em.clear();

        WorkspaceMember stale = workspaceMemberRepository.findById(memberId).orElseThrow();
        em.detach(stale);

        WorkspaceMember fresh = workspaceMemberRepository.findById(memberId).orElseThrow();
        fresh.changeAuthority("admin");
        workspaceMemberRepository.saveAndFlush(fresh);

        stale.changeAuthority("viewer");
        assertThatThrownBy(() -> workspaceMemberRepository.saveAndFlush(stale))
                .isInstanceOf(OptimisticLockingFailureException.class);
    }

    @Test
    @DisplayName("멤버 권한을 수정하면 @Version 값이 증가한다")
    void version_increments_onUpdate() {
        Long memberId = seedMember("owner2@test.com", "team-slug-2");
        em.clear();

        WorkspaceMember loaded = workspaceMemberRepository.findById(memberId).orElseThrow();
        assertThat(loaded.getVersion()).isEqualTo(0L);

        loaded.changeAuthority("admin");
        workspaceMemberRepository.saveAndFlush(loaded);
        em.clear();

        WorkspaceMember reloaded = workspaceMemberRepository.findById(memberId).orElseThrow();
        assertThat(reloaded.getVersion()).isEqualTo(1L);
    }
}
