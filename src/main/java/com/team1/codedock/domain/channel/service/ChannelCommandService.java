package com.team1.codedock.domain.channel.service;

import com.team1.codedock.domain.channel.dto.ChannelCreateRequest;
import com.team1.codedock.domain.channel.dto.ChannelListResponse;
import com.team1.codedock.domain.channel.dto.ChannelOrderUpdateRequest;
import com.team1.codedock.domain.channel.dto.ChannelUpdateRequest;
import com.team1.codedock.domain.channel.entity.Channel;
import com.team1.codedock.domain.channel.repository.ChannelRepository;
import com.team1.codedock.domain.chat.repository.BookmarkRepository;
import com.team1.codedock.domain.chat.repository.ChannelReadStatusRepository;
import com.team1.codedock.domain.chat.repository.MentionRepository;
import com.team1.codedock.domain.chat.repository.ReactionRepository;
import com.team1.codedock.domain.chat.repository.ThreadAttachmentRepository;
import com.team1.codedock.domain.chat.repository.ThreadReplyRepository;
import com.team1.codedock.domain.chat.repository.ThreadRepository;
import com.team1.codedock.domain.issue.repository.GithubIssueRepository;
import com.team1.codedock.domain.pr.repository.GithubPullRequestRepository;
import com.team1.codedock.domain.workspace.entity.Workspace;
import com.team1.codedock.domain.workspace.repository.WorkspaceMemberRepository;
import com.team1.codedock.domain.workspace.repository.WorkspaceRepository;
import com.team1.codedock.global.exception.BusinessException;
import com.team1.codedock.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional
public class ChannelCommandService {

    private static final String AUTHORITY_OWNER = "owner";
    private static final String AUTHORITY_ADMIN = "admin";

    private final ChannelRepository channelRepository;
    private final WorkspaceRepository workspaceRepository;
    private final WorkspaceMemberRepository workspaceMemberRepository;
    private final ThreadRepository threadRepository;
    private final ThreadReplyRepository threadReplyRepository;
    private final ThreadAttachmentRepository threadAttachmentRepository;
    private final ReactionRepository reactionRepository;
    private final BookmarkRepository bookmarkRepository;
    private final MentionRepository mentionRepository;
    private final ChannelReadStatusRepository channelReadStatusRepository;
    private final GithubPullRequestRepository githubPullRequestRepository;
    private final GithubIssueRepository githubIssueRepository;

    public ChannelListResponse createChannel(Long workspaceId, Long userId, ChannelCreateRequest request) {
        validateChannelManager(workspaceId, userId);
        Workspace workspace = findWorkspace(workspaceId);
        String name = normalizeName(request.name());
        validateNewChannelName(workspaceId, name);

        Channel channel = Channel.createCustom(workspace, name, request.description(), nextDisplayOrder(workspaceId));
        return ChannelListResponse.from(channelRepository.save(channel));
    }

    public ChannelListResponse updateChannel(
            Long workspaceId,
            Long channelId,
            Long userId,
            ChannelUpdateRequest request
    ) {
        validateChannelManager(workspaceId, userId);
        Channel channel = findWorkspaceChannel(workspaceId, channelId);
        validateEditableChannel(channel);
        String name = normalizeName(request.name());
        validateUpdatedChannelName(workspaceId, channelId, name);

        channel.update(name, request.description());
        return ChannelListResponse.from(channel);
    }

    public void deleteChannel(Long workspaceId, Long channelId, Long userId) {
        validateChannelManager(workspaceId, userId);
        Channel channel = findWorkspaceChannel(workspaceId, channelId);
        validateDeletableChannel(channel);
        validateNoChannelReferences(channelId);
        deleteChannelChatData(channelId);
        channelRepository.delete(channel);
    }

    public List<ChannelListResponse> updateChannelOrder(
            Long workspaceId,
            Long userId,
            ChannelOrderUpdateRequest request
    ) {
        validateChannelManager(workspaceId, userId);
        List<Long> requestedChannelIds = validateRequestedOrder(request);
        List<Channel> channels = channelRepository.findAllByWorkspace_IdOrderByDisplayOrderAscIdAsc(workspaceId);
        validateOrderContainsAllWorkspaceChannels(channels, requestedChannelIds);

        Map<Long, Channel> channelsById = channels.stream()
                .collect(Collectors.toMap(Channel::getId, Function.identity()));

        for (int displayOrder = 0; displayOrder < requestedChannelIds.size(); displayOrder++) {
            channelsById.get(requestedChannelIds.get(displayOrder)).updateDisplayOrder(displayOrder);
        }

        return requestedChannelIds.stream()
                .map(channelsById::get)
                .map(ChannelListResponse::from)
                .toList();
    }

    private Workspace findWorkspace(Long workspaceId) {
        return workspaceRepository.findById(workspaceId)
                .orElseThrow(() -> new BusinessException(ErrorCode.WORKSPACE_NOT_FOUND));
    }

