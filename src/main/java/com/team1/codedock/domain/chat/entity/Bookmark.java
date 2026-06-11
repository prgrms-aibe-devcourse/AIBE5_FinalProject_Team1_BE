package com.team1.codedock.domain.chat.entity;

import com.team1.codedock.domain.workspace.entity.WorkspaceMember;
import com.team1.codedock.global.entity.BaseCreatedEntity;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "bookmarks")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Bookmark extends BaseCreatedEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "seq_bookmarks")
    @SequenceGenerator(name = "seq_bookmarks", sequenceName = "seq_bookmarks", allocationSize = 1)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "workspace_member_id", nullable = false)
    private WorkspaceMember workspaceMember;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "thread_id", nullable = false)
    private Thread thread;

    // 현재 bookmarks 테이블은 thread_id만 가지므로 채널 메시지 북마크 생성함
    public static Bookmark create(WorkspaceMember workspaceMember, Thread thread) {
        Bookmark bookmark = new Bookmark();
        bookmark.workspaceMember = workspaceMember;
        bookmark.thread = thread;
        return bookmark;
    }
}
