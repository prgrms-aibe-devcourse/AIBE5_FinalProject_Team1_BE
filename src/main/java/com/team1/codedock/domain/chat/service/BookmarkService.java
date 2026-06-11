package com.team1.codedock.domain.chat.service;

import com.team1.codedock.domain.channel.entity.Channel;
import com.team1.codedock.domain.channel.repository.ChannelRepository;
import com.team1.codedock.domain.chat.dto.BookmarkResponse;
import com.team1.codedock.domain.chat.dto.BookmarkToggleResponse;
import com.team1.codedock.domain.chat.entity.Bookmark;
import com.team1.codedock.domain.chat.entity.Thread;
import com.team1.codedock.domain.chat.repository.BookmarkRepository;
import com.team1.codedock.domain.chat.repository.ThreadRepository;
import com.team1.codedock.domain.workspace.entity.WorkspaceMember;
import com.team1.codedock.domain.workspace.repository.WorkspaceMemberRepository;
import com.team1.codedock.global.exception.BusinessException;
import com.team1.codedock.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class BookmarkService {

    private final ChannelRepository channelRepository;
    private final ThreadRepository threadRepository;
    private final WorkspaceMemberRepository workspaceMemberRepository;
    private final BookmarkRepository bookmarkRepository;

    // 메시지 북마크 버튼을 누를 때 호출됨. 이미 있으면 삭제하고, 없으면 생성
    @Transactional
    public BookmarkToggleResponse toggleMessageBookmark(Long channelId, Long messageId, Long userId) {
        Channel channel = findChannel(channelId);
        WorkspaceMember member = findActiveWorkspaceMember(channel.getWorkspace().getId(), userId);
        Thread message = findBookmarkableMessage(channel, messageId);

        boolean bookmarked = bookmarkRepository
                .findByWorkspaceMember_IdAndThread_Id(member.getId(), message.getId())
                .map(bookmark -> {
                    bookmarkRepository.delete(bookmark);
                    return false;
                })
                .orElseGet(() -> {
                    bookmarkRepository.save(Bookmark.create(member, message));
                    return true;
                });

        return BookmarkToggleResponse.of(channelId, messageId, member.getId(), bookmarked);
    }

    // 워크스페이스 사이드바나 모아보기 화면에서 내가 저장한 메시지를 조회함.
    @Transactional(readOnly = true)
    public List<BookmarkResponse> getMyBookmarks(Long workspaceId, Long userId) {
        WorkspaceMember member = findActiveWorkspaceMember(workspaceId, userId);

        return bookmarkRepository.findAllByWorkspaceMember_IdOrderByCreatedAtDesc(member.getId()).stream()
                .map(BookmarkResponse::from)
                .toList();
    }

    private Channel findChannel(Long channelId) {
        return channelRepository.findById(channelId)
                .orElseThrow(() -> new BusinessException(ErrorCode.CHANNEL_NOT_FOUND));
    }

    // JWT 전환 전까지는 X-User-Id로 워크스페이스 멤버 여부를 확인하고 추후에 jwt 완료 후 jwt 인증 붙일 예정
    private WorkspaceMember findActiveWorkspaceMember(Long workspaceId, Long userId) {
        if (userId == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }

        return workspaceMemberRepository.findByWorkspace_IdAndUser_IdAndIsActiveTrue(workspaceId, userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.FORBIDDEN));
    }

    private Thread findBookmarkableMessage(Channel channel, Long messageId) {
        Thread message = threadRepository.findById(messageId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "메시지를 찾을 수 없습니다."));

        validateMessageBelongsToChannel(channel, message);
        validateUserMessage(message);
        return message;
    }

    private void validateMessageBelongsToChannel(Channel channel, Thread message) {
        if (!channel.getId().equals(message.getChannel().getId())) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "북마크 대상 메시지가 요청한 채널에 속하지 않습니다.");
        }
    }

    // 현재 bookmarks 스키마는 일반 채널 메시지(thread) 북마크만 지원하게 로직 구현
    private void validateUserMessage(Thread message) {
        if (!Thread.TYPE_USER_MESSAGE.equals(message.getThreadType())) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "사용자 메시지만 북마크할 수 있습니다.");
        }
    }
}
