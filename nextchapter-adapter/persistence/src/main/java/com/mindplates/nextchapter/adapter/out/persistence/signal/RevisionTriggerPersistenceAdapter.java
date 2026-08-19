package com.mindplates.nextchapter.adapter.out.persistence.signal;

import com.mindplates.nextchapter.application.signal.port.out.LoadRevisionTriggerPort;
import com.mindplates.nextchapter.application.signal.port.out.SaveRevisionTriggerPort;
import com.mindplates.nextchapter.domain.signal.model.RevisionTrigger;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 수정 트리거 어댑터.
 *
 * <p>{@code ON CONFLICT (chapter_id, chapter_version, block_id) DO NOTHING} 이 "같은 버전의 같은
 * 블록은 한 번만 트리거된다"의 실제 수단이다. 애플리케이션에서 존재 여부를 먼저 조회하고 나중에
 * 삽입하면 그 사이 다른 컨슈머 스레드가 끼어들 수 있다 — DB 제약이 유일하게 안전한 방법이다.
 */
@Component
public class RevisionTriggerPersistenceAdapter implements SaveRevisionTriggerPort, LoadRevisionTriggerPort {

    private final JdbcTemplate jdbcTemplate;

    public RevisionTriggerPersistenceAdapter(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    @Transactional
    public boolean saveIfAbsent(RevisionTrigger trigger) {
        int inserted = jdbcTemplate.update(
                """
                INSERT INTO revision_triggers
                       (chapter_id, chapter_version, block_id, question_count, attempt_count, wrong_count, triggered_at)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT (chapter_id, chapter_version, block_id) DO NOTHING
                """,
                trigger.chapterId(),
                trigger.chapterVersion(),
                trigger.blockId(),
                trigger.questionCount(),
                trigger.attemptCount(),
                trigger.wrongCount(),
                Timestamp.valueOf(trigger.triggeredAt()));
        return inserted > 0;
    }

    @Override
    @Transactional
    public void markProcessed(Long triggerId) {
        jdbcTemplate.update(
                "UPDATE revision_triggers SET processed_at = ? WHERE id = ?",
                Timestamp.valueOf(LocalDateTime.now()),
                triggerId);
    }

    /**
     * 잠그지 않으면 인스턴스 두 개가 같은 트리거로 제안을 두 번 만든다 — AI 호출 비용이 두 배가 되고,
     * 같은 문제에 대한 제안이 승인 대기열에 중복으로 오른다.
     */
    @Override
    @Transactional
    public List<RevisionTrigger> claimUnprocessed(int limit) {
        return jdbcTemplate.query(
                """
                SELECT id, chapter_id, chapter_version, block_id, question_count, attempt_count, wrong_count, triggered_at
                  FROM revision_triggers
                 WHERE processed_at IS NULL
                 ORDER BY id
                 LIMIT ?
                 FOR UPDATE SKIP LOCKED
                """,
                (rs, rowNum) -> new RevisionTrigger(
                        rs.getLong("id"),
                        rs.getLong("chapter_id"),
                        rs.getInt("chapter_version"),
                        rs.getString("block_id"),
                        rs.getLong("question_count"),
                        rs.getLong("attempt_count"),
                        rs.getLong("wrong_count"),
                        rs.getTimestamp("triggered_at").toLocalDateTime()),
                limit);
    }
}