    private void validateChannelManager(Long workspaceId, Long userId) {
        if (userId == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }

        var member = workspaceMemberRepository.findByWorkspace_IdAndUser_IdAndIsActiveTrue(workspaceId, userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.FORBIDDEN));

        if (!canManageChannel(member.getAuthority())) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
    }

    private boolean canManageChannel(String authority) {
        String normalizedAuthority = authority == null ? "" : authority.trim().toLowerCase();
        return AUTHORITY_OWNER.equals(normalizedAuthority) || AUTHORITY_ADMIN.equals(normalizedAuthority);
    }

    private int nextDisplayOrder(Long workspaceId) {
        return channelRepository.findMaxDisplayOrderByWorkspaceId(workspaceId) + 1;
    }

    private Channel findWorkspaceChannel(Long workspaceId, Long channelId) {
        Channel channel = channelRepository.findById(channelId)
                .orElseThrow(() -> new BusinessException(ErrorCode.CHANNEL_NOT_FOUND));
        if (!channel.getWorkspace().getId().equals(workspaceId)) {
            throw new BusinessException(ErrorCode.CHANNEL_NOT_FOUND);
        }
        return channel;
    }

    private String normalizeName(String name) {
        return name.trim();
    }

    private List<Long> validateRequestedOrder(ChannelOrderUpdateRequest request) {
        if (request == null || request.channelIds() == null || request.channelIds().isEmpty()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "채널 순서 목록은 비어 있을 수 없습니다.");
        }

        if (request.channelIds().stream().anyMatch(id -> id == null || id <= 0)) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "채널 id는 양수여야 합니다.");
        }

        LinkedHashSet<Long> uniqueChannelIds = new LinkedHashSet<>(request.channelIds());
        if (uniqueChannelIds.size() != request.channelIds().size()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "채널 순서 목록에 중복된 채널이 있습니다.");
        }

        return request.channelIds();
    }

    private void validateOrderContainsAllWorkspaceChannels(List<Channel> channels, List<Long> requestedChannelIds) {
        if (channels.size() != requestedChannelIds.size()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "워크스페이스의 모든 채널을 순서 목록에 포함해야 합니다.");
        }

        var workspaceChannelIds = channels.stream()
                .map(Channel::getId)
                .collect(Collectors.toSet());
        if (!workspaceChannelIds.containsAll(requestedChannelIds)) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "다른 워크스페이스의 채널은 정렬할 수 없습니다.");
        }
    }

    private void validateNewChannelName(Long workspaceId, String name) {
        if (channelRepository.countByWorkspaceIdAndNameIgnoreCase(workspaceId, name) > 0) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "워크스페이스에 이미 같은 이름의 채널이 존재합니다.");
        }
    }

    private void validateUpdatedChannelName(Long workspaceId, Long channelId, String name) {
        if (channelRepository.countByWorkspaceIdAndNameIgnoreCaseAndIdNot(workspaceId, name, channelId) > 0) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "워크스페이스에 이미 같은 이름의 채널이 존재합니다.");
        }
    }

    private void validateEditableChannel(Channel channel) {
        if (!channel.isDeletable()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "수정할 수 없는 채널입니다.");
        }
    }

    private void validateDeletableChannel(Channel channel) {
        if (!channel.isDeletable()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "삭제할 수 없는 채널입니다.");
        }
    }

    private void validateNoChannelReferences(Long channelId) {
        if (githubPullRequestRepository.countByChannelId(channelId) > 0) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "Pull Request가 연결된 채널은 삭제할 수 없습니다.");
        }
        if (githubIssueRepository.countByChannelId(channelId) > 0) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "Issue가 연결된 채널은 삭제할 수 없습니다.");
        }
    }

    private void deleteChannelChatData(Long channelId) {
        channelReadStatusRepository.clearLastReadThreadByThreadChannelId(channelId);
        channelReadStatusRepository.deleteAllByChannelId(channelId);
        reactionRepository.deleteAllThreadReplyReactionsByChannelId(channelId);
        reactionRepository.deleteAllThreadReactionsByChannelId(channelId);
        mentionRepository.deleteAllByThreadReplyChannelId(channelId);
        mentionRepository.deleteAllByThreadChannelId(channelId);
        bookmarkRepository.deleteAllByThreadChannelId(channelId);
        threadAttachmentRepository.deleteAllByThreadChannelId(channelId);
        threadReplyRepository.clearPullRequestReviewReferencesByThreadChannelId(channelId);
        threadReplyRepository.clearPullRequestReviewCommentReferencesByThreadChannelId(channelId);
        threadReplyRepository.deleteAllByThreadChannelId(channelId);
        threadRepository.clearReplyToByChannelId(channelId);
        threadRepository.deleteAllByChannelId(channelId);
    }
}
