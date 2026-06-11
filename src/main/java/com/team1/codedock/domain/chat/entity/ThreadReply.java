package com.team1.codedock.domain.chat.entity;

import com.team1.codedock.domain.workspace.entity.WorkspaceMember;
import com.team1.codedock.global.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "thread_replies")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ThreadReply extends BaseEntity {

    public static final String DELETED_REPLY_CONTENT = Thread.DELETED_MESSAGE_CONTENT;

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "seq_thread_replies")
    @SequenceGenerator(name = "seq_thread_replies", sequenceName = "seq_thread_replies", allocationSize = 1)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "thread_id", nullable = false)
    private Thread thread;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "workspace_member_id", nullable = false)
    private WorkspaceMember workspaceMember;

    @Lob
    @Column(nullable = false)
    private String content;

    public static ThreadReply create(Thread thread, WorkspaceMember workspaceMember, String content) {
        ThreadReply reply = new ThreadReply();
        reply.thread = thread;
        reply.workspaceMember = workspaceMember;
        reply.content = content;
        return reply;
    }

    public void updateContent(String content) {
        this.content = content;
    }

    public void markAsDeleted() {
        this.content = DELETED_REPLY_CONTENT;
    }

    public boolean isDeleted() {
        return DELETED_REPLY_CONTENT.equals(this.content);
    }
}
