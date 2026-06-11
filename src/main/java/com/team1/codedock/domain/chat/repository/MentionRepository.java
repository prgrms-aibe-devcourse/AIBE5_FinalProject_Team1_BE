package com.team1.codedock.domain.chat.repository;

import com.team1.codedock.domain.chat.entity.Mention;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface MentionRepository extends JpaRepository<Mention, Long> {

    // 내 멘션 목록을 최신 생성 순서로 조회함
    List<Mention> findAllByWorkspace_IdAndMentionedMember_IdOrderByCreatedAtDesc(
            Long workspaceId,
            Long mentionedMemberId
    );

    Optional<Mention> findByIdAndMentionedMember_Id(Long id, Long mentionedMemberId);
}
