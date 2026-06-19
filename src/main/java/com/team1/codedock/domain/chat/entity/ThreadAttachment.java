package com.team1.codedock.domain.chat.entity;

import com.team1.codedock.global.entity.BaseCreatedEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "thread_attachments")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ThreadAttachment extends BaseCreatedEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "seq_thread_attachments")
    @SequenceGenerator(name = "seq_thread_attachments", sequenceName = "seq_thread_attachments", allocationSize = 1)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "thread_id", nullable = false)
    private Thread thread;

    // 'file' | 'image' | 'link' | 'pr' | 'issue' | 'api' | 'erd' | 'docs'
    // Oracle 예약어 type -> attachment_type 으로 변경됨
    @Column(name = "attachment_type", nullable = false, length = 30)
    private String attachmentType;

    @Column(name = "target_id")
    private Long targetId;

    @Lob
    @Column
    private String url;

    @Column(length = 255)
    private String title;

    @Column(length = 255)
    private String detail;

    @Lob
    @Column
    private String meta;

    @Lob
    @Column(name = "preview_url")
    private String previewUrl;

    @Column(name = "mime_type", length = 100)
    private String mimeType;

    // Oracle 예약어 size -> file_size 로 변경됨
    @Column(name = "file_size")
    private Long fileSize;

    public void updateMeta(String meta) {
        this.meta = meta;
    }

    public static ThreadAttachment create(
            Thread thread,
            String attachmentType,
            Long targetId,
            String url,
            String title,
            String detail,
            String meta,
            String previewUrl,
            String mimeType,
            Long fileSize
    ) {
        ThreadAttachment attachment = new ThreadAttachment();
        attachment.thread = thread;
        attachment.attachmentType = attachmentType;
        attachment.targetId = targetId;
        attachment.url = url;
        attachment.title = title;
        attachment.detail = detail;
        attachment.meta = meta;
        attachment.previewUrl = previewUrl;
        attachment.mimeType = mimeType;
        attachment.fileSize = fileSize;
        return attachment;
    }
}
