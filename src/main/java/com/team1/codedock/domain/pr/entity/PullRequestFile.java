package com.team1.codedock.domain.pr.entity;

import com.team1.codedock.global.entity.BaseCreatedEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "pull_request_files")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PullRequestFile extends BaseCreatedEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "seq_pr_files")
    @SequenceGenerator(name = "seq_pr_files", sequenceName = "seq_pr_files", allocationSize = 1)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "github_pull_request_id", nullable = false)
    private GithubPullRequest githubPullRequest;

    @Column(nullable = false, length = 255)
    private String filename;

    @Column(length = 50)
    private String status;

    @Column(nullable = false)
    private Integer additions;

    @Column(nullable = false)
    private Integer deletions;

    @Lob
    @Column
    private String path;

    @Lob
    @Column
    private String patch;

    public static PullRequestFile create(GithubPullRequest pr, String filename, String status,
                                         int additions, int deletions, String path, String patch) {
        PullRequestFile file = new PullRequestFile();
        file.githubPullRequest = pr;
        file.filename = filename;
        file.status = status;
        file.additions = additions;
        file.deletions = deletions;
        file.path = path;
        file.patch = patch;
        return file;
    }
}
