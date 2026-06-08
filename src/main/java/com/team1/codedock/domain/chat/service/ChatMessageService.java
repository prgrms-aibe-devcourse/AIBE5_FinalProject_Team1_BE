package com.team1.codedock.domain.chat.service;

import com.team1.codedock.domain.channel.entity.Channel;
import com.team1.codedock.domain.chat.dto.ChannelMessageCreateRequest;
import com.team1.codedock.domain.chat.dto.ChannelMessageResponse;
import com.team1.codedock.domain.chat.repository.ThreadRepository;
import com.team1.codedock.domain.workspace.entity.WorkspaceMember;
import com.team1.codedock.global.exception.BusinessException;
import com.team1.codedock.global.exception.ErrorCode;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ChatMessageService {

    private final ThreadRepository threadRepository;
    private final EntityManager entityManager;

    @Transactional
    public ChannelMessageResponse createChannelMessage(Long channelId, ChannelMessageCreateRequest request) {
        validateContent(request.content());
        Channel channel = findChannel(channelId);
        WorkspaceMember sender = findWorkspaceMember(request.senderMemberId());

        com.team1.codedock.domain.chat.entity.Thread thread =
                com.team1.codedock.domain.chat.entity.Thread.createChannelMessage(
                        channel,
                        sender,
                        request.content()
                );

        com.team1.codedock.domain.chat.entity.Thread savedThread = threadRepository.save(thread);
        return ChannelMessageResponse.from(savedThread);
    }

    private void validateContent(String content) {
        if (content == null || content.isBlank()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "Message content must not be blank.");
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
