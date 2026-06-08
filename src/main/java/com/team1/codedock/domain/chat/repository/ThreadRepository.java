package com.team1.codedock.domain.chat.repository;

import com.team1.codedock.domain.chat.entity.Thread;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ThreadRepository extends JpaRepository<Thread, Long> {
}
