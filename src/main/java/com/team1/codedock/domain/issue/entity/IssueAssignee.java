package com.team1.codedock.domain.issue.entity;

import com.team1.codedock.domain.workspace.entity.WorkspaceMember;
import com.team1.codedock.global.entity.BaseCreatedEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "issue_assignees")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class IssueAssignee extends BaseCreatedEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "seq_issue_assignees")
    @SequenceGenerator(name = "seq_issue_assignees", sequenceName = "seq_issue_assignees", allocationSize = 1)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "github_issue_id", nullable = false)
    private GithubIssue githubIssue;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "workspace_member_id", nullable = false)
    private WorkspaceMember workspaceMember;
}
