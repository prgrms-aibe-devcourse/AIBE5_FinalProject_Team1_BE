package com.team1.codedock.domain.issue.dto;

import com.team1.codedock.domain.issue.entity.GithubIssue;
import com.team1.codedock.domain.issue.entity.IssueAssignee;
import com.team1.codedock.domain.issue.entity.IssueLabel;

import java.time.LocalDateTime;
import java.util.List;

public record IssueResponse(
        Long id,
        String githubIssueId,
        Long repositoryId,
        String repositoryFullName,
        Long channelId,
        Integer issueNumber,
        String title,
        String description,
        String state,
        String localStatus,
        String url,
        String author,
        String priority,
        String issueType,
        List<IssueLabelDto> labels,
        List<String> assignees,
        // github* 시각은 UTC LocalDateTime이라 JS가 UTC로 파싱하도록 'Z'를 붙인 ISO 문자열로 내려준다.
        String closedAt,
        String githubCreatedAt,
        String githubUpdatedAt,
        LocalDateTime createdAt
) {

    public record IssueLabelDto(String name, String color) {
        public static IssueLabelDto from(IssueLabel label) {
            return new IssueLabelDto(label.getName(), label.getColor());
        }
    }

    // UTC LocalDateTime → 'Z' 표식이 붙은 ISO 문자열(없으면 null).
    private static String toUtcIso(LocalDateTime value) {
        return value == null ? null : value.toString() + "Z";
    }

    public static IssueResponse from(GithubIssue issue, List<IssueLabel> labels, List<IssueAssignee> assignees) {
        return new IssueResponse(
                issue.getId(),
                issue.getGithubIssueId(),
                issue.getRepository().getId(),
                issue.getRepository().getFullName(),
                issue.getChannel().getId(),
                issue.getIssueNumber(),
                issue.getTitle(),
                issue.getDescription(),
                issue.getState(),
                issue.getLocalStatus(),
                issue.getUrl(),
                issue.getAuthor(),
                issue.getPriority(),
                issue.getIssueType(),
                labels.stream().map(IssueLabelDto::from).toList(),
                assignees.stream()
                        .map(a -> a.getWorkspaceMember().getUser().getDisplayName() != null
                                ? a.getWorkspaceMember().getUser().getDisplayName()
                                : a.getWorkspaceMember().getUser().getUsername())
                        .toList(),
                toUtcIso(issue.getClosedAt()),
                toUtcIso(issue.getGithubCreatedAt()),
                toUtcIso(issue.getGithubUpdatedAt()),
                issue.getCreatedAt()
        );
    }

    public static IssueResponse fromSimple(GithubIssue issue) {
        return from(issue, List.of(), List.of());
    }
}
