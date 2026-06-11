package com.team1.codedock.domain.chat.service;

import com.team1.codedock.domain.channel.entity.Channel;
import com.team1.codedock.domain.chat.dto.ChannelMessageCreateRequest;
import com.team1.codedock.domain.chat.dto.ChannelMessageResponse;
import com.team1.codedock.domain.chat.dto.ChannelMessageRestCreateRequest;
import com.team1.codedock.domain.chat.dto.ChannelMessageUpdateRequest;
import com.team1.codedock.domain.chat.entity.Thread;
import com.team1.codedock.domain.chat.repository.ThreadRepository;
import com.team1.codedock.domain.workspace.entity.WorkspaceMember;
import com.team1.codedock.domain.workspace.repository.WorkspaceMemberRepository;
import com.team1.codedock.global.exception.BusinessException;
import com.team1.codedock.global.exception.ErrorCode;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ChatMessageService {

    private static final int DEFAULT_MESSAGE_LIMIT = 30;
    private static final int MAX_MESSAGE_LIMIT = 100;

    private final ThreadRepository threadRepository;
    private final WorkspaceMemberRepository workspaceMemberRepository;
    private final EntityManager entityManager;
    private final MentionService mentionService;

    @Transactional
    public ChannelMessageResponse createChannelMessage(Long channelId, ChannelMessageCreateRequest request) {
        validateContent(request.content());
        Channel channel = findChannel(channelId);
        WorkspaceMember sender = findWorkspaceMember(request.senderMemberId());
        validateSenderCanWriteChannelMessage(channel, sender);

        return saveChannelMessage(channel, sender, request.content());
    }

    @Transactional
    public ChannelMessageResponse createChannelMessage(Long channelId, Long userId, ChannelMessageRestCreateRequest request) {
        validateContent(request.content());
        Channel channel = findChannel(channelId);
        WorkspaceMember sender = findActiveWorkspaceMember(channel, userId);

        return saveChannelMessage(channel, sender, request.content());
    }

    @Transactional
    public ChannelMessageResponse updateChannelMessage(
            Long channelId,
            Long messageId,
            Long userId,
            ChannelMessageUpdateRequest request
    ) {
        validateContent(request.content());
        Channel channel = findChannel(channelId);
        WorkspaceMember member = findActiveWorkspaceMember(channel, userId);
        Thread message = findEditableChannelMessage(channel, messageId, member);

        message.updateContent(request.content());
        return ChannelMessageResponse.from(message);
    }

    @Transactional
    public ChannelMessageResponse deleteChannelMessage(Long channelId, Long messageId, Long userId) {
        Channel channel = findChannel(channelId);
        WorkspaceMember member = findActiveWorkspaceMember(channel, userId);
        Thread message = findEditableChannelMessage(channel, messageId, member);

        message.markAsDeleted();
        return ChannelMessageResponse.from(message);
    }

    private ChannelMessageResponse saveChannelMessage(Channel channel, WorkspaceMember sender, String content) {
        Thread thread =
                Thread.createChannelMessage(
                        channel,
                        sender,
                        content
                );

        Thread savedThread = threadRepository.save(thread);
        mentionService.createMentionsForThread(savedThread, sender, content);
        return ChannelMessageResponse.from(savedThread);
    }

    @Transactional(readOnly = true)
    public List<ChannelMessageResponse> getChannelMessages(Long channelId, Long userId, Long cursor, int limit) {
        int normalizedLimit = normalizeLimit(limit);
        Channel channel = findChannel(channelId);
        validateWorkspaceMember(channel, userId);

        List<Thread> messages = findPagedChannelMessages(
                        channelId,
                        cursor,
                        normalizedLimit
        );
        List<Thread> orderedMessages = new ArrayList<>(messages);
        Collections.reverse(orderedMessages);

        return orderedMessages.stream()
                .map(ChannelMessageResponse::from)
                .toList();
    }

    private void validateContent(String content) {
        if (content == null || content.isBlank()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "Message content must not be blank.");
        }
    }

    private int normalizeLimit(int limit) {
        if (limit <= 0) {
            return DEFAULT_MESSAGE_LIMIT;
        }
        return Math.min(limit, MAX_MESSAGE_LIMIT);
    }

    private List<Thread> findPagedChannelMessages(
            Long channelId,
            Long cursor,
            int limit
    ) {
        PageRequest pageRequest = PageRequest.of(0, limit);
        String threadType = Thread.TYPE_USER_MESSAGE;

        if (cursor == null) {
            return threadRepository.findAllByChannel_IdAndThreadTypeOrderByIdDesc(
                    channelId,
                    threadType,
                    pageRequest
            );
        }

        return threadRepository.findAllByChannel_IdAndThreadTypeAndIdLessThanOrderByIdDesc(
                channelId,
                threadType,
                cursor,
                pageRequest
        );
    }

    private void validateWorkspaceMember(Channel channel, Long userId) {
        findActiveWorkspaceMember(channel, userId);
    }

    private WorkspaceMember findActiveWorkspaceMember(Channel channel, Long userId) {
        if (userId == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }

        Long workspaceId = channel.getWorkspace().getId();
        return workspaceMemberRepository.findByWorkspace_IdAndUser_IdAndIsActiveTrue(workspaceId, userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.FORBIDDEN));
    }

    private void validateSenderCanWriteChannelMessage(Channel channel, WorkspaceMember sender) {
        if (!sender.isActive()) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }

        Long channelWorkspaceId = channel.getWorkspace().getId();
        Long senderWorkspaceId = sender.getWorkspace().getId();
        if (!channelWorkspaceId.equals(senderWorkspaceId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
    }

    private Thread findEditableChannelMessage(Channel channel, Long messageId, WorkspaceMember member) {
        Thread message = threadRepository.findById(messageId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Message not found."));

        validateMessageBelongsToChannel(channel, message);
        validateUserMessage(message);
        validateMessageAuthor(message, member);
        return message;
    }

    private void validateMessageBelongsToChannel(Channel channel, Thread message) {
        if (!channel.getId().equals(message.getChannel().getId())) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "Message does not belong to the requested channel.");
        }
    }

    private void validateUserMessage(Thread message) {
        if (!Thread.TYPE_USER_MESSAGE.equals(message.getThreadType())) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "Only user messages can be updated or deleted.");
        }
    }

    private void validateMessageAuthor(Thread message, WorkspaceMember member) {
        if (message.getCreatedBy() == null || !member.getId().equals(message.getCreatedBy().getId())) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
    }

    private Channel findChannel(Long channelId) {
        Channel channel = entityManager.find(Channel.class, channelId);
        if (channel == null) {
            throw new BusinessException(ErrorCode.CHANNEL_NOT_FOUND);
        }
        return channel;
    }

    private WorkspaceMember findWorkspaceMember(Long memberId) {
        WorkspaceMember member = entityManager.find(WorkspaceMember.class, memberId);
        if (member == null) {
            throw new BusinessException(ErrorCode.WORKSPACE_MEMBER_NOT_FOUND);
        }
        return member;
    }
}
