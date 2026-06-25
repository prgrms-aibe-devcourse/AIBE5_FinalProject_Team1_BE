package com.team1.codedock.global.security;

import com.team1.codedock.domain.user.entity.User;
import com.team1.codedock.domain.user.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CustomUserDetailsServiceTest {

    @Mock
    private UserRepository userRepository;

    @Test
    @DisplayName("loadUserById - 활성 사용자는 CustomUserDetails로 변환한다")
    void loadUserById_activeUser() {
        User user = user(1L);
        CustomUserDetailsService service = new CustomUserDetailsService(userRepository);
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));

        CustomUserDetails result = service.loadUserById(1L);

        assertThat(result.getUserId()).isEqualTo(1L);
        assertThat(result.getEmail()).isEqualTo("user1@test.com");
    }

    @Test
    @DisplayName("loadUserById - 비활성 사용자는 인증 principal로 만들지 않는다")
    void loadUserById_inactiveUser() {
        User user = user(1L);
        user.deactivateAccount("deleted-user-1@codedock.local", "deleted-user-1");
        CustomUserDetailsService service = new CustomUserDetailsService(userRepository);
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> service.loadUserById(1L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("비활성화된 사용자");
    }

    @Test
    @DisplayName("loadUserById - 존재하지 않는 사용자는 예외를 던진다")
    void loadUserById_unknownUser() {
        CustomUserDetailsService service = new CustomUserDetailsService(userRepository);
        when(userRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.loadUserById(99L))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static User user(Long id) {
        User user = User.create("user" + id + "@test.com", "hashed-password", "사용자" + id);
        ReflectionTestUtils.setField(user, "id", id);
        return user;
    }
}
