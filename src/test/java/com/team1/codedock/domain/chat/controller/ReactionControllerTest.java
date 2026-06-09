package com.team1.codedock.domain.chat.controller;

import com.team1.codedock.domain.chat.dto.ChatEventResponse;
import com.team1.codedock.domain.chat.dto.ChatEventType;
import com.team1.codedock.domain.chat.dto.ReactionSummaryResponse;
import com.team1.codedock.domain.chat.dto.ReactionToggleRequest;
import com.team1.codedock.domain.chat.dto.ReactionToggleResponse;
import com.team1.codedock.domain.chat.entity.Reaction;
import com.team1.codedock.domain.chat.service.ReactionService;
import com.team1.codedock.global.response.ApiResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReactionControllerTest {

    @Mock
    private ReactionService reactionService;

    @Mock
    private SimpMessagingTemplate messagingTemplate;

    @InjectMocks
    private ReactionController reactionController;

    @Test
    @DisplayName("리액션 토글 후 REACTION_UPDATED 이벤트를 브로드캐스트한다")
    void toggleReaction() {
        Long channelId = 1L;
        ReactionToggleRequest request = new ReactionToggleRequest(
                10L,
                Reaction.TARGET_TYPE_THREAD,
                100L,
                "like"
        );
        ReactionToggleResponse response = ReactionToggleResponse.of(
                channelId,
                10L,
                Reaction.TARGET_TYPE_THREAD,
                100L,
                "like",
                true,
                1L
        );

        when(reactionService.toggleReaction(channelId, request)).thenReturn(response);

        ApiResponse<ReactionToggleResponse> apiResponse = reactionController.toggleReaction(channelId, request);

        assertThat(apiResponse.isSuccess()).isTrue();
        assertThat(apiResponse.getData()).isEqualTo(response);

        ArgumentCaptor<Object> payloadCaptor = ArgumentCaptor.forClass(Object.class);
        verify(messagingTemplate).convertAndSend(
                eq("/topic/channels/" + channelId + "/events"),
                payloadCaptor.capture()
        );

        assertThat(payloadCaptor.getValue()).isInstanceOf(ChatEventResponse.class);
        ChatEventResponse<?> event = (ChatEventResponse<?>) payloadCaptor.getValue();
        assertThat(event.type()).isEqualTo(ChatEventType.REACTION_UPDATED);
        assertThat(event.payload()).isEqualTo(response);
    }

    @Test
    @DisplayName("채널 리액션 집계 목록을 조회한다")
    void getReactionSummaries() {
        Long channelId = 1L;
        ReactionSummaryResponse summary = new ReactionSummaryResponse(
                Reaction.TARGET_TYPE_THREAD,
                100L,
                "like",
                3L
        );

        when(reactionService.getReactionSummaries(channelId)).thenReturn(List.of(summary));

        ApiResponse<List<ReactionSummaryResponse>> apiResponse =
                reactionController.getReactionSummaries(channelId);

        assertThat(apiResponse.isSuccess()).isTrue();
        assertThat(apiResponse.getData()).containsExactly(summary);
        verify(reactionService).getReactionSummaries(channelId);
    }
}
