package com.team1.codedock.domain.document.repository;

import com.team1.codedock.domain.document.entity.ErdTable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ErdTableRepository extends JpaRepository<ErdTable, Long> {

    List<ErdTable> findAllByWorkspace_Id(Long workspaceId);

    void deleteAllByWorkspace_Id(Long workspaceId);
}
