package com.team1.codedock.domain.workspace.controller;

import com.team1.codedock.domain.workspace.dto.WorkspaceResponse;
import com.team1.codedock.domain.workspace.service.WorkspaceService;
import com.team1.codedock.global.exception.GlobalExceptionHandler;
import com.team1.codedock.global.security.CustomUserDetails;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.filter.CharacterEncodingFilter;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class WorkspaceControllerTest {

    private static final Long USER_ID = 100L;

    @Mock
    private WorkspaceService workspaceService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        CustomUserDetails userDetails = mock(CustomUserDetails.class);
        lenient().when(userDetails.getUserId()).thenReturn(USER_ID);
        List<SimpleGrantedAuthority> authorities = List.of(new SimpleGrantedAuthority("ROLE_USER"));
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(userDetails, null, authorities));

        mockMvc = MockMvcBuilders.standaloneSetup(new WorkspaceController(workspaceService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .addFilters(new CharacterEncodingFilter("UTF-8", true))
                .build();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("워크스페이스 로고 업로드 API는 인증 사용자 기준으로 파일을 전달한다")
    void updateWorkspaceLogo() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "logo.png",
                "image/png",
                new byte[]{1, 2, 3}
        );
        WorkspaceResponse response = WorkspaceResponse.builder()
                .id(10L)
                .name("Team")
                .slug("team")
                .myRole("admin")
                .memberCount(3)
                .logoUrl("data:image/png;base64,AQID")
                .build();

        when(workspaceService.updateWorkspaceLogo(eq(10L), any(MultipartFile.class), eq(USER_ID))).thenReturn(response);

        mockMvc.perform(multipart("/api/v1/workspaces/{workspaceId}/logo", 10L)
                        .file(file))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(10L))
                .andExpect(jsonPath("$.data.logoUrl").value("data:image/png;base64,AQID"));

        verify(workspaceService).updateWorkspaceLogo(eq(10L), any(MultipartFile.class), eq(USER_ID));
    }

    @Test
    @DisplayName("워크스페이스 로고 업로드 API는 파일 파트가 없으면 서비스 호출 전에 거부한다")
    void updateWorkspaceLogoWithoutFilePart() throws Exception {
        mockMvc.perform(multipart("/api/v1/workspaces/{workspaceId}/logo", 10L))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("C001"));

        verifyNoInteractions(workspaceService);
    }
}
