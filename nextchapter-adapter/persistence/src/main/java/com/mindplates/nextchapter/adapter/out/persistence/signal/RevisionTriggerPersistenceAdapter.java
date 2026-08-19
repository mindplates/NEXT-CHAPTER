package com.mindplates.nextchapter.adapter.out.persistence.signal;

import com.mindplates.nextchapter.application.signal.port.out.SaveRevisionTriggerPort;
import com.mindplates.nextchapter.domain.signal.model.RevisionTrigger;
import java.sql.Timestamp;
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
public class RevisionTriggerPersistenceAdapter implements SaveRevisionTriggerPort {

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
}
