package com.mindplates.nextchapter.common.exception;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 예외 계층이 응답 코드 매핑의 기준이므로 계층 관계 자체를 고정해 둔다.
 *
 * <p>{@link BusinessException}(4xx)과 {@link InfrastructureException}(5xx)의 구분이 깨지면 규칙 위반이
 * 500 으로, 인프라 실패가 400 으로 나간다 — 둘 다 재시도 판단을 틀리게 만든다.
 */
@DisplayName("예외 계층")
class ExceptionMessagesTest {

    @Test
    @DisplayName("엔티티 없음 메시지에 대상과 ID 가 들어간다")
    void entityNotFound() {
        assertThat(new EntityNotFoundException("주제", 42L).getMessage())
                .contains("주제")
                .contains("42");
    }

    @Test
    @DisplayName("중복 메시지에 필드와 값이 들어간다")
    void duplicate() {
        assertThat(new DuplicateException("주제", "slug", "machine-learning").getMessage())
                .contains("slug")
                .contains("machine-learning");
    }

    @Test
    @DisplayName("규칙 위반은 BusinessException, 외부 실패는 InfrastructureException 계열이다")
    void hierarchy() {
        assertThat(new EntityNotFoundException("주제", 1L)).isInstanceOf(BusinessException.class);
        assertThat(new DuplicateException("중복")).isInstanceOf(BusinessException.class);
        assertThat(new InvalidOperationException("불가")).isInstanceOf(BusinessException.class);
        assertThat(new AuthenticationFailedException("실패")).isInstanceOf(BusinessException.class);
        assertThat(new ExternalApiException("외부 실패")).isInstanceOf(InfrastructureException.class);
    }

    @Test
    @DisplayName("외부 실패는 원인을 보존한다 — 로그에서 근본 원인을 찾을 수 있어야 한다")
    void preservesCause() {
        Throwable cause = new IllegalStateException("소켓 닫힘");

        assertThat(new ExternalApiException("호출 실패", cause)).hasCause(cause);
    }
}
