package com.mindplates.nextchapter.domain.generation.model;

/** 배치 잡의 상태. */
public enum AiBatchStatus {
    /** 제출했고 벤더가 처리 중이다. 최대 24시간까지 이 상태일 수 있다. */
    SUBMITTED,

    /** 결과를 받아 반영했다. 항목별 실패는 개별 경로로 넘어갔다. */
    COMPLETED,

    /**
     * 배치 자체가 실패했다 — 제출이 거절됐거나 조회가 계속 실패한다.
     *
     * <p>항목 하나의 실패와 구분한다. 항목 실패는 그 챕터만 개별 경로로 넘기면 되지만, 배치 실패는 그 배치의
     * 전 챕터가 생성되지 않았다는 뜻이다.
     */
    FAILED;

    public boolean isTerminal() {
        return this != SUBMITTED;
    }
}
