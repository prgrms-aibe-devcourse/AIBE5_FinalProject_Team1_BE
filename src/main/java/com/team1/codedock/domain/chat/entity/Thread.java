package com.team1.codedock.domain.chat.entity;

import com.team1.codedock.domain.channel.entity.Channel;
import com.team1.codedock.domain.workspace.entity.WorkspaceMember;
import com.team1.codedock.global.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "threads")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Thread extends BaseEntity {

    public static final String TYPE_USER_MESSAGE = "user_message";
    public static final String DELETED_MESSAGE_CONTENT = "삭제된 메시지입니다";

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "seq_threads")
    @SequenceGenerator(name = "seq_threads", sequenceName = "seq_threads", allocationSize = 1)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "channel_id", nullable = false)
    private Channel channel;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by_id")
    private WorkspaceMember createdBy;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reply_to_id")
    private Thread replyTo;

    // 'user_message' | 'github_bot_notification' | 'system'
    @Column(name = "thread_type", nullable = false, length = 50)
    private String threadType;

    // 'github_issue' | 'github_pull_request'
    @Column(name = "threadable_type", length = 50)
    private String threadableType;

    @Column(name = "threadable_id")
    private Long threadableId;

    @Column(length = 255)
    private String title;

    @Lob
    @Column
    private String content;

    public void updateContent(String content) {
        this.content = content;
    }

    public void markAsDeleted() {
        this.content = DELETED_MESSAGE_CONTENT;
    }

    public static Thread createChannelMessage(Channel channel, WorkspaceMember createdBy, String content) {
        Thread thread = new Thread();
        thread.channel = channel;
        thread.createdBy = createdBy;
        thread.replyTo = null;
        thread.threadType = TYPE_USER_MESSAGE;
        thread.threadableType = null;
        thread.threadableId = null;
        thread.title = null;
        thread.content = content;
        return thread;
    }
}
