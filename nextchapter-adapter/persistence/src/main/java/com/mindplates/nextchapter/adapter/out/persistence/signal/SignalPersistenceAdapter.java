package com.mindplates.nextchapter.adapter.out.persistence.signal;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mindplates.nextchapter.application.signal.port.out.LoadSignalPort;
import com.mindplates.nextchapter.application.signal.port.out.SaveSignalPort;
import com.mindplates.nextchapter.common.exception.InvalidOperationException;
import com.mindplates.nextchapter.domain.chapter.model.DeliveryFormat;
import com.mindplates.nextchapter.domain.signal.model.Signal;
import com.mindplates.nextchapter.domain.signal.model.SignalType;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 신호 어댑터.
 *
 * <p>JPA 가 아니라 {@link JdbcTemplate} 를 쓴다. 이 테이블은 <b>append-only 이고 동시에 outbox</b>여서
 * 필요한 것이 두 가지뿐인데, 그중 하나가 {@code FOR UPDATE SKIP LOCKED} 다 — JPA 로는 그 쿼리를 표현할 수
 * 없고, 엔티티를 두면 "갱신 가능한 것처럼 보이는" 타입이 하나 생긴다.
 */
@Component
public class SignalPersistenceAdapter implements SaveSignalPort, LoadSignalPort {

    private static final TypeReference<Map<String, Object>> PAYLOAD_TYPE = new TypeReference<>() {};

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    private final RowMapper<Signal> signalMapper;

    public SignalPersistenceAdapter(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
        // 자체 매퍼를 쓴다. 웹 어댑터의 매퍼 설정이 바뀌면 이미 저장된 페이로드의 해석이 함께
        // 바뀌는데, 그 변화는 배포 후 집계에서만 드러난다.
        this.objectMapper = new ObjectMapper();
        this.signalMapper = (rs, rowNum) -> new Signal(
                rs.getLong("id"),
                rs.getLong("user_id"),
                rs.getLong("chapter_id"),
                rs.getInt("chapter_version"),
                rs.getString("block_id"),
                DeliveryFormat.valueOf(rs.getString("format")),
                SignalType.valueOf(rs.getString("type")),
                readPayload(rs.getString("payload")),
                rs.getTimestamp("occurred_at").toLocalDateTime(),
                rs.getTimestamp("published_at") == null
                        ? null
                        : rs.getTimestamp("published_at").toLocalDateTime());
    }

    @Override
    @Transactional
    public Signal save(Signal signal) {
        LocalDateTime occurredAt = signal.occurredAt();
        Long id = jdbcTemplate.queryForObject(
                """
                INSERT INTO signals
                       (user_id, chapter_id, chapter_version, block_id, format, type, payload, occurred_at, created_at)
                VALUES (?, ?, ?, ?, ?, ?, ?::jsonb, ?, ?)
                RETURNING id
                """,
                Long.class,
                signal.userId(),
                signal.chapterId(),
                signal.chapterVersion(),
                signal.blockId(),
                signal.format().name(),
                signal.type().name(),
                writePayload(signal.payload()),
                Timestamp.valueOf(occurredAt),
                Timestamp.valueOf(LocalDateTime.now()));
        return new Signal(
                id,
                signal.userId(),
                signal.chapterId(),
                signal.chapterVersion(),
                signal.blockId(),
                signal.format(),
                signal.type(),
                signal.payload(),
                occurredAt,
                null);
    }

    @Override
    @Transactional
    public void markPublished(Long signalId) {
        jdbcTemplate.update(
                "UPDATE signals SET published_at = ? WHERE id = ?", Timestamp.valueOf(LocalDateTime.now()), signalId);
    }

    /**
     * 잠그지 않으면 인스턴스 두 개가 같은 신호를 발행해 집계가 두 번 센다. 잠그되 건너뛰지 않으면 한쪽이
     * 다른 쪽을 기다려 처리량이 1로 떨어진다 — 둘을 같이 쓰는 것이 요점이다.
     */
    @Override
    @Transactional
    public List<Signal> claimUnpublished(int limit) {
        return jdbcTemplate.query(
                """
                SELECT id, user_id, chapter_id, chapter_version, block_id, format, type,
                       payload::text AS payload, occurred_at, published_at
                  FROM signals
                 WHERE published_at IS NULL
                 ORDER BY id
                 LIMIT ?
                 FOR UPDATE SKIP LOCKED
                """,
                signalMapper,
                limit);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Signal> findByUserAndChapter(Long userId, Long chapterId) {
        return jdbcTemplate.query(
                """
                SELECT id, user_id, chapter_id, chapter_version, block_id, format, type,
                       payload::text AS payload, occurred_at, published_at
                  FROM signals
                 WHERE user_id = ? AND chapter_id = ?
                 ORDER BY id
                """,
                signalMapper,
                userId,
                chapterId);
    }

    private Map<String, Object> readPayload(String json) {
        try {
            return objectMapper.readValue(json, PAYLOAD_TYPE);
        } catch (Exception e) {
            // 신호 페이로드를 읽을 수 없으면 그 행은 집계에 쓸 수 없다. 조용히 빈 값으로 넘기면
            // 오답률이 근거 없이 계산된다.
            throw new InvalidOperationException(
                    "저장된 신호 페이로드를 읽지 못했습니다: " + e.getClass().getSimpleName());
        }
    }

    private String writePayload(Map<String, Object> payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (Exception e) {
            throw new InvalidOperationException(
                    "신호 페이로드를 저장 형식으로 변환하지 못했습니다: " + e.getClass().getSimpleName());
        }
    }
}
