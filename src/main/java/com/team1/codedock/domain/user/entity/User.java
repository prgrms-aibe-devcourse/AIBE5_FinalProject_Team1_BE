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

    // 이메일/비밀번호 회원가입
    public static User create(String email, String passwordHash, String displayName) {
        User user = new User();
        user.email = email;
        user.passwordHash = passwordHash;
        user.username = email;
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
        user.githubEmail = email;
        user.email = (email != null && !email.isBlank())
                ? email
                : githubUsername + "@users.noreply.github.com";
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
    }

    public void updateProfile(String displayName, String nickname, String developerType, String bio, String avatarUrl) {
        this.displayName = displayName;
        this.nickname = nickname;
        this.developerType = developerType;
        this.bio = bio;
        this.avatarUrl = avatarUrl;
    }
}
