package com.mindplates.nextchapter.common.response;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("API 응답 봉투")
class ApiResponseTest {

    @Test
    @DisplayName("성공 응답에는 message 가 없다")
    void okCarriesOnlyData() {
        ApiResponse<String> response = ApiResponse.ok("값");

        assertThat(response.data()).isEqualTo("값");
        assertThat(response.message()).isNull();
        assertThat(response.errors()).isNull();
    }

    @Test
    @DisplayName("실패 응답에는 data 가 없다 — 부분 성공을 표현하지 않는다")
    void errorCarriesNoData() {
        ApiResponse<String> response = ApiResponse.error("실패했습니다.");

        assertThat(response.data()).isNull();
        assertThat(response.message()).isEqualTo("실패했습니다.");
    }

    @Test
    @DisplayName("필드 오류를 함께 담을 수 있다")
    void errorWithFieldErrors() {
        ApiResponse<Void> response =
                ApiResponse.error("입력값이 올바르지 않습니다.", List.of(new ApiResponse.FieldError("slug", "필수입니다.")));

        assertThat(response.errors()).singleElement().satisfies(error -> {
            assertThat(error.field()).isEqualTo("slug");
            assertThat(error.message()).isEqualTo("필수입니다.");
        });
    }
}
