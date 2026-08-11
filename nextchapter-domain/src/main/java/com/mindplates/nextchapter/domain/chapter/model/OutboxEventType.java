package com.mindplates.nextchapter.domain.chapter.model;

/**
 * outbox 이벤트 종류. 지금은 Neo4j 반영만 다룬다.
 *
 * <p>outbox 를 chapter 축에 둔 이유는 이벤트의 집합체가 챕터와 챕터 그래프뿐이기 때문이다. 다른 축이
 * 쓰기 시작하면 그때 옮긴다 — 쓰이지 않는 일반화를 미리 만들면 이름만 중립적이고 실제로는 챕터 전용인
 * 코드가 남는다.
 */
public enum OutboxEventType {
    /** 챕터 노드를 만들거나 갱신한다. 멱등 키는 챕터 ID + 버전. */
    CHAPTER_PERSISTED,

    /** 뼈대의 챕터 간 관계를 통째로 교체한다. 전량 교체이므로 그 자체로 멱등이다. */
    CHAPTER_GRAPH_PERSISTED
}
