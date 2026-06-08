package com.team1.codedock.domain.user.repository;

import com.team1.codedock.domain.user.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByGithubId(String githubId);
    Optional<User> findByEmail(String email);
    boolean existsByUsername(String username);
}
