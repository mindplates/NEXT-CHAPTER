package com.mindplates.nextchapter.common.exception;

/**
 * 인증 실패. 401 로 매핑된다.
 *
 * <p>{@link InvalidOperationException}(400) 과 나눈 이유는 클라이언트의 대응이 다르기 때문이다 —
 * 400 은 입력을 고칠 문제고, 401 은 다시 로그인할 문제다.
 */
public class AuthenticationFailedException extends BusinessException {

    public AuthenticationFailedException(String message) {
        super(message);
    }
}
