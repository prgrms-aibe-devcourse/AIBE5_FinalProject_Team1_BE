package com.team1.codedock.domain.github.repository;

import com.team1.codedock.domain.github.entity.GithubRepository;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface GithubRepositoryRepository extends JpaRepository<GithubRepository, Long> {

    @Query("SELECT g FROM GithubRepository g WHERE g.workspace.id = :workspaceId")
    List<GithubRepository> findByWorkspaceId(@Param("workspaceId") Long workspaceId);

    @Query("""
            SELECT g
            FROM GithubRepository g
            WHERE g.workspace.id = :workspaceId
              AND g.githubRepoId = :githubRepoId
            """)
    Optional<GithubRepository> findByWorkspaceIdAndGithubRepoId(
            @Param("workspaceId") Long workspaceId,
            @Param("githubRepoId") String githubRepoId
    );

    // 웹훅 수신용: GitHub repo id는 불변이므로 DB 재생성/재연결과 무관하게 레포를 찾는다.
    // 같은 GitHub 레포가 여러 워크스페이스에 연결될 수 있어 목록으로 반환한다.
    List<GithubRepository> findAllByGithubRepoId(String githubRepoId);
}
