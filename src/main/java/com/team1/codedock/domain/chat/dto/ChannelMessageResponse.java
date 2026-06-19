package com.team1.codedock.domain.chat.dto;

import com.team1.codedock.domain.chat.entity.Thread;
import com.team1.codedock.domain.chat.util.ChatContentEmojiCodec;
import com.team1.codedock.domain.user.entity.User;
import com.team1.codedock.domain.workspace.entity.WorkspaceMember;

import java.time.LocalDateTime;
import java.util.List;

public record ChannelMessageResponse(
        Long id,
        Long channelId,
        Long senderMemberId,
        String senderName,
        String content,
        LocalDateTime createdAt,
        List<ThreadAttachmentResponse> attachments,
        ReplyToSummary replyTo
) {

    private static final int REPLY_PREVIEW_MAX_LENGTH = 100;

    // 답장 대상 메시지 요약 (없으면 null)
    public record ReplyToSummary(
            Long messageId,
            String senderName,
            String content
    ) {
    }

    public ChannelMessageResponse(
            Long id,
            Long channelId,
            Long senderMemberId,
            String senderName,
            String content,
            LocalDateTime createdAt
    ) {
        this(id, channelId, senderMemberId, senderName, content, createdAt, List.of(), null);
    }

    public static ChannelMessageResponse from(Thread thread) {
        return from(thread, List.of());
    }

    public static ChannelMessageResponse from(Thread thread, List<ThreadAttachmentResponse> attachments) {
        WorkspaceMember sender = thread.getCreatedBy();
        if (sender == null) {
            return fromBot(thread, "GitHub Bot", attachments);
        }
        User user = sender.getUser();
        return new ChannelMessageResponse(
                thread.getId(),
                thread.getChannel().getId(),
                sender.getId(),
                resolveSenderName(user),
                ChatContentEmojiCodec.decode(thread.getContent()),
                thread.getCreatedAt(),
                attachments == null ? List.of() : attachments,
                toReplyToSummary(thread.getReplyTo())
        );
    }

    public static ChannelMessageResponse fromBot(Thread thread, String botName, List<ThreadAttachmentResponse> attachments) {
        return new ChannelMessageResponse(
                thread.getId(),
                thread.getChannel().getId(),
                null,
                botName,
                thread.getContent(),
                thread.getCreatedAt(),
                attachments == null ? List.of() : attachments,
                null
        );
    }

    private static ReplyToSummary toReplyToSummary(Thread replyTo) {
        if (replyTo == null) {
            return null;
        }
        WorkspaceMember sender = replyTo.getCreatedBy();
        String senderName = sender == null ? null : resolveSenderName(sender.getUser());
        return new ReplyToSummary(
                replyTo.getId(),
                senderName,
                toReplyPreview(ChatContentEmojiCodec.decode(replyTo.getContent()))
        );
    }

    // 답장 인용은 UI에서 한 줄로 잘려 표시되므로 응답 페이로드도 프리뷰 길이로 제한함
    private static String toReplyPreview(String content) {
        if (content == null || content.length() <= REPLY_PREVIEW_MAX_LENGTH) {
            return content;
        }
        return content.substring(0, REPLY_PREVIEW_MAX_LENGTH) + "…";
    }
    private static String resolveSenderName(User user) {
        if (user.getDisplayName() != null && !user.getDisplayName().isBlank()) {
            return user.getDisplayName();
        }
        if (user.getNickname() != null && !user.getNickname().isBlank()) {
            return user.getNickname();
        }
        return user.getUsername();
    }
}
