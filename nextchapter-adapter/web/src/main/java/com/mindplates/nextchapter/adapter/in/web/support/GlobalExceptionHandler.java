package com.mindplates.nextchapter.adapter.in.web.support;

import com.mindplates.nextchapter.common.exception.AuthenticationFailedException;
import com.mindplates.nextchapter.common.exception.BusinessException;
import com.mindplates.nextchapter.common.exception.DuplicateException;
import com.mindplates.nextchapter.common.exception.EntityNotFoundException;
import com.mindplates.nextchapter.common.exception.InfrastructureException;
import com.mindplates.nextchapter.common.exception.RateLimitExceededException;
import com.mindplates.nextchapter.common.response.ApiResponse;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.ServletRequestBindingException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

/**
 * 예외 → HTTP 응답 매핑을 한 곳에 모은다.
 *
 * <p>마지막 핸들러가 예외 메시지를 그대로 내보내지 않는 것이 요점이다. 예상하지 못한 예외의
 * 메시지에는 드라이버 오류·쿼리 조각·경로 같은 내부 정보가 섞여 들어온다. 상세는 로그에만 남기고
 * 응답에는 고정 문구를 준다 (프로젝트 보안 규칙 — 오류 메시지가 내부 정보를 흘리지 않는다).
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(EntityNotFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleNotFound(EntityNotFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiResponse.error(e.getMessage()));
    }

    @ExceptionHandler(AuthenticationFailedException.class)
    public ResponseEntity<ApiResponse<Void>> handleAuthenticationFailed(AuthenticationFailedException e) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(ApiResponse.error(e.getMessage()));
    }

    @ExceptionHandler(DuplicateException.class)
    public ResponseEntity<ApiResponse<Void>> handleDuplicate(DuplicateException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(ApiResponse.error(e.getMessage()));
    }

    /**
     * 429. 400 과 나누는 이유는 클라이언트의 대응이 다르기 때문이다 — 입력을 고칠 문제가 아니라 기다릴
     * 문제다. 같은 코드로 접으면 클라이언트가 즉시 재시도해 한도를 계속 소진시킨다.
     */
    @ExceptionHandler(RateLimitExceededException.class)
    public ResponseEntity<ApiResponse<Void>> handleRateLimit(RateLimitExceededException e) {
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).body(ApiResponse.error(e.getMessage()));
    }

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResponse<Void>> handleBusiness(BusinessException e) {
        return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiResponse<Void>> handleIllegalArgument(IllegalArgumentException e) {
        return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidation(MethodArgumentNotValidException e) {
        List<ApiResponse.FieldError> errors = e.getBindingResult().getFieldErrors().stream()
                .map(error -> new ApiResponse.FieldError(error.getField(), error.getDefaultMessage()))
                .toList();
        return ResponseEntity.badRequest().body(ApiResponse.error("입력값이 올바르지 않습니다.", errors));
    }

    /**
     * 경로 변수·쿼리 파라미터의 타입이 맞지 않는 경우 — 400 이다.
     *
     * <p>이 핸들러가 없으면 잘못된 입력이 마지막 핸들러로 떨어져 <b>500</b> 이 된다. 없는 enum 값
     * ({@code ?format=HOLOGRAM}) 이나 숫자가 아닌 ID 가 서버 오류로 보이면, 운영자는 고칠 것이 없는
     * 알림을 계속 받고 진짜 오류가 그 사이에 묻힌다.
     *
     * <p>예외 메시지를 그대로 내보내지 않는다. 거기에는 대상 Java 타입 이름이 들어 있다.
     */
    /**
     * 필수 파라미터·헤더가 빠진 요청 — 400 이다.
     *
     * <p>{@link MethodArgumentTypeMismatchException}·{@link HttpMessageNotReadableException} 와 함께,
     * <b>Spring MVC 의 바인딩 예외 전체</b>가 매핑돼 있지 않으면 잘못된 요청이 500 으로 나간다. 세 가지를
     * 각각 발견했고 원인은 하나였다 — 마지막 핸들러가 모든 입력 오류를 서버 오류로 위장시킨다.
     */
    @ExceptionHandler(ServletRequestBindingException.class)
    public ResponseEntity<ApiResponse<Void>> handleBinding(ServletRequestBindingException e) {
        log.debug("[요청] 바인딩 실패: {}", e.getClass().getSimpleName());
        return ResponseEntity.badRequest().body(ApiResponse.error("필수 요청 값이 빠졌습니다."));
    }

    /**
     * 본문을 읽을 수 없는 요청 — 400 이다.
     *
     * <p>없는 enum 값이나 깨진 JSON 이 이 경로로 온다. 핸들러가 없으면 마지막 핸들러로 떨어져 <b>500</b> 이
     * 되고, 입력 오류가 서버 오류로 위장된다.
     *
     * <p>예외 메시지를 그대로 내보내지 않는다. Jackson 의 메시지에는 대상 Java 타입·필드 경로와 함께
     * <b>보낸 값 일부</b>가 들어 있는데, 그 값이 질문·오류 신고 본문일 수 있다.
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiResponse<Void>> handleUnreadableBody(HttpMessageNotReadableException e) {
        log.debug("[요청] 본문을 읽을 수 없다: {}", e.getClass().getSimpleName());
        return ResponseEntity.badRequest().body(ApiResponse.error("요청 본문을 읽을 수 없습니다."));
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiResponse<Void>> handleTypeMismatch(MethodArgumentTypeMismatchException e) {
        return ResponseEntity.badRequest()
                .body(ApiResponse.error(
                        "요청 값이 올바르지 않습니다.", List.of(new ApiResponse.FieldError(e.getName(), "허용되지 않은 값입니다."))));
    }

    @ExceptionHandler(InfrastructureException.class)
    public ResponseEntity<ApiResponse<Void>> handleInfrastructure(InfrastructureException e) {
        log.error("[인프라] 외부 시스템 호출 실패: {}", e.getMessage(), e);
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(ApiResponse.error(e.getMessage()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleUnexpected(Exception e) {
        log.error("[예기치 못한 오류] {}", e.getMessage(), e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ApiResponse.error("요청을 처리하지 못했습니다."));
    }
}
