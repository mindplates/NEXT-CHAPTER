package com.mindplates.nextchapter.domain.skeleton.model;

/**
 * 뼈대 생성 잡의 상태. 생성 파이프라인 4단계와 1:1 로 대응한다.
 *
 * <p>{@link #PUBLISHED} 는 <b>전 챕터 · 활성 전 형태가 성공했을 때만</b> 도달한다. 부분 공개를
 * 허용하면 그래프 탐색 · 신호 집계 · 수정 루프 전부에 "일부 챕터 없음" 분기가 생긴다 — 일괄
 * 생성을 택한 근거 자체가 "뼈대는 항상 완결 상태"였다.
 *
 * <p><b>{@code PUBLISHED} 는 되돌아가지 않는다.</b> 챕터를 수정하면 파생 자산을 다시 만들어야 하지만
 * 그 진행은 자산마다 자기 버전으로 추적되고, 그동안 준비된 것 중 최신본이 계속 서빙된다. 뼈대 상태를
 * {@code GENERATING_ASSETS} 로 되돌리면 "재생성 중에는 형태를 잠근다"는 뜻이 되어, 개선이 잦을수록
 * 서비스가 나빠지는 역전이 생긴다.
 */
public enum SkeletonStatus {
    GENERATING_GRAPH(0),
    GENERATING_OUTLINES(1),
    GENERATING_BODIES(2),
    GENERATING_ASSETS(3),
    PUBLISHED(4),

    /**
     * 재시도를 소진해 운영자 큐로 넘어간 상태. 뼈대는 공개되지 않는다.
     *
     * <p>순서 축에 두지 않는 이유는 실패가 단계가 아니기 때문이다 — 어느 단계에서든 들어올 수 있고,
     * 운영자가 그 단계부터 다시 시작시킨다.
     */
    FAILED(-1);

    private final int order;

    SkeletonStatus(int order) {
        this.order = order;
    }

    public boolean isTerminal() {
        return this == PUBLISHED || this == FAILED;
    }

    public boolean isGenerating() {
        return order >= 0 && this != PUBLISHED;
    }

    /**
     * 허용되는 전이인지.
     *
     * <ul>
     *   <li>생성 단계는 <b>한 칸씩 앞으로만</b> 간다 — 단계를 건너뛰면 개요 없이 본문을 쓰거나 본문
     *       없이 자산을 만들게 되고, 그 실패는 생성이 끝난 뒤에야 드러난다
     *   <li>어느 생성 단계에서든 {@link #FAILED} 로 갈 수 있다
     *   <li>{@code FAILED} 에서는 생성 단계로 되돌아갈 수 있다 — 운영자의 재시도 경로다
     *   <li>{@link #PUBLISHED} 에서는 어디로도 가지 않는다
     * </ul>
     */
    public boolean canTransitionTo(SkeletonStatus next) {
        if (next == null || next == this) {
            return false;
        }
        if (this == PUBLISHED) {
            return false;
        }
        if (next == FAILED) {
            return isGenerating();
        }
        if (this == FAILED) {
            return next.isGenerating();
        }
        return next.order == order + 1;
    }

    /**
     * 다음 생성 단계. {@link #PUBLISHED}·{@link #FAILED} 에는 다음이 없다.
     *
     * <p>종료 상태를 먼저 걸러내는 것이 중요하다. {@code FAILED} 의 order 가 -1 이라 순번 계산만으로는
     * "다음"이 {@code GENERATING_GRAPH} 로 나오는데, 그건 실패한 뼈대가 자동으로 처음부터 다시 도는
     * 뜻이 된다 — 재시작은 운영자의 판단이어야 하고, 자동으로 돌면 실패 원인을 그대로 안은 채 비용만
     * 다시 든다.
     */
    public SkeletonStatus next() {
        if (isTerminal()) {
            throw new IllegalStateException(this + " 다음 단계가 없습니다.");
        }
        for (SkeletonStatus candidate : values()) {
            if (candidate.order == order + 1) {
                return candidate;
            }
        }
        throw new IllegalStateException(this + " 다음 단계가 없습니다.");
    }
}
