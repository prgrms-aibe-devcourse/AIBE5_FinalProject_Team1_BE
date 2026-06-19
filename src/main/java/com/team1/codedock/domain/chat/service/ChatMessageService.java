package com.team1.codedock.domain.chat.service;

import com.team1.codedock.domain.channel.entity.Channel;
import com.team1.codedock.domain.chat.dto.ChannelMessageCreateRequest;
import com.team1.codedock.domain.chat.dto.ChannelMessageResponse;
import com.team1.codedock.domain.chat.dto.ChannelMessageRestCreateRequest;
import com.team1.codedock.domain.chat.dto.ChannelMessageUpdateRequest;
import com.team1.codedock.domain.chat.dto.ThreadAttachmentResponse;
import com.team1.codedock.domain.chat.dto.TypingEventRequest;
import com.team1.codedock.domain.chat.dto.TypingEventResponse;
import com.team1.codedock.domain.chat.entity.Thread;
import com.team1.codedock.domain.chat.repository.ThreadRepository;
import com.team1.codedock.domain.chat.util.ChatContentEmojiCodec;
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
import java.util.Map;

@Service
@RequiredArgsConstructor
public class ChatMessageService {

    private static final int DEFAULT_MESSAGE_LIMIT = 30;
    private static final int MAX_MESSAGE_LIMIT = 100;

    private final ThreadRepository threadRepository;
    private final WorkspaceMemberRepository workspaceMemberRepository;
    private final EntityManager entityManager;
    private final MentionService mentionService;
    private final ThreadAttachmentService threadAttachmentService;

    @Transactional
    public ChannelMessageResponse createChannelMessage(Long channelId, Long userId, ChannelMessageCreateRequest request) {
        validateContent(request.content());
        Channel channel = findChannel(channelId);
        // 클라이언트 senderMemberId를 믿지 않고 인증 userId로 채널 멤버 조회함
        WorkspaceMember sender = findActiveWorkspaceMember(channel, userId);
        Thread replyTo = resolveReplyTo(channel, request.replyToMessageId());

        return saveChannelMessage(channel, sender, request.content(), replyTo);
    }

    @Transactional(readOnly = true)
    public TypingEventResponse createTypingEventResponse(Long channelId, Long userId, TypingEventRequest request) {
        Channel channel = findChannel(channelId);
        // typing은 DB 저장 없이 현재 멤버 정보로 브로드캐스트 payload만 구성함
        WorkspaceMember sender = findActiveWorkspaceMember(channel, userId);

        return TypingEventResponse.of(channelId, sender, request);
    }

    @Transactional
    public ChannelMessageResponse createChannelMessage(Long channelId, Long userId, ChannelMessageRestCreateRequest request) {
        validateContent(request.content());
        Channel channel = findChannel(channelId);
        WorkspaceMember sender = findActiveWorkspaceMember(channel, userId);

        Thread replyTo = resolveReplyTo(channel, request.replyToMessageId());
        Thread savedThread = saveChannelMessageEntity(channel, sender, request.content(), replyTo);
        List<ThreadAttachmentResponse> attachments =
                threadAttachmentService.saveAttachments(savedThread, request.attachments());
        return ChannelMessageResponse.from(savedThread, attachments);
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

        message.updateContent(ChatContentEmojiCodec.encode(request.content()));
        return responseWithAttachments(message);
    }

    @Transactional
    public ChannelMessageResponse deleteChannelMessage(Long channelId, Long messageId, Long userId) {
        Channel channel = findChannel(channelId);
        WorkspaceMember member = findActiveWorkspaceMember(channel, userId);
        Thread message = findEditableChannelMessage(channel, messageId, member);

        message.markAsDeleted();
        return responseWithAttachments(message);
    }

    private ChannelMessageResponse saveChannelMessage(
            Channel channel,
            WorkspaceMember sender,
            String content,
            Thread replyTo
    ) {
        return ChannelMessageResponse.from(saveChannelMessageEntity(channel, sender, content, replyTo));
    }

