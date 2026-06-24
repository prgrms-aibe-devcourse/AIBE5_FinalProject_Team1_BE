package com.team1.codedock.domain.chat.repository;

import com.team1.codedock.domain.chat.entity.Thread;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.withSettings;

// findFirstThreadByThreadableTypeAndThreadableId는 (threadableType, threadableId)로 스레드가
// 중복 생성돼도 NonUniqueResultException 없이 동작하도록 추가한 default 메서드다.
// 정렬된 목록에서 가장 오래된(첫 번째) 스레드를 반환하는지, 비었을 때 empty인지 검증한다.
class ThreadRepositoryDefaultMethodTest {

    private final ThreadRepository repository =
            mock(ThreadRepository.class, withSettings().defaultAnswer(CALLS_REAL_METHODS));

    @Test
    @DisplayName("중복 스레드가 있어도 id 오름차순 첫 번째 스레드를 반환한다")
    void findFirst_중복있으면_첫번째_반환() {
        Thread first = mock(Thread.class);
        Thread second = mock(Thread.class);
        doReturn(List.of(first, second))
                .when(repository)
                .findAllByThreadableTypeAndThreadableIdOrderByIdAsc(Thread.THREADABLE_TYPE_GITHUB_ISSUE, 40L);

        Optional<Thread> result =
                repository.findFirstThreadByThreadableTypeAndThreadableId(Thread.THREADABLE_TYPE_GITHUB_ISSUE, 40L);

        assertThat(result).containsSame(first);
    }

    @Test
    @DisplayName("스레드가 없으면 empty를 반환한다")
    void findFirst_없으면_empty() {
        doReturn(List.of())
                .when(repository)
                .findAllByThreadableTypeAndThreadableIdOrderByIdAsc(Thread.THREADABLE_TYPE_GITHUB_PR, 99L);

        Optional<Thread> result =
                repository.findFirstThreadByThreadableTypeAndThreadableId(Thread.THREADABLE_TYPE_GITHUB_PR, 99L);

        assertThat(result).isEmpty();
    }
}
