package com.mindplates.nextchapter.adapter.out.messaging.generation;

import com.mindplates.nextchapter.application.generation.port.in.AdvanceGenerationStageUseCase;
import com.mindplates.nextchapter.common.exception.BudgetExceededException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 예산 초과를 <b>재시도하지 않고</b> 즉시 실패로 확정한다.
 *
 * <p>다른 생성 실패(벤더 5xx, 타임아웃)는 재시도가 곧 해결책이지만 예산 초과는 반대다. 상한은 운영자가
 * 올려 주기 전까지 그대로이므로 재시도는 <b>같은 결과를 반복</b>하고, 그동안 파티션이 막혀 다른 뼈대의
 * 생성까지 밀린다. 재시도 자체도 토큰을 쓰지는 않지만(호출 전에 막힌다) 컨슈머 슬롯을 소모한다.
 *
 * <p>그래서 이 경로만 예외를 삼킨다 — 삼키는 대가로 <b>실패를 반드시 기록</b>한다. 기록하지 않고 삼키면
 * 뼈대가 생성 중 상태에 영원히 남고, 사용자는 완료 알림을 기다리며 원인을 알 수 없다.
 */
final class BudgetStopReporter {

    private static final Logger log = LoggerFactory.getLogger(BudgetStopReporter.class);

    private BudgetStopReporter() {}

    static void report(AdvanceGenerationStageUseCase advance, Long skeletonId, BudgetExceededException exception) {
        log.error("[예산중단] skeletonId={} 재시도 없이 실패로 확정합니다. {}", skeletonId, exception.getMessage());
        advance.failed(skeletonId, "예산 상한 초과: " + exception.getMessage());
    }
}
