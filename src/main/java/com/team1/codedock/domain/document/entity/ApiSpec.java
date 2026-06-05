package com.team1.codedock.domain.document.entity;

import com.team1.codedock.domain.issue.entity.GithubIssue;
import com.team1.codedock.domain.pr.entity.GithubPullRequest;
import com.team1.codedock.domain.workspace.entity.Workspace;
import com.team1.codedock.domain.workspace.entity.WorkspaceMember;
import com.team1.codedock.global.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "api_specs")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ApiSpec extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "seq_api_specs")
    @SequenceGenerator(name = "seq_api_specs", sequenceName = "seq_api_specs", allocationSize = 1)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "workspace_id", nullable = false)
    private Workspace workspace;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by_id", nullable = false)
    private WorkspaceMember createdBy;

    @Column(nullable = false, length = 255)
    private String title;

    // 'GET' | 'POST' | 'PUT' | 'PATCH' | 'DELETE'
    @Column(nullable = false, length = 10)
    private String method;

    @Column(nullable = false, length = 255)
    private String endpoint;

    @Column(name = "group_name", length = 100)
    private String groupName;

    @Column(length = 100)
    private String entity;

    @Column(length = 255)
    private String summary;

    @Lob
    @Column
    private String description;

    // 'completed' | 'in_progress' | 'design'
    @Column(length = 30)
    private String status;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assignee_id")
    private WorkspaceMember assignee;

    @Lob
    @Column(name = "path_params")
    private String pathParams;

    @Lob
    @Column
    private String headers;

    @Lob
    @Column(name = "query_params")
    private String queryParams;

    @Lob
    @Column(name = "request_body")
    private String requestBody;

    @Column(name = "response_status")
    private Integer responseStatus;

    @Lob
    @Column(name = "response_body")
    private String responseBody;

    @Column(length = 50)
    private String version;

    // 'manual' | 'github' | 'imported'
    @Column(name = "source_type", length = 30)
    private String sourceType;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "related_issue_id")
    private GithubIssue relatedIssue;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "related_pr_id")
    private GithubPullRequest relatedPr;

    @Lob
    @Column
    private String note;
}