    private Thread resolveReplyTo(Channel channel, Long replyToMessageId) {
        if (replyToMessageId == null) {
            return null;
        }
        Thread replyTo = threadRepository.findById(replyToMessageId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "답장 대상 메시지를 찾을 수 없습니다."));
        // 답장 대상은 같은 채널의 메시지여야 함
        if (!replyTo.getChannel().getId().equals(channel.getId())) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "다른 채널의 메시지에는 답장할 수 없습니다.");
        }
        return replyTo;
    }

    private ChannelMessageResponse responseWithAttachments(Thread message) {
        if (isDeletedMessage(message)) {
            return ChannelMessageResponse.from(message);
        }
        List<ThreadAttachmentResponse> attachments = threadAttachmentService.getAttachments(message.getId());
        return ChannelMessageResponse.from(message, attachments);
    }

    private Thread saveChannelMessageEntity(
            Channel channel,
            WorkspaceMember sender,
            String content,
            Thread replyTo
    ) {
        String encodedContent = ChatContentEmojiCodec.encode(content);
        Thread thread =
                Thread.createChannelMessage(
                        channel,
                        sender,
                        encodedContent,
                        replyTo
                );

        Thread savedThread = threadRepository.save(thread);
        mentionService.createMentionsForThread(savedThread, sender, content);
        return savedThread;
    }

    @Transactional(readOnly = true)
    public List<ChannelMessageResponse> getChannelMessages(Long channelId, Long userId, Long cursor, int limit) {
        int normalizedLimit = normalizeLimit(limit);
        Channel channel = findChannel(channelId);
        validateWorkspaceMember(channel, userId);

        List<Thread> messages = findPagedChannelMessages(
                        channel,
                        cursor,
                        normalizedLimit
        );
        List<Thread> orderedMessages = new ArrayList<>(messages);
        Collections.reverse(orderedMessages);

        Map<Long, List<ThreadAttachmentResponse>> attachmentMap = threadAttachmentService.getAttachmentMap(
                orderedMessages.stream()
                        .map(Thread::getId)
                        .toList()
        );
        Map<Long, List<ThreadAttachmentResponse>> attachmentsByThread =
                attachmentMap == null ? Map.of() : attachmentMap;

        return orderedMessages.stream()
                .map(message -> ChannelMessageResponse.from(
                        message,
                        isDeletedMessage(message)
                                ? List.of()
                                : attachmentsByThread.getOrDefault(message.getId(), List.of())
                ))
                .toList();
    }

    private boolean isDeletedMessage(Thread message) {
        return Thread.DELETED_MESSAGE_CONTENT.equals(message.getContent());
    }

    private void validateContent(String content) {
        if (content == null || content.isBlank()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "메시지 내용은 필수입니다.");
        }
    }

    private int normalizeLimit(int limit) {
        if (limit <= 0) {
            return DEFAULT_MESSAGE_LIMIT;
        }
        return Math.min(limit, MAX_MESSAGE_LIMIT);
    }

    private List<Thread> findPagedChannelMessages(
            Channel channel,
            Long cursor,
            int limit
    ) {
        PageRequest pageRequest = PageRequest.of(0, limit);
        Long channelId = channel.getId();

        if (Channel.TYPE_REPOSITORY.equals(channel.getChannelType())) {
            List<String> types = List.of(Thread.TYPE_USER_MESSAGE, Thread.TYPE_BOT_NOTIFICATION);
            if (cursor == null) {
                return threadRepository.findAllByChannel_IdAndThreadTypeInOrderByIdDesc(channelId, types, pageRequest);
            }
            return threadRepository.findAllByChannel_IdAndThreadTypeInAndIdLessThanOrderByIdDesc(channelId, types, cursor, pageRequest);
        }

        String threadType = Thread.TYPE_USER_MESSAGE;
        if (cursor == null) {
            return threadRepository.findAllByChannel_IdAndThreadTypeOrderByIdDesc(channelId, threadType, pageRequest);
        }
        return threadRepository.findAllByChannel_IdAndThreadTypeAndIdLessThanOrderByIdDesc(channelId, threadType, cursor, pageRequest);
    }

    private void validateWorkspaceMember(Channel channel, Long userId) {
        findActiveWorkspaceMember(channel, userId);
    }

    private WorkspaceMember findActiveWorkspaceMember(Channel channel, Long userId) {
        if (userId == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }

        // 채널이 속한 워크스페이스에서 활성 멤버인지 확인함
        Long workspaceId = channel.getWorkspace().getId();
        return workspaceMemberRepository.findByWorkspace_IdAndUser_IdAndIsActiveTrue(workspaceId, userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.FORBIDDEN));
    }

    private Thread findEditableChannelMessage(Channel channel, Long messageId, WorkspaceMember member) {
        Thread message = threadRepository.findById(messageId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "메시지를 찾을 수 없습니다."));

        validateMessageBelongsToChannel(channel, message);
        validateUserMessage(message);
        validateMessageAuthor(message, member);
        return message;
    }

    private void validateMessageBelongsToChannel(Channel channel, Thread message) {
        if (!channel.getId().equals(message.getChannel().getId())) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "요청한 채널에 속한 메시지가 아닙니다.");
        }
    }

    private void validateUserMessage(Thread message) {
        if (!Thread.TYPE_USER_MESSAGE.equals(message.getThreadType())) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "사용자 메시지만 수정하거나 삭제할 수 있습니다.");
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

}
