package com.mindplates.nextchapter.common.exception;

/**
 * 도메인 규칙 위반. 호출자가 입력을 고치면 해결되는 오류다.
 *
 * <p>{@link InfrastructureException} 과 나누는 이유는 응답 코드와 재시도 판단이 다르기 때문이다 —
 * 규칙 위반은 4xx 이고 재시도해도 같은 결과지만, 인프라 실패는 5xx 이고 재시도가 의미를 갖는다.
 */
public abstract class BusinessException extends RuntimeException {

    protected BusinessException(String message) {
        super(message);
    }
}
