package com.mindplates.nextchapter.domain.generation.model;

/**
 * 운영자 큐 항목의 처리 상태.
 *
 * <p>{@link #RETRIED} 를 {@link #RESOLVED} 와 구분하는 이유는 <b>재시도가 성공을 뜻하지 않기 때문이다.</b>
 * 다시 발행한 메시지가 또 실패하면 새 항목이 큐에 들어오고, 그때 앞의 항목이 "해결됨"으로 닫혀 있으면
 * 같은 원인이 몇 번 반복됐는지 알 수 없다.
 */
public enum GenerationFailureStatus {
    /** 아직 아무 조치도 하지 않았다. 큐 화면의 기본 목록이다. */
    OPEN,

    /** 운영자가 다시 발행했다. 결과는 아직 모른다. */
    RETRIED,

    /** 운영자가 닫았다 — 고쳤거나, 다시 돌릴 필요가 없다고 판단했다. */
    RESOLVED;

    public boolean isClosed() {
        return this == RESOLVED;
    }
}
