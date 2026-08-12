package com.mindplates.nextchapter.common.exception;

/**
 * 레이트 리밋 초과. 429 로 매핑된다.
 *
 * <p>400 과 나눈 이유는 클라이언트의 대응이 다르기 때문이다 — 400 은 입력을 고칠 문제고, 429 는 기다릴
 * 문제다. 같은 상태 코드로 접으면 클라이언트가 즉시 재시도해 한도를 계속 소진시킨다.
 */
public class RateLimitExceededException extends BusinessException {

    public RateLimitExceededException(String message) {
        super(message);
    }
}
