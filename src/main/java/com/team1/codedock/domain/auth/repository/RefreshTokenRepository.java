package com.team1.codedock.domain.auth.repository;

import com.team1.codedock.domain.auth.entity.RefreshToken;
import com.team1.codedock.domain.user.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {

    Optional<RefreshToken> findByToken(String token);

    void deleteByUser(User user);
}