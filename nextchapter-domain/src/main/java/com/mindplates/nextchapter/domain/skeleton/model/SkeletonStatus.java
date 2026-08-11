package com.mindplates.nextchapter.domain.skeleton.model;

/**
 * 뼈대 생성 잡의 상태. 생성 파이프라인 4단계와 1:1 로 대응한다.
 *
 * <p>{@link #PUBLISHED} 는 <b>전 챕터 · 활성 전 형태가 성공했을 때만</b> 도달한다. 부분 공개를
 * 허용하면 그래프 탐색 · 신호 집계 · 수정 루프 전부에 "일부 챕터 없음" 분기가 생긴다 — 일괄
 * 생성을 택한 근거 자체가 "뼈대는 항상 완결 상태"였다.
 */
public enum SkeletonStatus {
    GENERATING_GRAPH,
    GENERATING_OUTLINES,
    GENERATING_BODIES,
    GENERATING_ASSETS,
    PUBLISHED,
    /** 재시도를 소진해 운영자 큐로 넘어간 상태. 뼈대는 공개되지 않는다. */
    FAILED;

    public boolean isTerminal() {
        return this == PUBLISHED || this == FAILED;
    }
}
