package com.team1.codedock.domain.user.service;

import com.team1.codedock.domain.user.dto.UpdateProfileRequest;
import com.team1.codedock.domain.user.dto.UpdateSkillsRequest;
import com.team1.codedock.domain.user.dto.UserResponse;
import com.team1.codedock.domain.user.entity.User;
import com.team1.codedock.domain.user.entity.UserSkill;
import com.team1.codedock.domain.user.repository.UserRepository;
import com.team1.codedock.domain.user.repository.UserSkillRepository;
import com.team1.codedock.global.exception.BusinessException;
import com.team1.codedock.global.exception.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock private UserRepository userRepository;
    @Mock private UserSkillRepository userSkillRepository;

    @InjectMocks
    private UserService userService;

    // ── updateProfile ─────────────────────────────────────────────────────────

    @Test
    @DisplayName("프로필 업데이트: 변경된 필드가 UserResponse에 반영된다")
    void updateProfile_success() {
        User user = user(1L, "test@test.com", "testuser");
        UpdateProfileRequest req = new UpdateProfileRequest();
        req.setDisplayName("Jin");
        req.setNickname("jini");
        req.setDeveloperType("Backend");
        req.setBio("Hello");
        req.setAvatarUrl("https://example.com/avatar.png");

        when(userRepository.findById(1L)).thenReturn(Optional.of(user));

        UserResponse response = userService.updateProfile(1L, req);

        assertThat(response.getDisplayName()).isEqualTo("Jin");
        assertThat(response.getNickname()).isEqualTo("jini");
        assertThat(response.getDeveloperType()).isEqualTo("Backend");
        assertThat(response.getBio()).isEqualTo("Hello");
        assertThat(response.getAvatarUrl()).isEqualTo("https://example.com/avatar.png");

        // Entity assertions (confirm the domain object was actually mutated)
        assertThat(user.getDisplayName()).isEqualTo("Jin");
        assertThat(user.getNickname()).isEqualTo("jini");
    }

    @Test
    @DisplayName("존재하지 않는 유저 프로필 업데이트: USER_NOT_FOUND 예외가 발생한다")
    void updateProfile_userNotFound() {
        when(userRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.updateProfile(99L, new UpdateProfileRequest()))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.USER_NOT_FOUND);
    }

    // ── updateSkills ──────────────────────────────────────────────────────────

    @Test
    @DisplayName("스킬 업데이트: 기존 스킬을 모두 삭제하고 새 스킬을 저장한다")
    void updateSkills_success() {
        User user = user(1L, "test@test.com", "testuser");
        UpdateSkillsRequest req = new UpdateSkillsRequest();
        req.setSkills(List.of("Java", "Spring", "React"));

        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(userSkillRepository.saveAll(anyList())).thenAnswer(i -> i.getArguments()[0]);

        List<String> result = userService.updateSkills(1L, req);

        assertThat(result).containsExactlyInAnyOrder("Java", "Spring", "React");
        verify(userSkillRepository).deleteAllByUser(user);
    }

    @Test
    @DisplayName("중복 스킬 입력: 중복이 제거된 목록이 저장된다")
    void updateSkills_deduplication() {
        User user = user(1L, "test@test.com", "testuser");
        UpdateSkillsRequest req = new UpdateSkillsRequest();
        req.setSkills(List.of("Java", "Java", "Spring"));

        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(userSkillRepository.saveAll(anyList())).thenAnswer(i -> i.getArguments()[0]);

        List<String> result = userService.updateSkills(1L, req);

        assertThat(result).containsExactlyInAnyOrder("Java", "Spring");
        assertThat(result).hasSize(2);

        ArgumentCaptor<List<UserSkill>> captor = ArgumentCaptor.forClass(List.class);
        verify(userSkillRepository).saveAll(captor.capture());
        assertThat(captor.getValue()).hasSize(2);
        verify(userSkillRepository).deleteAllByUser(user);
    }

    // ── getSkills ─────────────────────────────────────────────────────────────

    @Test
    @DisplayName("스킬 조회: 저장된 스킬 이름 목록을 반환한다")
    void getSkills_success() {
        User user = user(1L, "test@test.com", "testuser");
        List<UserSkill> skills = List.of(
                UserSkill.create(user, "Java"),
                UserSkill.create(user, "Spring")
        );

        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(userSkillRepository.findAllByUser(user)).thenReturn(skills);

        List<String> result = userService.getSkills(1L);

        assertThat(result).containsExactlyInAnyOrder("Java", "Spring");
    }

    @Test
    @DisplayName("스킬이 없는 유저 조회: 빈 목록을 반환한다")
    void getSkills_empty() {
        User user = user(1L, "test@test.com", "testuser");

        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(userSkillRepository.findAllByUser(user)).thenReturn(List.of());

        List<String> result = userService.getSkills(1L);

        assertThat(result).isEmpty();
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private static User user(Long id, String email, String username) {
        User user = User.create(email, "hashed-password", username);
        ReflectionTestUtils.setField(user, "id", id);
        return user;
    }
}