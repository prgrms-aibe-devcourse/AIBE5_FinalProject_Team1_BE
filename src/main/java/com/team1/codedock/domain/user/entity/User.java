package com.team1.codedock.domain.user.entity;

import com.team1.codedock.global.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "users")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class User extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "seq_users")
    @SequenceGenerator(name = "seq_users", sequenceName = "seq_users", allocationSize = 1)
    private Long id;

    @Column(nullable = false, unique = true, length = 255)
    private String email;

    @Column(name = "password_hash", length = 255)
    private String passwordHash;

    @Column(name = "email_verified", nullable = false)
    private boolean emailVerified;

    @Column(name = "email_verified_at")
    private LocalDateTime emailVerifiedAt;

    @Column(nullable = false, unique = true, length = 100)
    private String username;

    @Column(name = "display_name", length = 100)
    private String displayName;

    @Column(length = 50)
    private String nickname;

    @Column(name = "developer_type", length = 100)
    private String developerType;

    @Column(length = 160)
    private String bio;

    @Lob
    @Column(name = "avatar_url")
    private String avatarUrl;

    @Column(name = "github_id", unique = true, length = 100)
    private String githubId;

    @Column(name = "github_username", length = 100)
    private String githubUsername;

    @Column(name = "github_email", length = 255)
    private String githubEmail;

    @Column(name = "github_connected", nullable = false)
    private boolean githubConnected;

    @Column(name = "github_connected_at")
    private LocalDateTime githubConnectedAt;

    @Lob
    @Column(name = "github_access_token")
    private String githubAccessToken;

    @Column(name = "github_token_expires_at")
    private LocalDateTime githubTokenExpiresAt;

    @Column(name = "is_active", nullable = false)
    private boolean isActive;

    @Column(name = "deactivated_at")
    private LocalDateTime deactivatedAt;

    @Column(name = "last_login_at")
    private LocalDateTime lastLoginAt;

    public static User create(String email, String passwordHash, String username) {
        User user = new User();
        user.email = email;
        user.passwordHash = passwordHash;
        user.username = username;
        user.isActive = true;
        user.emailVerified = false;
        user.githubConnected = false;
        return user;
    }

    public void updateLastLogin() {
        this.lastLoginAt = LocalDateTime.now();
    }
}
