package com.mindplates.nextchapter.adapter.out.persistence.outbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.mindplates.nextchapter.adapter.out.persistence.AbstractPostgresIT;
import com.mindplates.nextchapter.domain.chapter.model.OutboxEvent;
import com.mindplates.nextchapter.domain.chapter.model.OutboxEventType;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * outbox 어댑터를 실제 PostgreSQL 에 붙여 검증한다.
 *
 * <p>여기서만 확인되는 것 — {@code MANDATORY} 가 트랜잭션 없는 호출을 실제로 막는지, {@code FOR UPDATE
 * SKIP LOCKED} 로 집어 온 행이 발행 표시 후 다시 잡히지 않는지, 그리고 뼈대 단위 미발행 건수가 published
 * 전환 조건에 쓸 수 있는지.
 *
 * <p>{@code MANDATORY} 검증이 특히 중요하다. 트랜잭션 없이 호출되면 outbox 행이 본문과 다른 커밋에 들어가
 * 두 단계 쓰기가 되는데, 그 상태는 정상 동작처럼 보이고 프로세스가 사이에서 죽을 때만 불일치가 드러난다.
 */
@Import(OutboxPersistenceAdapter.class)
@DisplayName("outbox 어댑터 (실제 PostgreSQL)")
class OutboxPersistenceAdapterIT extends AbstractPostgresIT {

    @Autowired
    OutboxPersistenceAdapter outbox;

    @Test
    @DisplayName("이벤트를 남기고 미발행으로 집어 온다")
    void appendsAndClaims() {
        outbox.append(chapterEvent(5L, 100L, 1));

        List<OutboxEvent> claimed = outbox.claimUnpublished(10);

        assertThat(claimed).singleElement().satisfies(event -> {
            assertThat(event.eventType()).isEqualTo(OutboxEventType.CHAPTER_PERSISTED);
            assertThat(event.idempotencyKey()).isEqualTo("chapter:100:v1");
            assertThat(event.partitionKey()).isEqualTo("5");
            assertThat(event.isPublished()).isFalse();
            assertThat(event.payload()).contains("\"chapterId\"");
        });
    }

    @Test
    @DisplayName("발행 표시된 이벤트는 다시 집히지 않는다")
    void publishedIsNotReclaimed() {
        OutboxEvent saved = outbox.append(chapterEvent(5L, 100L, 1));

        outbox.markPublished(saved.id());

        assertThat(outbox.claimUnpublished(10)).isEmpty();
    }

    @Test
    @DisplayName("ID 순으로 집어 온다 — 파티션 키 안에서 순서가 유지돼야 한다")
    void claimsInIdOrder() {
        outbox.append(chapterEvent(5L, 100L, 1));
        outbox.append(chapterEvent(5L, 101L, 1));
        outbox.append(chapterEvent(5L, 102L, 1));

        assertThat(outbox.claimUnpublished(2))
                .extracting(OutboxEvent::aggregateId)
                .containsExactly("100", "101");
    }

    /** 파티션 키가 뼈대 ID 이므로 이 조회가 곧 "이 뼈대의 반영이 끝났는가"다. */
    @Test
    @DisplayName("뼈대 단위 미발행 건수를 센다 — published 전환 조건의 재료")
    void countsPendingPerSkeleton() {
        OutboxEvent first = outbox.append(chapterEvent(5L, 100L, 1));
        outbox.append(chapterEvent(5L, 101L, 1));
        outbox.append(chapterEvent(9L, 200L, 1));

        assertThat(outbox.countPendingBySkeletonId(5L)).isEqualTo(2);
        assertThat(outbox.countPendingBySkeletonId(9L)).isEqualTo(1);

        outbox.markPublished(first.id());

        assertThat(outbox.countPendingBySkeletonId(5L)).isEqualTo(1);
    }

    @Test
    @DisplayName("그래프 이벤트도 같은 테이블에 남는다")
    void storesGraphEvent() {
        outbox.append(OutboxEvent.chapterGraphPersisted(5L, 2, "{\"skeletonId\":5,\"edges\":[]}"));

        assertThat(outbox.claimUnpublished(10)).singleElement().satisfies(event -> assertThat(event.eventType())
                .isEqualTo(OutboxEventType.CHAPTER_GRAPH_PERSISTED));
    }

    /**
     * 트랜잭션 없이 호출되면 즉시 예외여야 한다. 허용하면 outbox 행이 본문과 다른 커밋에 들어가고, 그
     * 상태는 프로세스가 사이에서 죽을 때만 드러난다.
     */
    @Test
    @DisplayName("트랜잭션 없이 append 하면 거절한다")
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void appendRequiresTransaction() {
        assertThatThrownBy(() -> outbox.append(chapterEvent(5L, 100L, 1)))
                .isInstanceOf(org.springframework.transaction.IllegalTransactionStateException.class);
    }

    private static OutboxEvent chapterEvent(Long skeletonId, Long chapterId, int version) {
        return OutboxEvent.chapterPersisted(
                skeletonId,
                chapterId,
                version,
                "{\"skeletonId\":" + skeletonId + ",\"chapterId\":" + chapterId + ",\"chapterKey\":\"k\","
                        + "\"title\":\"t\",\"version\":" + version + "}");
    }
}
