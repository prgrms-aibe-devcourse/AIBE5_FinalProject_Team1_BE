package com.team1.codedock.domain.user.entity;

import com.team1.codedock.global.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Locale;

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

    @Column(name = "avatar_url", columnDefinition = "CLOB")
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

    @Column(name = "github_access_token", columnDefinition = "CLOB")
    private String githubAccessToken;

    @Column(name = "github_token_expires_at")
    private LocalDateTime githubTokenExpiresAt;

    @Column(name = "is_active", nullable = false)
    private boolean isActive;

    @Column(name = "deactivated_at")
    private LocalDateTime deactivatedAt;

    @Column(name = "last_login_at")
    private LocalDateTime lastLoginAt;

    private static String normalizeEmail(String email) {
        return (email == null) ? null : email.trim().toLowerCase(Locale.ROOT);
    }

    public static User create(String email, String passwordHash, String displayName) {
        User user = new User();
        user.email = normalizeEmail(email);
        user.passwordHash = passwordHash;
        user.username = normalizeEmail(email);
        user.displayName = displayName;
        user.isActive = true;
        user.emailVerified = false;
        user.githubConnected = false;
        return user;
    }

    // GitHub OAuth 가입
    public static User createFromGithub(String githubId, String githubUsername,
                                        String email, String avatarUrl,
                                        String githubAccessToken) {
        User user = new User();
        user.githubId = githubId;
        user.githubUsername = githubUsername;
        String resolved = (email != null && !email.isBlank())
                ? email
                : githubId + "+" + githubUsername + "@users.noreply.github.com";
        user.githubEmail = normalizeEmail(resolved);
        user.email = normalizeEmail(resolved);
        user.username = githubUsername;
        user.displayName = githubUsername;
        user.avatarUrl = avatarUrl;
        user.githubConnected = true;
        user.githubConnectedAt = LocalDateTime.now();
        user.githubAccessToken = githubAccessToken;
        user.emailVerified = (email != null && !email.isBlank());
        user.emailVerifiedAt = user.emailVerified ? LocalDateTime.now() : null;
        user.isActive = true;
        user.lastLoginAt = LocalDateTime.now();
        return user;
    }

    public void updateLastLogin() {
        this.lastLoginAt = LocalDateTime.now();
    }

    public void updateOnGithubLogin(String githubAccessToken, String avatarUrl) {
        this.githubAccessToken = githubAccessToken;
        if (avatarUrl != null) this.avatarUrl = avatarUrl;
        this.lastLoginAt = LocalDateTime.now();
        if (!this.githubConnected) {
            this.githubConnected = true;
            this.githubConnectedAt = LocalDateTime.now();
        }
    }

    public void updateOnGithubLogin(String githubAccessToken, String avatarUrl, String githubEmail) {
        updateOnGithubLogin(githubAccessToken, avatarUrl);
        if (githubEmail != null && !githubEmail.isBlank()) {
            this.githubEmail = normalizeEmail(githubEmail);
        }
    }

    public void linkGithub(String githubId, String githubUsername, String githubEmail,
                           String avatarUrl, String githubAccessToken) {
        this.githubId = githubId;
        this.githubUsername = githubUsername;
        if (githubEmail != null && !githubEmail.isBlank()) {
            this.githubEmail = normalizeEmail(githubEmail);
        }
        if (avatarUrl != null) {
            this.avatarUrl = avatarUrl;
        }
        this.githubAccessToken = githubAccessToken;
        this.githubConnected = true;
        this.githubConnectedAt = LocalDateTime.now();
        this.lastLoginAt = LocalDateTime.now();
    }

    public void completeEmailSignup(String email, String passwordHash, String displayName) {
        this.email = normalizeEmail(email);
        this.passwordHash = passwordHash;
        this.displayName = displayName;
    }

    public void disconnectGithub() {
        this.githubId = null;
        this.githubUsername = null;
        this.githubEmail = null;
        this.githubConnected = false;
        this.githubConnectedAt = null;
        this.githubAccessToken = null;
        this.githubTokenExpiresAt = null;
    }

    public void deactivateAccount(String anonymizedEmail, String anonymizedUsername) {
        this.email = normalizeEmail(anonymizedEmail);
        this.username = anonymizedUsername;
        this.passwordHash = null;
        this.emailVerified = false;
        this.emailVerifiedAt = null;
        this.displayName = "탈퇴한 사용자";
        this.nickname = null;
        this.developerType = null;
        this.bio = null;
        this.avatarUrl = null;
        disconnectGithub();
        this.isActive = false;
        this.deactivatedAt = LocalDateTime.now();
    }

    public void updateProfile(String displayName, String nickname, String developerType, String bio, String avatarUrl) {
        this.displayName = displayName;
        this.nickname = nickname;
        this.developerType = developerType;
        this.bio = bio;
        this.avatarUrl = avatarUrl;
    }
}
