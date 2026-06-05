package com.team1.codedock.domain.chat.entity;

import com.team1.codedock.domain.channel.entity.Channel;
import com.team1.codedock.domain.workspace.entity.WorkspaceMember;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "channel_read_status")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ChannelReadStatus {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "seq_channel_read_status")
    @SequenceGenerator(name = "seq_channel_read_status", sequenceName = "seq_channel_read_status", allocationSize = 1)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "channel_id", nullable = false)
    private Channel channel;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "workspace_member_id", nullable = false)
    private WorkspaceMember workspaceMember;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "last_read_thread_id")
    private Thread lastReadThread;

    @Column(name = "last_read_at", nullable = false)
    private LocalDateTime lastReadAt;
}
