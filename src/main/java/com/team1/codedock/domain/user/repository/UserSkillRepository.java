package com.team1.codedock.domain.user.repository;

import com.team1.codedock.domain.user.entity.User;
import com.team1.codedock.domain.user.entity.UserSkill;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface UserSkillRepository extends JpaRepository<UserSkill, Long> {

    List<UserSkill> findAllByUser(User user);

    @Modifying
    @Query("DELETE FROM UserSkill s WHERE s.user = :user")
    void deleteAllByUser(@Param("user") User user);
}