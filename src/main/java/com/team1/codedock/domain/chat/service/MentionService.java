package com.team1.codedock.domain.chat.service;

import com.team1.codedock.domain.chat.dto.ChatNotificationResponse;
import com.team1.codedock.domain.chat.dto.MentionResponse;
import com.team1.codedock.domain.chat.entity.Mention;
import com.team1.codedock.domain.chat.entity.Thread;
import com.team1.codedock.domain.chat.entity.ThreadReply;
import com.team1.codedock.domain.chat.repository.MentionRepository;
import com.team1.codedock.domain.user.entity.User;
import com.team1.codedock.domain.workspace.entity.Workspace;
import com.team1.codedock.domain.workspace.entity.WorkspaceEvent;
import com.team1.codedock.domain.workspace.entity.WorkspaceMember;
import com.team1.codedock.domain.workspace.repository.WorkspaceMemberRepository;
import com.team1.codedock.domain.workspace.service.WorkspaceEventService;
import com.team1.codedock.global.exception.BusinessException;
import com.team1.codedock.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class MentionService {

    private static final Pattern MENTION_PATTERN = Pattern.compile("@([\\p{L}\\p{N}._-]{1,100})");
    private static final String THREAD_MENTION_MESSAGE = "새 멘션이 도착했습니다.";
    private static final String THREAD_REPLY_MENTION_MESSAGE = "새 멘션 답글이 도착했습니다.";

    private final MentionRepository mentionRepository;
    private final WorkspaceMemberRepository workspaceMemberRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final WorkspaceEventService workspaceEventService;

    @Transactional
    public void createMentionsForThread(Thread thread, WorkspaceMember mentionedByMember, String content) {
        Workspace workspace = thread.getChannel().getWorkspace();
        List<WorkspaceMember> mentionedMembers = findMentionedMembers(workspace.getId(), content);
        if (mentionedMembers.isEmpty()) {
            return;
        }

        List<Mention> mentions = mentionedMembers.stream()
                .map(mentionedMember -> Mention.createForThread(
                        workspace,
                        thread,
                        mentionedMember,
                        mentionedByMember
                ))
                .toList();
        mentionRepository.saveAll(mentions);
        for (WorkspaceMember mentionedMember : mentionedMembers) {
            workspaceEventService.recordEvent(workspace.getId(), WorkspaceEvent.EventType.MENTION,
                    mentionedByMember.getUser().getDisplayName(), null, null, thread.getChannel().getId(), content,
                    null, null, thread.getId(), null, null, mentionedMember.getUser().getId());
        }
        publishThreadMentionNotifications(workspace, thread, mentionedMembers);
    }

    @Transactional
    public void createMentionsForThreadReply(ThreadReply threadReply, WorkspaceMember mentionedByMember, String content) {
        Workspace workspace = threadReply.getThread().getChannel().getWorkspace();
        List<WorkspaceMember> mentionedMembers = findMentionedMembers(workspace.getId(), content);
        if (mentionedMembers.isEmpty()) {
            return;
        }

        List<Mention> mentions = mentionedMembers.stream()
                .map(mentionedMember -> Mention.createForThreadReply(
                        workspace,
                        threadReply,
                        mentionedMember,
                        mentionedByMember
                ))
                .toList();
        mentionRepository.saveAll(mentions);
        for (WorkspaceMember mentionedMember : mentionedMembers) {
            workspaceEventService.recordEvent(workspace.getId(), WorkspaceEvent.EventType.MENTION,
                    mentionedByMember.getUser().getDisplayName(), null, null,
                    threadReply.getThread().getChannel().getId(), content,
                    null, null, threadReply.getThread().getId(), null, null, mentionedMember.getUser().getId());
        }
        publishThreadReplyMentionNotifications(workspace, threadReply, mentionedMembers);
    }

    private void publishThreadMentionNotifications(
            Workspace workspace,
            Thread thread,
            List<WorkspaceMember> mentionedMembers
    ) {
        mentionedMembers.forEach(mentionedMember -> publishMentionNotification(
                mentionedMember,
                ChatNotificationResponse.of(
                        workspace.getId(),
                        thread.getChannel().getId(),
                        thread.getId(),
                        null,
                        mentionedMember.getId(),
                        THREAD_MENTION_MESSAGE
                )
        ));
    }

    private void publishThreadReplyMentionNotifications(
            Workspace workspace,
            ThreadReply threadReply,
            List<WorkspaceMember> mentionedMembers
    ) {
        Thread thread = threadReply.getThread();

        mentionedMembers.forEach(mentionedMember -> publishMentionNotification(
                mentionedMember,
                ChatNotificationResponse.of(
                        workspace.getId(),
                        thread.getChannel().getId(),
                        thread.getId(),
                        threadReply.getId(),
                        mentionedMember.getId(),
                        THREAD_REPLY_MENTION_MESSAGE
                )
        ));
    }

    private void publishMentionNotification(
            WorkspaceMember mentionedMember,
            ChatNotificationResponse notification
    ) {
        User user = mentionedMember.getUser();

        // 트랜잭션이 커밋된 뒤 실제 WebSocket 알림을 보내도록 이벤트만 발행함
        eventPublisher.publishEvent(new MentionNotificationEvent(user.getEmail(), notification));
    }

    @Transactional(readOnly = true)
    public List<MentionResponse> getMyMentions(Long workspaceId, Long userId) {
        WorkspaceMember member = findActiveWorkspaceMember(workspaceId, userId);

        return mentionRepository
                .findAllByWorkspace_IdAndMentionedMember_IdOrderByCreatedAtDesc(workspaceId, member.getId())
                .stream()
                .map(MentionResponse::from)
                .toList();
    }

    @Transactional
    public MentionResponse markMentionAsRead(Long mentionId, Long userId) {
        Mention myMention = findMyMention(mentionId, userId);
        myMention.markAsRead();
        return MentionResponse.from(myMention);
    }

    @Transactional
    public MentionResponse deleteMention(Long mentionId, Long userId) {
        Mention myMention = findMyMention(mentionId, userId);
        MentionResponse response = MentionResponse.from(myMention);

        // 멘션 목록에서 본인이 받은 멘션만 제거함
        mentionRepository.delete(myMention);
        return response;
    }

    private List<WorkspaceMember> findMentionedMembers(Long workspaceId, String content) {
        List<String> mentionNames = parseMentionNames(content);
        if (mentionNames.isEmpty()) {
            return List.of();
        }

        return workspaceMemberRepository.findActiveMentionTargets(workspaceId, mentionNames);
    }

    private List<String> parseMentionNames(String content) {
        if (content == null || content.isBlank()) {
            return List.of();
        }

        // LinkedHashMap으로 처음 등장한 순서를 유지하면서 중복 멘션 제거함
        Map<String, Boolean> names = new LinkedHashMap<>();
        Matcher matcher = MENTION_PATTERN.matcher(content);
        while (matcher.find()) {
            names.put(matcher.group(1).toLowerCase(Locale.ROOT), true);
        }
        return List.copyOf(names.keySet());
    }

    private WorkspaceMember findActiveWorkspaceMember(Long workspaceId, Long userId) {
        if (userId == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }

        return workspaceMemberRepository.findByWorkspace_IdAndUser_IdAndIsActiveTrue(workspaceId, userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.FORBIDDEN));
    }

    private Mention findMyMention(Long mentionId, Long userId) {
        Mention mention = mentionRepository.findById(mentionId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "멘션을 찾을 수 없습니다."));
        WorkspaceMember member = findActiveWorkspaceMember(mention.getWorkspace().getId(), userId);

        return mentionRepository.findByIdAndMentionedMember_Id(mentionId, member.getId())
                .orElseThrow(() -> new BusinessException(ErrorCode.FORBIDDEN));
    }
}
