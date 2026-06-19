package com.team1.codedock.domain.workspace.dto;

import com.team1.codedock.domain.user.entity.User;
import com.team1.codedock.domain.workspace.entity.Workspace;
import com.team1.codedock.domain.workspace.entity.WorkspaceMember;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;

class WorkspaceMemberResponseTest {

    @Test
    @DisplayName("멤버 응답은 직무(position)를 포함한다")
    void from_includesPosition() {
        User user = User.create("a@x.com", "hash", "name");
        ReflectionTestUtils.setField(user, "id", 1L);
        Workspace workspace = Workspace.create(user, "WS", "ws", "");
        WorkspaceMember member = WorkspaceMember.create(workspace, user, "viewer");
        member.assignPosition("Frontend Developer");

        WorkspaceMemberResponse response = WorkspaceMemberResponse.from(member);

        assertThat(response.getPosition()).isEqualTo("Frontend Developer");
        assertThat(response.getRole()).isEqualTo("viewer");
    }
}