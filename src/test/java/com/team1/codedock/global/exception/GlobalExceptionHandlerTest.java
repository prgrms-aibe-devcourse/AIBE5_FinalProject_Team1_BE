package com.team1.codedock.global.exception;

import com.team1.codedock.global.response.ApiResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.filter.CharacterEncodingFilter;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class GlobalExceptionHandlerTest {

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new TestController())
                .setControllerAdvice(new GlobalExceptionHandler())
                .addFilters(new CharacterEncodingFilter("UTF-8", true))
                .build();
    }

    @RestController
    static class TestController {

        @GetMapping("/test/business-exception")
        public ApiResponse<Void> throwBusinessException() {
            throw new BusinessException(ErrorCode.USER_NOT_FOUND);
        }

        @GetMapping("/test/unhandled-exception")
        public ApiResponse<Void> throwUnhandledException() {
            throw new RuntimeException("예상치 못한 오류");
        }
    }

    @Test
    @DisplayName("BusinessException 발생 시 ErrorCode의 HTTP 상태와 에러 응답을 반환한다")
    void handleBusinessException() throws Exception {
        mockMvc.perform(get("/test/business-exception")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("U001"))
                .andExpect(jsonPath("$.message").value(ErrorCode.USER_NOT_FOUND.getMessage()));
    }

    @Test
    @DisplayName("처리되지 않은 예외 발생 시 500 에러 응답을 반환한다")
    void handleUnexpectedException() throws Exception {
        mockMvc.perform(get("/test/unhandled-exception")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("C005"));
    }
}
