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
}
