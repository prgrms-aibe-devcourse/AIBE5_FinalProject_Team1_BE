package com.team1.codedock.domain.channel.repository;

import com.team1.codedock.domain.channel.entity.Channel;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ChannelRepository extends JpaRepository<Channel, Long> {

    List<Channel> findAllByWorkspace_IdOrderByDisplayOrderAscIdAsc(Long workspaceId);

    @Query("SELECT COALESCE(MAX(c.displayOrder), -1) FROM Channel c WHERE c.workspace.id = :workspaceId")
    int findMaxDisplayOrderByWorkspaceId(@Param("workspaceId") Long workspaceId);

    @Query("SELECT c.workspace.id FROM Channel c WHERE c.id = :channelId")
    Optional<Long> findWorkspaceIdById(@Param("channelId") Long channelId);

    @Query("SELECT COUNT(c) FROM Channel c WHERE c.workspace.id = :workspaceId AND LOWER(c.name) = LOWER(:name)")
    long countByWorkspaceIdAndNameIgnoreCase(@Param("workspaceId") Long workspaceId, @Param("name") String name);

    @Query("""
            SELECT COUNT(c)
            FROM Channel c
            WHERE c.workspace.id = :workspaceId
              AND LOWER(c.name) = LOWER(:name)
              AND c.id <> :id
            """)
    long countByWorkspaceIdAndNameIgnoreCaseAndIdNot(
            @Param("workspaceId") Long workspaceId,
            @Param("name") String name,
            @Param("id") Long id
    );

    // 연결된 GitHub 레포지토리 하나에 대응되는 레포지토리 채널 조회함.
    @Query("""
            SELECT c
            FROM Channel c
            WHERE c.workspace.id = :workspaceId
              AND c.githubRepository.id = :githubRepositoryId
              AND c.channelType = 'repository'
            """)
    Optional<Channel> findRepositoryChannel(
            @Param("workspaceId") Long workspaceId,
            @Param("githubRepositoryId") Long githubRepositoryId
    );
}
