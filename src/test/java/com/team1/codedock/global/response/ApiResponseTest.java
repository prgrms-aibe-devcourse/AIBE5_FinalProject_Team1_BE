package com.team1.codedock.global.response;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ApiResponseTest {

    @Test
    @DisplayName("ok(data) - 데이터와 함께 성공 응답을 생성한다")
    void ok_withData() {
        String data = "test";

        ApiResponse<String> response = ApiResponse.ok(data);

        assertThat(response.isSuccess()).isTrue();
        assertThat(response.getData()).isEqualTo("test");
        assertThat(response.getCode()).isNull();
        assertThat(response.getMessage()).isNull();
    }

    @Test
    @DisplayName("ok() - 데이터 없이 성공 응답을 생성한다")
    void ok_withoutData() {
        ApiResponse<Void> response = ApiResponse.ok();

        assertThat(response.isSuccess()).isTrue();
        assertThat(response.getData()).isNull();
    }

    @Test
    @DisplayName("fail() - 에러 코드와 메시지로 실패 응답을 생성한다")
    void fail() {
        ApiResponse<Void> response = ApiResponse.fail("C001", "입력값이 올바르지 않습니다.");

        assertThat(response.isSuccess()).isFalse();
        assertThat(response.getCode()).isEqualTo("C001");
        assertThat(response.getMessage()).isEqualTo("입력값이 올바르지 않습니다.");
        assertThat(response.getData()).isNull();
    }
}
