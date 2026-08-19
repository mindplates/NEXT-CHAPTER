package com.mindplates.nextchapter.domain.signal.model;

/**
 * 블록 하나에 모인 집단 신호 요약. P5.2 임계치 검사가 이 값을 읽는다.
 *
 * <p>딜리버리 형태를 나누지 않는다 — 형태별로 쪼개면 초기 규모에서 어느 쪽도 임계치를 못 넘는다. 형태별
 * 분해는 수정안의 근거(P5.3)에서만 만든다.
 */
public record BlockSignalAggregate(
        Long chapterId, int chapterVersion, String blockId, long questionCount, long attemptCount, long wrongCount) {

    public double wrongRate() {
        return attemptCount == 0 ? 0.0 : (double) wrongCount / attemptCount;
    }
}
