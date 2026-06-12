package com.team1.codedock.domain.chat.service;

import com.team1.codedock.domain.chat.dto.MentionResponse;
import com.team1.codedock.domain.chat.entity.Mention;
import com.team1.codedock.domain.chat.entity.Thread;
import com.team1.codedock.domain.chat.entity.ThreadReply;
import com.team1.codedock.domain.chat.repository.MentionRepository;
import com.team1.codedock.domain.workspace.entity.Workspace;
import com.team1.codedock.domain.workspace.entity.WorkspaceMember;
import com.team1.codedock.domain.workspace.repository.WorkspaceMemberRepository;
import com.team1.codedock.global.exception.BusinessException;
import com.team1.codedock.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
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

    private final MentionRepository mentionRepository;
    private final WorkspaceMemberRepository workspaceMemberRepository;

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
        Mention mention = mentionRepository.findById(mentionId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "멘션을 찾을 수 없습니다."));
        WorkspaceMember member = findActiveWorkspaceMember(mention.getWorkspace().getId(), userId);

        Mention myMention = mentionRepository.findByIdAndMentionedMember_Id(mentionId, member.getId())
                .orElseThrow(() -> new BusinessException(ErrorCode.FORBIDDEN));
        myMention.markAsRead();
        return MentionResponse.from(myMention);
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
}
