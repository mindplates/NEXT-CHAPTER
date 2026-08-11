package com.mindplates.nextchapter.common.response;

import java.util.List;

/**
 * REST 응답 봉투. 프로젝트 규칙의 {@code ApiResponse<T>} 형태를 그대로 따른다.
 *
 * <p>성공은 {@code data} 만, 실패는 {@code message}(+ 필드 오류)만 채운다.
 */
public record ApiResponse<T>(T data, String message, List<FieldError> errors) {

    public static <T> ApiResponse<T> ok(T data) {
        return new ApiResponse<>(data, null, null);
    }

    public static <T> ApiResponse<T> error(String message) {
        return new ApiResponse<>(null, message, null);
    }

    public static <T> ApiResponse<T> error(String message, List<FieldError> errors) {
        return new ApiResponse<>(null, message, errors);
    }

    public record FieldError(String field, String message) {}
}
