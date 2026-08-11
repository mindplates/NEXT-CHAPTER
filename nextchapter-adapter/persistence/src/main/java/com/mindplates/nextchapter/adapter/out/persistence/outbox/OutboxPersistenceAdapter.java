package com.mindplates.nextchapter.adapter.out.persistence.outbox;

import com.mindplates.nextchapter.application.chapter.port.out.LoadOutboxEventPort;
import com.mindplates.nextchapter.application.chapter.port.out.SaveOutboxEventPort;
import com.mindplates.nextchapter.domain.chapter.model.OutboxEvent;
import com.mindplates.nextchapter.domain.chapter.model.OutboxEventType;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * outbox 어댑터.
 *
 * <p>{@code append} 에 {@link Propagation#MANDATORY} 를 걸었다. outbox 행은 본문·관계와 <b>같은 커밋</b>에
 * 들어가야 하고, 트랜잭션 없이 호출되면 그 보장이 조용히 깨진다 — 커밋은 성공하고 이벤트만 사라지거나,
 * 이벤트만 남고 본문이 롤백된다. MANDATORY 는 그 호출을 즉시 예외로 만든다.
 */
@Component
@RequiredArgsConstructor
public class OutboxPersistenceAdapter implements SaveOutboxEventPort, LoadOutboxEventPort {

    private static final RowMapper<OutboxEvent> EVENT_MAPPER = (rs, rowNum) -> new OutboxEvent(
            rs.getLong("id"),
            rs.getString("aggregate_type"),
            rs.getString("aggregate_id"),
            OutboxEventType.valueOf(rs.getString("event_type")),
            rs.getString("partition_key"),
            rs.getString("idempotency_key"),
            rs.getString("payload"),
            rs.getTimestamp("created_at").toLocalDateTime(),
            rs.getTimestamp("published_at") == null
                    ? null
                    : rs.getTimestamp("published_at").toLocalDateTime());

    private final JdbcTemplate jdbcTemplate;

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public OutboxEvent append(OutboxEvent event) {
        Long id = jdbcTemplate.queryForObject(
                """
                INSERT INTO outbox_events
                       (aggregate_type, aggregate_id, event_type, partition_key, idempotency_key, payload, created_at)
                VALUES (?, ?, ?, ?, ?, ?::jsonb, ?)
                RETURNING id
                """,
                Long.class,
                event.aggregateType(),
                event.aggregateId(),
                event.eventType().name(),
                event.partitionKey(),
                event.idempotencyKey(),
                event.payload(),
                Timestamp.valueOf(LocalDateTime.now()));
        return new OutboxEvent(
                id,
                event.aggregateType(),
                event.aggregateId(),
                event.eventType(),
                event.partitionKey(),
                event.idempotencyKey(),
                event.payload(),
                LocalDateTime.now(),
                null);
    }

    /**
     * {@code FOR UPDATE SKIP LOCKED} 로 집어 온다.
     *
     * <p>잠그지 않으면 인스턴스 두 개가 같은 이벤트를 발행하고, 잠그되 건너뛰지 않으면 한쪽이 다른 쪽을
     * 기다려 처리량이 1로 떨어진다. 둘을 같이 쓰는 것이 이 쿼리의 요점이다.
     */
    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public List<OutboxEvent> claimUnpublished(int limit) {
        return jdbcTemplate.query(
                """
                SELECT id, aggregate_type, aggregate_id, event_type, partition_key,
                       idempotency_key, payload::text AS payload, created_at, published_at
                  FROM outbox_events
                 WHERE published_at IS NULL
                 ORDER BY id
                 LIMIT ?
                 FOR UPDATE SKIP LOCKED
                """,
                EVENT_MAPPER,
                limit);
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void markPublished(Long eventId) {
        jdbcTemplate.update(
                "UPDATE outbox_events SET published_at = ? WHERE id = ?",
                Timestamp.valueOf(LocalDateTime.now()),
                eventId);
    }

    /** 파티션 키가 뼈대 ID 이므로 이 조회가 곧 "이 뼈대의 반영이 끝났는가"다. */
    @Override
    @Transactional(readOnly = true)
    public long countPendingBySkeletonId(Long skeletonId) {
        Long count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM outbox_events WHERE partition_key = ? AND published_at IS NULL",
                Long.class,
                String.valueOf(skeletonId));
        return count == null ? 0 : count;
    }
}
