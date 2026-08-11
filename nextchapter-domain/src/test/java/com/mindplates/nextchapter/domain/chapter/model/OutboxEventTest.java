package com.mindplates.nextchapter.domain.chapter.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 멱등 키와 파티션 키가 이 타입의 전부다.
 *
 * <p>멱등 키에 버전이 들어가야 재발행이 옛 상태로 되돌리지 않고, 파티션 키가 뼈대 ID 여야 간선 이벤트가
 * 노드 이벤트보다 먼저 도착하지 않는다. 둘 다 틀려도 에러가 나지 않고 그래프만 조용히 어긋난다.
 */
@DisplayName("outbox 이벤트")
class OutboxEventTest {

    @Test
    @DisplayName("챕터 이벤트의 멱등 키에 버전이 들어간다")
    void chapterKeyCarriesVersion() {
        OutboxEvent event = OutboxEvent.chapterPersisted(5L, 100L, 3, "{}");

        assertThat(event.idempotencyKey()).isEqualTo("chapter:100:v3");
        assertThat(event.eventType()).isEqualTo(OutboxEventType.CHAPTER_PERSISTED);
        assertThat(event.aggregateId()).isEqualTo("100");
    }

    /** 본문 생성이 병렬도를 위해 챕터 ID 를 키로 쓴 것과 반대다 — 여기서는 순서가 중요하다. */
    @Test
    @DisplayName("파티션 키는 뼈대 ID 다 — 챕터 ID 가 아니다")
    void partitionKeyIsSkeleton() {
        assertThat(OutboxEvent.chapterPersisted(5L, 100L, 1, "{}").partitionKey())
                .isEqualTo("5");
        assertThat(OutboxEvent.chapterGraphPersisted(5L, 2, "{}").partitionKey())
                .isEqualTo("5");
    }

    @Test
    @DisplayName("그래프 이벤트는 뼈대를 집합체로 갖는다")
    void graphEventAggregate() {
        OutboxEvent event = OutboxEvent.chapterGraphPersisted(5L, 7, "{}");

        assertThat(event.eventType()).isEqualTo(OutboxEventType.CHAPTER_GRAPH_PERSISTED);
        assertThat(event.aggregateId()).isEqualTo("5");
        assertThat(event.idempotencyKey()).isEqualTo("skeleton:5:graph:7");
    }

    @Test
    @DisplayName("발행 시각이 없으면 미발행이다")
    void publishedFlagFollowsTimestamp() {
        OutboxEvent pending = OutboxEvent.chapterPersisted(5L, 100L, 1, "{}");

        assertThat(pending.isPublished()).isFalse();
        assertThat(new OutboxEvent(
                                1L,
                                "CHAPTER",
                                "100",
                                OutboxEventType.CHAPTER_PERSISTED,
                                "5",
                                "k",
                                "{}",
                                LocalDateTime.now(),
                                LocalDateTime.now())
                        .isPublished())
                .isTrue();
    }

    @Test
    @DisplayName("필수 값이 빠지면 만들 수 없다")
    void requiresEssentials() {
        assertThatThrownBy(() -> new OutboxEvent(
                        null, "CHAPTER", "100", OutboxEventType.CHAPTER_PERSISTED, "5", "k", "  ", null, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new OutboxEvent(null, "CHAPTER", "100", null, "5", "k", "{}", null, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new OutboxEvent(
                        null, "CHAPTER", "100", OutboxEventType.CHAPTER_PERSISTED, "  ", "k", "{}", null, null))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
