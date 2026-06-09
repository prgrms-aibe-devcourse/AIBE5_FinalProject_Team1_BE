package com.team1.codedock.domain.chat.repository;

import com.team1.codedock.domain.chat.entity.Reaction;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ReactionRepository extends JpaRepository<Reaction, Long> {

    Optional<Reaction> findByWorkspaceMember_IdAndTargetTypeAndTargetIdAndEmoji(
            Long workspaceMemberId,
            String targetType,
            Long targetId,
            String emoji
    );

    long countByTargetTypeAndTargetIdAndEmoji(String targetType, Long targetId, String emoji);
}
