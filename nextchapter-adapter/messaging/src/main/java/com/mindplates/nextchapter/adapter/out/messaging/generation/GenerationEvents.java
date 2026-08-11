package com.mindplates.nextchapter.adapter.out.messaging.generation;

/**
 * 와이어 페이로드. 도메인 타입을 직접 보내지 않는다.
 *
 * <p>도메인 레코드를 그대로 직렬화하면 필드 이름을 바꾸는 리팩터링이 <b>토픽에 이미 쌓인 메시지</b>를
 * 읽을 수 없게 만든다. 그 실패는 배포 후 컨슈머가 첫 메시지를 집을 때 드러나고, 그때는 재처리할 방법이
 * 마땅치 않다.
 *
 * <p>Spring Kafka 의 타입 헤더도 쓰지 않는다 — 헤더에 Java 클래스 이름이 박혀서 클래스를 옮기는 것만으로
 * 옛 메시지가 죽는다. 페이로드는 문자열 JSON 이고 컨슈머가 자기 타입으로 읽는다.
 */
public final class GenerationEvents {

    private GenerationEvents() {}

    /** ① 그래프 생성 요청. */
    public record GraphRequested(Long skeletonId, Long topicId) {}

    /** ② 개요 생성 요청. */
    public record OutlinesRequested(Long skeletonId) {}

    /**
     * ③ 본문 생성 요청 — 챕터 하나에 대한 요청이다.
     *
     * <p>{@code chapterKey} 를 함께 싣는 이유는 로그와 실패 큐가 사람이 읽을 수 있는 식별자를 필요로
     * 하기 때문이다. ID 만 있으면 어느 챕터가 막혔는지 알기 위해 DB 를 다시 봐야 한다.
     */
    public record BodyRequested(Long skeletonId, Long chapterId, String chapterKey) {}
}
