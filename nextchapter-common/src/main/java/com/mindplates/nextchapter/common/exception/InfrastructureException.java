package com.mindplates.nextchapter.common.exception;

/** 외부 시스템·저장소 실패. 입력이 옳아도 발생하며 재시도가 의미를 갖는다. */
public abstract class InfrastructureException extends RuntimeException {

    protected InfrastructureException(String message) {
        super(message);
    }

    protected InfrastructureException(String message, Throwable cause) {
        super(message, cause);
    }
}
