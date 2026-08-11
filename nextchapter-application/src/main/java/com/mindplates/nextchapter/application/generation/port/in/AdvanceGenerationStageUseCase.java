package com.mindplates.nextchapter.application.generation.port.in;

/**
 * 단계 완료를 기록하고 다음 단계를 발행한다 — 토픽 체인의 이음새.
 *
 * <p>각 단계 컨슈머가 자기 일을 끝낸 뒤 이 경로를 지난다. 상태 전이와 다음 이벤트 발행을 한 곳에
 * 모으는 이유는 둘이 어긋나면 파이프라인이 조용히 멈추기 때문이다 — 상태만 올라가면 아무도 다음
 * 단계를 시작하지 않고, 이벤트만 나가면 상태가 뒤처져 진행률이 거짓이 된다.
 */
public interface AdvanceGenerationStageUseCase {

    /** ① 그래프 생성 완료 → ② 개요 생성 요청. */
    void graphCompleted(Long skeletonId);

    /** ② 개요 생성 완료 → 챕터 수만큼 ③ 본문 생성 요청. */
    void outlinesCompleted(Long skeletonId);

    /**
     * ③ 본문 생성 완료 보고. <b>전 챕터가 끝났을 때만</b> ④ 로 넘어간다.
     *
     * <p>챕터 하나가 끝날 때마다 부르는 경로이므로 여기서 완료 판정을 한다 — 별도 스케줄러로 폴링하면
     * 마지막 챕터와 판정 사이에 지연이 생기고, 그 지연은 뼈대 수만큼 쌓인다.
     */
    void chapterBodyCompleted(Long skeletonId);

    /** 재시도를 소진한 단계 실패. 뼈대는 published 로 넘어가지 않는다. */
    void failed(Long skeletonId, String reason);
}
