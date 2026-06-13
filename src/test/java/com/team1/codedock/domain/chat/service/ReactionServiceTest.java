package com.team1.codedock.domain.chat.service;

import com.team1.codedock.domain.channel.entity.Channel;
import com.team1.codedock.domain.chat.dto.ReactionSummaryResponse;
import com.team1.codedock.domain.chat.dto.ReactionToggleRequest;
import com.team1.codedock.domain.chat.dto.ReactionToggleResponse;
import com.team1.codedock.domain.chat.entity.Reaction;
import com.team1.codedock.domain.chat.entity.Thread;
import com.team1.codedock.domain.chat.entity.ThreadReply;
import com.team1.codedock.domain.chat.repository.ReactionRepository;
import com.team1.codedock.domain.chat.repository.ThreadRepository;
import com.team1.codedock.domain.workspace.entity.Workspace;
import com.team1.codedock.domain.workspace.entity.WorkspaceMember;
import com.team1.codedock.domain.workspace.repository.WorkspaceMemberRepository;
import com.team1.codedock.global.exception.BusinessException;
import com.team1.codedock.global.exception.ErrorCode;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReactionServiceTest {

    @Mock
    private ReactionRepository reactionRepository;

    @Mock
    private ThreadRepository threadRepository;

    @Mock
    private WorkspaceMemberRepository workspaceMemberRepository;

    @Mock
    private EntityManager entityManager;

    @InjectMocks
    private ReactionService reactionService;

    @Test
    @DisplayName("리액션이 없으면 새 리액션을 생성하고 reacted true를 반환한다")
    void toggleReaction_createReaction() {
        Long channelId = 1L;
        Long workspaceId = 5L;
        Long userId = 30L;
        Long workspaceMemberId = 10L;
        Long targetId = 100L;
        String emoji = "like";
        ReactionToggleRequest request = new ReactionToggleRequest(
                Reaction.TARGET_TYPE_THREAD,
                targetId,
                emoji
        );
        WorkspaceMember workspaceMember = mock(WorkspaceMember.class);
        stubThreadTarget(channelId, targetId, workspaceId);

        when(workspaceMemberRepository.findByWorkspace_IdAndUser_IdAndIsActiveTrue(workspaceId, userId))
                .thenReturn(Optional.of(workspaceMember));
        when(workspaceMember.getId()).thenReturn(workspaceMemberId);
        when(reactionRepository.findByWorkspaceMember_IdAndTargetTypeAndTargetIdAndEmoji(
                workspaceMemberId,
                Reaction.TARGET_TYPE_THREAD,
                targetId,
                emoji
        )).thenReturn(Optional.empty());
        when(reactionRepository.countByTargetTypeAndTargetIdAndEmoji(
                Reaction.TARGET_TYPE_THREAD,
                targetId,
                emoji
        )).thenReturn(1L);

        ReactionToggleResponse response = reactionService.toggleReaction(channelId, userId, request);

        assertThat(response.channelId()).isEqualTo(channelId);
        assertThat(response.workspaceMemberId()).isEqualTo(workspaceMemberId);
        assertThat(response.targetType()).isEqualTo(Reaction.TARGET_TYPE_THREAD);
        assertThat(response.targetId()).isEqualTo(targetId);
        assertThat(response.emoji()).isEqualTo(emoji);
        assertThat(response.reacted()).isTrue();
        assertThat(response.count()).isEqualTo(1L);

        ArgumentCaptor<Reaction> reactionCaptor = ArgumentCaptor.forClass(Reaction.class);
        verify(reactionRepository).save(reactionCaptor.capture());
        Reaction savedReaction = reactionCaptor.getValue();
        assertThat(savedReaction.getWorkspaceMember()).isEqualTo(workspaceMember);
        assertThat(savedReaction.getTargetType()).isEqualTo(Reaction.TARGET_TYPE_THREAD);
        assertThat(savedReaction.getTargetId()).isEqualTo(targetId);
        assertThat(savedReaction.getEmoji()).isEqualTo(emoji);
        verify(reactionRepository, never()).delete(any(Reaction.class));
    }

    @Test
    @DisplayName("이미 리액션이 있으면 삭제하고 reacted false를 반환한다")
    void toggleReaction_deleteReaction() {
        Long channelId = 1L;
        Long workspaceId = 5L;
        Long userId = 30L;
        Long workspaceMemberId = 10L;
        Long targetId = 100L;
        String emoji = "like";
        ReactionToggleRequest request = new ReactionToggleRequest(
                Reaction.TARGET_TYPE_THREAD,
                targetId,
                emoji
        );
        WorkspaceMember workspaceMember = mock(WorkspaceMember.class);
        Reaction existingReaction = mock(Reaction.class);
        stubThreadTarget(channelId, targetId, workspaceId);

        when(workspaceMemberRepository.findByWorkspace_IdAndUser_IdAndIsActiveTrue(workspaceId, userId))
                .thenReturn(Optional.of(workspaceMember));
        when(workspaceMember.getId()).thenReturn(workspaceMemberId);
        when(reactionRepository.findByWorkspaceMember_IdAndTargetTypeAndTargetIdAndEmoji(
                workspaceMemberId,
                Reaction.TARGET_TYPE_THREAD,
                targetId,
                emoji
        )).thenReturn(Optional.of(existingReaction));
        when(reactionRepository.countByTargetTypeAndTargetIdAndEmoji(
                Reaction.TARGET_TYPE_THREAD,
                targetId,
                emoji
        )).thenReturn(0L);

        ReactionToggleResponse response = reactionService.toggleReaction(channelId, userId, request);

        assertThat(response.reacted()).isFalse();
        assertThat(response.count()).isZero();
        verify(reactionRepository).delete(existingReaction);
        verify(reactionRepository, never()).save(any(Reaction.class));
    }

    @Test
    @DisplayName("thread_reply 대상이면 부모 thread의 채널을 기준으로 검증한다")
    void toggleReaction_threadReplyTarget() {
        Long channelId = 1L;
        Long workspaceId = 5L;
        Long userId = 30L;
        Long workspaceMemberId = 10L;
        Long targetId = 200L;
        String emoji = "like";
        ReactionToggleRequest request = new ReactionToggleRequest(
                Reaction.TARGET_TYPE_THREAD_REPLY,
                targetId,
                emoji
        );
        WorkspaceMember workspaceMember = mock(WorkspaceMember.class);
        stubThreadReplyTarget(channelId, targetId, workspaceId);

        when(workspaceMemberRepository.findByWorkspace_IdAndUser_IdAndIsActiveTrue(workspaceId, userId))
                .thenReturn(Optional.of(workspaceMember));
        when(workspaceMember.getId()).thenReturn(workspaceMemberId);
        when(reactionRepository.findByWorkspaceMember_IdAndTargetTypeAndTargetIdAndEmoji(
                workspaceMemberId,
                Reaction.TARGET_TYPE_THREAD_REPLY,
                targetId,
                emoji
        )).thenReturn(Optional.empty());
        when(reactionRepository.countByTargetTypeAndTargetIdAndEmoji(
                Reaction.TARGET_TYPE_THREAD_REPLY,
                targetId,
                emoji
        )).thenReturn(1L);

        ReactionToggleResponse response = reactionService.toggleReaction(channelId, userId, request);

        assertThat(response.targetType()).isEqualTo(Reaction.TARGET_TYPE_THREAD_REPLY);
        assertThat(response.reacted()).isTrue();
        assertThat(response.count()).isEqualTo(1L);
        verify(reactionRepository).save(any(Reaction.class));
    }

    @Test
    @DisplayName("허용되지 않은 targetType이면 INVALID_INPUT 예외가 발생한다")
    void toggleReaction_invalidTargetType() {
        ReactionToggleRequest request = new ReactionToggleRequest("invalid", 100L, "like");

        assertThatThrownBy(() -> reactionService.toggleReaction(1L, 30L, request))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVALID_INPUT);

        verifyNoInteractions(threadRepository, workspaceMemberRepository, reactionRepository, entityManager);
    }

    @Test
    @DisplayName("대상이 요청 채널에 속하지 않으면 INVALID_INPUT 예외가 발생한다")
    void toggleReaction_targetChannelMismatch() {
        Long requestChannelId = 1L;
        Long actualChannelId = 2L;
        Long targetId = 100L;
        ReactionToggleRequest request = new ReactionToggleRequest(
                Reaction.TARGET_TYPE_THREAD,
                targetId,
                "like"
        );
        stubThreadTarget(actualChannelId, targetId);

        assertThatThrownBy(() -> reactionService.toggleReaction(requestChannelId, 30L, request))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVALID_INPUT);

        verifyNoInteractions(workspaceMemberRepository, reactionRepository, entityManager);
    }

    @Test
    @DisplayName("인증 사용자가 없으면 UNAUTHORIZED 예외가 발생한다")
    void toggleReaction_unauthorizedWhenUserIdIsNull() {
        Long channelId = 1L;
        Long targetId = 100L;
        ReactionToggleRequest request = new ReactionToggleRequest(
                Reaction.TARGET_TYPE_THREAD,
                targetId,
                "like"
        );
        stubThreadTarget(channelId, targetId);

        assertThatThrownBy(() -> reactionService.toggleReaction(channelId, null, request))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.UNAUTHORIZED);

        verifyNoInteractions(workspaceMemberRepository, reactionRepository, entityManager);
    }

    @Test
    @DisplayName("인증 사용자가 대상 채널의 워크스페이스 멤버가 아니면 FORBIDDEN 예외가 발생한다")
    void toggleReaction_forbiddenWhenUserIsNotWorkspaceMember() {
        Long channelId = 1L;
        Long workspaceId = 5L;
        Long userId = 30L;
        Long targetId = 100L;
        ReactionToggleRequest request = new ReactionToggleRequest(
                Reaction.TARGET_TYPE_THREAD,
                targetId,
                "like"
        );
        stubThreadTarget(channelId, targetId, workspaceId);

        when(workspaceMemberRepository.findByWorkspace_IdAndUser_IdAndIsActiveTrue(workspaceId, userId))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> reactionService.toggleReaction(channelId, userId, request))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.FORBIDDEN);

        verifyNoInteractions(reactionRepository);
    }

    @Test
    @DisplayName("채널 기준으로 메시지와 답글 리액션 집계를 함께 조회한다")
    void getReactionSummaries() {
        Long channelId = 1L;
        ReactionSummaryResponse threadSummary = new ReactionSummaryResponse(
                Reaction.TARGET_TYPE_THREAD,
                100L,
                "like",
                3L
        );
        ReactionSummaryResponse replySummary = new ReactionSummaryResponse(
                Reaction.TARGET_TYPE_THREAD_REPLY,
                200L,
                "smile",
                2L
        );

        when(reactionRepository.findThreadReactionSummariesByChannelId(channelId))
                .thenReturn(List.of(threadSummary));
        when(reactionRepository.findThreadReplyReactionSummariesByChannelId(channelId))
                .thenReturn(List.of(replySummary));

        List<ReactionSummaryResponse> summaries = reactionService.getReactionSummaries(channelId);

        assertThat(summaries).containsExactly(threadSummary, replySummary);
    }

    private void stubThreadTarget(Long channelId, Long targetId) {
        Thread thread = mock(Thread.class);
        Channel channel = mock(Channel.class);

        when(threadRepository.findById(targetId)).thenReturn(Optional.of(thread));
        when(thread.getChannel()).thenReturn(channel);
        when(channel.getId()).thenReturn(channelId);
    }

    private void stubThreadTarget(Long channelId, Long targetId, Long workspaceId) {
        Thread thread = mock(Thread.class);
        Channel channel = mockChannel(channelId, workspaceId);

        when(threadRepository.findById(targetId)).thenReturn(Optional.of(thread));
        when(thread.getChannel()).thenReturn(channel);
    }

    private void stubThreadReplyTarget(Long channelId, Long targetId, Long workspaceId) {
        ThreadReply threadReply = mock(ThreadReply.class);
        Thread thread = mock(Thread.class);
        Channel channel = mockChannel(channelId, workspaceId);

        when(entityManager.find(ThreadReply.class, targetId)).thenReturn(threadReply);
        when(threadReply.getThread()).thenReturn(thread);
        when(thread.getChannel()).thenReturn(channel);
    }

    private Channel mockChannel(Long channelId, Long workspaceId) {
        Channel channel = mock(Channel.class);
        Workspace workspace = mock(Workspace.class);

        when(channel.getId()).thenReturn(channelId);
        when(channel.getWorkspace()).thenReturn(workspace);
        when(workspace.getId()).thenReturn(workspaceId);
        return channel;
    }
}
