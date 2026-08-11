package com.mindplates.nextchapter.application.generation.port.in;

/**
 * ③ 본문 생성을 배치 한 건으로 제출한다.
 *
 * <p>비동기 일괄 생성이므로 배치 조건에 부합한다 — 전 토큰 50% 할인. 비동기 생성 결정이 그대로 비용
 * 절반으로 돌아온다.
 */
public interface SubmitChapterBodyBatchUseCase {

    /**
     * @return 제출했으면 true. <b>false 면 호출부가 개별 호출 경로로 가야 한다</b> — 배치가 꺼져 있거나,
     *     단계에 지정된 벤더가 배치를 지원하지 않거나, 제출할 챕터가 없는 경우다
     */
    boolean submitBodies(Long skeletonId);
}
