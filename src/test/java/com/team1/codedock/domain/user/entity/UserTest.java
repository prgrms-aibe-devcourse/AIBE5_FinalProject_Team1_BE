package com.team1.codedock.domain.user.entity;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class UserTest {

    @Test
    @DisplayName("create(): 이메일, 해시 비밀번호, 사용자명이 저장되고 isActive가 true다")
    void create() {
        User user = User.create("test@test.com", "hashed-password", "testuser");

        assertThat(user.getEmail()).isEqualTo("test@test.com");
        assertThat(user.getPasswordHash()).isEqualTo("hashed-password");
        assertThat(user.getUsername()).isEqualTo("test@test.com");
        assertThat(user.getDisplayName()).isEqualTo("testuser");
        assertThat(user.isActive()).isTrue();
    }

    @Test
    @DisplayName("updateProfile(): 모든 프로필 필드가 새 값으로 변경된다")
    void updateProfile_success() {
        User user = User.create("test@test.com", "hashed-password", "testuser");

        user.updateProfile("Jin", "jini", "Backend", "Hello world", "https://example.com/avatar.png");

        assertThat(user.getDisplayName()).isEqualTo("Jin");
        assertThat(user.getNickname()).isEqualTo("jini");
        assertThat(user.getDeveloperType()).isEqualTo("Backend");
        assertThat(user.getBio()).isEqualTo("Hello world");
        assertThat(user.getAvatarUrl()).isEqualTo("https://example.com/avatar.png");
    }

    @Test
    @DisplayName("updateProfile(): null 전달 시 해당 필드가 null로 변경된다 (PATCH 허용)")
    void updateProfile_withNullValues() {
        User user = User.create("test@test.com", "hashed-password", "testuser");
        user.updateProfile("Jin", "jini", "Backend", "Hello", "https://example.com/avatar.png");

        user.updateProfile(null, null, null, null, null);

        assertThat(user.getDisplayName()).isNull();
        assertThat(user.getNickname()).isNull();
        assertThat(user.getDeveloperType()).isNull();
        assertThat(user.getBio()).isNull();
        assertThat(user.getAvatarUrl()).isNull();
    }

    @Test
    @DisplayName("deactivateAccount(): 계정 식별값을 익명화하고 GitHub 연결과 프로필 정보를 정리한다")
    void deactivateAccount_success() {
        User user = User.createFromGithub(
                "github-1",
                "octocat",
                "octocat@example.com",
                "https://example.com/avatar.png",
                "github-token"
        );
        user.completeEmailSignup("member@example.com", "hashed-password", "멤버");
        user.updateProfile("멤버", "nick", "Backend", "bio", "https://example.com/profile.png");

        user.deactivateAccount("deleted-user-1@codedock.local", "deleted-user-1");

        assertThat(user.isActive()).isFalse();
        assertThat(user.getDeactivatedAt()).isNotNull();
        assertThat(user.getEmail()).isEqualTo("deleted-user-1@codedock.local");
        assertThat(user.getUsername()).isEqualTo("deleted-user-1");
        assertThat(user.getPasswordHash()).isNull();
        assertThat(user.getDisplayName()).isEqualTo("탈퇴한 사용자");
        assertThat(user.getNickname()).isNull();
        assertThat(user.getDeveloperType()).isNull();
        assertThat(user.getBio()).isNull();
        assertThat(user.getAvatarUrl()).isNull();
        assertThat(user.isGithubConnected()).isFalse();
        assertThat(user.getGithubId()).isNull();
        assertThat(user.getGithubUsername()).isNull();
        assertThat(user.getGithubEmail()).isNull();
        assertThat(user.getGithubAccessToken()).isNull();
    }
}
