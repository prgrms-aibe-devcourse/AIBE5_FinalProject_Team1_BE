package com.team1.codedock.domain.chat.entity;

import com.team1.codedock.domain.workspace.entity.WorkspaceMember;
import com.team1.codedock.global.entity.BaseCreatedEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "reactions")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Reaction extends BaseCreatedEntity {

    public static final String TARGET_TYPE_THREAD = "thread";
    public static final String TARGET_TYPE_THREAD_REPLY = "thread_reply";

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "seq_reactions")
    @SequenceGenerator(name = "seq_reactions", sequenceName = "seq_reactions", allocationSize = 1)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "workspace_member_id", nullable = false)
    private WorkspaceMember workspaceMember;

    // 'thread' | 'thread_reply'
    @Column(name = "target_type", nullable = false, length = 30)
    private String targetType;

    @Column(name = "target_id", nullable = false)
    private Long targetId;

    @Column(nullable = false, length = 50)
    private String emoji;

    public static Reaction create(
            WorkspaceMember workspaceMember,
            String targetType,
            Long targetId,
            String emoji
    ) {
        Reaction reaction = new Reaction();
        reaction.workspaceMember = workspaceMember;
        reaction.targetType = targetType;
        reaction.targetId = targetId;
        reaction.emoji = emoji;
        return reaction;
    }
}
