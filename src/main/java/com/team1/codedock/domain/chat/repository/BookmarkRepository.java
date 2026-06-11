package com.team1.codedock.domain.chat.repository;

import com.team1.codedock.domain.chat.entity.Bookmark;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface BookmarkRepository extends JpaRepository<Bookmark, Long> {

    // 한 멤버는 같은 메시지를 한 번만 북마크할 수 있다.
    Optional<Bookmark> findByWorkspaceMember_IdAndThread_Id(Long workspaceMemberId, Long threadId);

    // 내 북마크 목록을 최신 북마크 순서로 조회한다.
    List<Bookmark> findAllByWorkspaceMember_IdOrderByCreatedAtDesc(Long workspaceMemberId);
}
