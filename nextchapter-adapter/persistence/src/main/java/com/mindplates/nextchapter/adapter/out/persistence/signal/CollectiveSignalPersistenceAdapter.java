package com.mindplates.nextchapter.adapter.out.persistence.signal;

import com.mindplates.nextchapter.application.signal.port.out.LoadCollectiveSignalAggregatePort;
import com.mindplates.nextchapter.application.signal.port.out.SaveCollectiveSignalEventPort;
import com.mindplates.nextchapter.domain.signal.model.BlockSignalAggregate;
import com.mindplates.nextchapter.domain.signal.model.CollectiveSignalEvent;
import com.mindplates.nextchapter.domain.signal.model.SignalType;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 집단 신호 집계 원장 어댑터.
 *
 * <p>{@code ON CONFLICT (signal_id) DO NOTHING} 이 멱등성의 전부다 — 몇 번을 다시 받아도 원장에는 한
 * 줄만 남는다. 집계는 별도 카운터 테이블 없이 이 원장을 그때그때 {@code FILTER} 로 센다.
 */
@Component
public class CollectiveSignalPersistenceAdapter
        implements SaveCollectiveSignalEventPort, LoadCollectiveSignalAggregatePort {

    private final JdbcTemplate jdbcTemplate;

    public CollectiveSignalPersistenceAdapter(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    @Transactional
    public boolean saveIfAbsent(CollectiveSignalEvent event) {
        int inserted = jdbcTemplate.update(
                """
                INSERT INTO collective_signal_events
                       (signal_id, chapter_id, chapter_version, block_id, format, type, correct, occurred_at, consumed_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT (signal_id) DO NOTHING
                """,
                event.signalId(),
                event.chapterId(),
                event.chapterVersion(),
                event.blockId(),
                event.format().name(),
                event.type().name(),
                event.correct(),
                Timestamp.valueOf(event.occurredAt()),
                Timestamp.valueOf(LocalDateTime.now()));
        return inserted > 0;
    }

    @Override
    @Transactional(readOnly = true)
    public BlockSignalAggregate aggregate(Long chapterId, int chapterVersion, String blockId) {
        return jdbcTemplate.queryForObject(
                """
                SELECT
                    COUNT(*) FILTER (WHERE type = ?)                     AS question_count,
                    COUNT(*) FILTER (WHERE type = ?)                     AS attempt_count,
                    COUNT(*) FILTER (WHERE type = ? AND correct = FALSE) AS wrong_count
                  FROM collective_signal_events
                 WHERE chapter_id = ? AND chapter_version = ? AND block_id = ?
                """,
                (rs, rowNum) -> new BlockSignalAggregate(
                        chapterId,
                        chapterVersion,
                        blockId,
                        rs.getLong("question_count"),
                        rs.getLong("attempt_count"),
                        rs.getLong("wrong_count")),
                SignalType.QUESTION.name(),
                SignalType.QUIZ_ANSWER.name(),
                SignalType.QUIZ_ANSWER.name(),
                chapterId,
                chapterVersion,
                blockId);
    }
}
