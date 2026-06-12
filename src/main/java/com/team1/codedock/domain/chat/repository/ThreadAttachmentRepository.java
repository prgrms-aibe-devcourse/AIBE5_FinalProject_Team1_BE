package com.team1.codedock.domain.chat.repository;

import com.team1.codedock.domain.chat.entity.ThreadAttachment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ThreadAttachmentRepository extends JpaRepository<ThreadAttachment, Long> {

    List<ThreadAttachment> findAllByThread_IdOrderByIdAsc(Long threadId);

    List<ThreadAttachment> findAllByThread_IdInOrderByThread_IdAscIdAsc(List<Long> threadIds);
}
