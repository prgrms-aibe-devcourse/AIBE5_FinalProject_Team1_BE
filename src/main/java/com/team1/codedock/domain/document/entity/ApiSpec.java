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

    // 'manual' | 'github' | 'imported' | 'swagger' | 'AI'
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

    public static ApiSpec create(
            Workspace workspace,
            WorkspaceMember createdBy,
            String title,
            String method,
            String endpoint,
            String groupName,
            String entityName,
            String summary,
            String description,
            String status,
            WorkspaceMember assignee,
            String pathParams,
            String headers,
            String queryParams,
            String requestBody,
            String responseBody,
            Integer responseStatus,
            String version,
            String sourceType,
            GithubIssue relatedIssue,
            GithubPullRequest relatedPr,
            String note
    ) {
        ApiSpec spec = new ApiSpec();
        spec.workspace = workspace;
        spec.createdBy = createdBy;
        spec.title = title;
        spec.method = method;
        spec.endpoint = endpoint;
        spec.groupName = groupName;
        spec.entity = entityName;
        spec.summary = summary;
        spec.description = description;
        spec.status = status != null ? status : "design";
        spec.assignee = assignee;
        spec.pathParams = pathParams;
        spec.headers = headers;
        spec.queryParams = queryParams;
        spec.requestBody = requestBody;
        spec.responseBody = responseBody;
        spec.responseStatus = responseStatus;
        spec.version = version;
        spec.sourceType = sourceType != null ? sourceType : "manual";
        spec.relatedIssue = relatedIssue;
        spec.relatedPr = relatedPr;
        spec.note = note;
        return spec;
    }

    public static ApiSpec createFromSwagger(
            Workspace workspace,
            WorkspaceMember createdBy,
            String title,
            String method,
            String endpoint,
            String groupName,
            String summary,
            String description,
            String pathParams,
            String headers,
            String queryParams,
            String requestBody,
            String responseBody,
            Integer responseStatus
    ) {
        ApiSpec spec = new ApiSpec();
        spec.workspace = workspace;
        spec.createdBy = createdBy;
        spec.title = title;
        spec.method = method;
        spec.endpoint = endpoint;
        spec.groupName = groupName;
        spec.summary = summary;
        spec.description = description;
        spec.pathParams = pathParams;
        spec.headers = headers;
        spec.queryParams = queryParams;
        spec.requestBody = requestBody;
        spec.responseBody = responseBody;
        spec.responseStatus = responseStatus;
        spec.status = "completed";
        spec.sourceType = "swagger";
        return spec;
    }

    public static ApiSpec createFromAi(
            Workspace workspace,
            WorkspaceMember createdBy,
            String title,
            String method,
            String endpoint,
            String groupName,
            String summary,
            String description
    ) {
        ApiSpec spec = new ApiSpec();
        spec.workspace = workspace;
        spec.createdBy = createdBy;
        spec.title = title;
        spec.method = method;
        spec.endpoint = endpoint;
        spec.groupName = groupName;
        spec.summary = summary;
        spec.description = description;
        spec.status = "design";
        spec.sourceType = "AI";
        return spec;
    }

    public void update(
            String title,
            String method,
            String endpoint,
            String groupName,
            String entityName,
            String summary,
            String description,
            String status,
            WorkspaceMember assignee,
            String pathParams,
            String headers,
            String queryParams,
            String requestBody,
            String responseBody,
            Integer responseStatus,
            String version,
            String sourceType,
            GithubIssue relatedIssue,
            GithubPullRequest relatedPr,
            String note
    ) {
        if (title != null) this.title = title;
        if (method != null) this.method = method;
        if (endpoint != null) this.endpoint = endpoint;
        if (groupName != null) this.groupName = groupName;
        if (entityName != null) this.entity = entityName;
        if (summary != null) this.summary = summary;
        if (description != null) this.description = description;
        if (status != null) this.status = status;
        if (assignee != null) this.assignee = assignee;
        if (pathParams != null) this.pathParams = pathParams;
        if (headers != null) this.headers = headers;
        if (queryParams != null) this.queryParams = queryParams;
        if (requestBody != null) this.requestBody = requestBody;
        if (responseBody != null) this.responseBody = responseBody;
        if (responseStatus != null) this.responseStatus = responseStatus;
        if (version != null) this.version = version;
        if (sourceType != null) this.sourceType = sourceType;
        if (relatedIssue != null) this.relatedIssue = relatedIssue;
        if (relatedPr != null) this.relatedPr = relatedPr;
        if (note != null) this.note = note;
    }
}
