package com.team1.codedock.domain.chat.repository;

import com.team1.codedock.domain.chat.entity.ThreadReply;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ThreadReplyRepository extends JpaRepository<ThreadReply, Long> {

    List<ThreadReply> findAllByThread_IdOrderByCreatedAtAscIdAsc(Long threadId);
}
