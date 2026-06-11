package com.team1.codedock.domain.chat.repository;

import com.team1.codedock.domain.chat.entity.Bookmark;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface BookmarkRepository extends JpaRepository<Bookmark, Long> {

    // 해당 멤버가 이미 같은 메시지를 북마크했는지 확인함
    Optional<Bookmark> findByWorkspaceMember_IdAndThread_Id(Long workspaceMemberId, Long threadId);

    // 북마크 목록을 최신 저장 순서로 조회함
    List<Bookmark> findAllByWorkspaceMember_IdOrderByCreatedAtDesc(Long workspaceMemberId);
}
