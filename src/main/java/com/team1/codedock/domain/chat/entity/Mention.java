package com.team1.codedock.domain.chat.entity;

import com.team1.codedock.domain.workspace.entity.Workspace;
import com.team1.codedock.domain.workspace.entity.WorkspaceMember;
import com.team1.codedock.global.entity.BaseCreatedEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "mentions")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Mention extends BaseCreatedEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "seq_mentions")
    @SequenceGenerator(name = "seq_mentions", sequenceName = "seq_mentions", allocationSize = 1)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "workspace_id", nullable = false)
    private Workspace workspace;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "thread_id")
    private Thread thread;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "thread_reply_id")
    private ThreadReply threadReply;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "mentioned_member_id", nullable = false)
    private WorkspaceMember mentionedMember;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "mentioned_by_member_id", nullable = false)
    private WorkspaceMember mentionedByMember;

    @Column(name = "is_read", nullable = false)
    private boolean isRead;

    // 채널 메시지를 가리키는 멘션 생성함
    public static Mention createForThread(
            Workspace workspace,
            Thread thread,
            WorkspaceMember mentionedMember,
            WorkspaceMember mentionedByMember
    ) {
        Mention mention = new Mention();
        mention.workspace = workspace;
        mention.thread = thread;
        mention.threadReply = null;
        mention.mentionedMember = mentionedMember;
        mention.mentionedByMember = mentionedByMember;
        mention.isRead = false;
        return mention;
    }

    // 스레드 답글을 가리키는 멘션 생성함
    public static Mention createForThreadReply(
            Workspace workspace,
            ThreadReply threadReply,
            WorkspaceMember mentionedMember,
            WorkspaceMember mentionedByMember
    ) {
        Mention mention = new Mention();
        mention.workspace = workspace;
        mention.thread = null;
        mention.threadReply = threadReply;
        mention.mentionedMember = mentionedMember;
        mention.mentionedByMember = mentionedByMember;
        mention.isRead = false;
        return mention;
    }

    public void markAsRead() {
        this.isRead = true;
    }
}
