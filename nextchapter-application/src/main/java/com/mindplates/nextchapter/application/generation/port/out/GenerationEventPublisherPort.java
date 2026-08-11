package com.mindplates.nextchapter.application.generation.port.out;

/**
 * 생성 파이프라인 단계 전환 이벤트 발행.
 *
 * <p>메서드를 단계별로 나눈 이유는 <b>메시지 키가 단계마다 다르기</b> 때문이다. 그래프·개요는 뼈대
 * 단위라 뼈대 ID 가 키지만, 본문은 챕터 단위라 챕터 ID 가 키다. 하나의 {@code publish(event)} 로
 * 묶으면 키 선택이 어댑터 안쪽 분기로 숨고, 그 분기가 틀리면 병렬도가 조용히 1이 된다.
 */
public interface GenerationEventPublisherPort {

    /** ① 그래프 생성 요청. */
    void requestGraph(Long skeletonId, Long topicId);

    /** ② 개요 생성 요청. */
    void requestOutlines(Long skeletonId);

    /** ③ 본문 생성 요청 — 챕터 수만큼 발행된다. */
    void requestBody(Long skeletonId, Long chapterId, String chapterKey);
}
