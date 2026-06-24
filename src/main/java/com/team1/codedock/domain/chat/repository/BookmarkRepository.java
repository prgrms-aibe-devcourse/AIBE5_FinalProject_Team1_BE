package com.team1.codedock.domain.chat.repository;

import com.team1.codedock.domain.chat.entity.Bookmark;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface BookmarkRepository extends JpaRepository<Bookmark, Long> {

    // 해당 멤버가 이미 같은 메시지를 북마크했는지 확인함
    Optional<Bookmark> findByWorkspaceMember_IdAndThread_Id(Long workspaceMemberId, Long threadId);

    // 북마크 목록을 최신 저장 순서로 조회함
    List<Bookmark> findAllByWorkspaceMember_IdOrderByCreatedAtDesc(Long workspaceMemberId);

    // 메시지(스레드)가 삭제될 때 해당 메시지를 가리키는 모든 북마크를 함께 제거함
    void deleteAllByThread_Id(Long threadId);

    @Modifying
    @Query(value = """
            DELETE FROM bookmarks
            WHERE thread_id IN (
                SELECT id
                FROM threads
                WHERE channel_id = :channelId
            )
            """, nativeQuery = true)
    void deleteAllByThreadChannelId(@Param("channelId") Long channelId);
}
