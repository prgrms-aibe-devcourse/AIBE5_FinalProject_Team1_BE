package com.team1.codedock.domain.workspace.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PresenceRegistryTest {

    @Test
    @DisplayName("첫 세션 증가는 true(offline→online), 이후 증가는 false")
    void incrementTransition() {
        PresenceRegistry registry = new PresenceRegistry();

        assertThat(registry.increment(10L)).isTrue();
        assertThat(registry.increment(10L)).isFalse();
        assertThat(registry.isOnline(10L)).isTrue();
    }

    @Test
    @DisplayName("마지막 세션 감소는 true(online→offline), 그 전 감소는 false")
    void decrementTransition() {
        PresenceRegistry registry = new PresenceRegistry();
        registry.increment(10L);
        registry.increment(10L);

        assertThat(registry.decrement(10L)).isFalse();
        assertThat(registry.isOnline(10L)).isTrue();
        assertThat(registry.decrement(10L)).isTrue();
        assertThat(registry.isOnline(10L)).isFalse();
    }

    @Test
    @DisplayName("onlineUserIds는 세션이 있는 사용자만 포함한다")
    void onlineUserIds() {
        PresenceRegistry registry = new PresenceRegistry();
        registry.increment(10L);
        registry.increment(20L);
        registry.decrement(20L);

        assertThat(registry.onlineUserIds()).containsExactly(10L);
    }

    @Test
    @DisplayName("null userId는 무시한다")
    void nullSafe() {
        PresenceRegistry registry = new PresenceRegistry();

        assertThat(registry.increment(null)).isFalse();
        assertThat(registry.decrement(null)).isFalse();
        assertThat(registry.isOnline(null)).isFalse();
    }
}
