package com.mindplates.nextchapter.common.exception;

/**
 * 예산 상한 초과. <b>재시도하면 안 되는 실패</b>다.
 *
 * <p>일시적 오류와 별도 타입으로 두는 이유가 여기 있다. 벤더 5xx 나 타임아웃은 재시도가 옳지만, 상한
 * 초과는 다시 시도해도 같은 결과이고 그 시도마다 대기·로그·컨슈머 슬롯을 쓴다. 컨슈머가 이 타입을 보면
 * 재시도 없이 뼈대를 실패로 넘긴다.
 */
public class BudgetExceededException extends BusinessException {

    public BudgetExceededException(String message) {
        super(message);
    }
}
