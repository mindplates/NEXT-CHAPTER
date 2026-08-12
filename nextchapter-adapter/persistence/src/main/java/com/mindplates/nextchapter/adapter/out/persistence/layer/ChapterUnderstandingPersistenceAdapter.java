package com.mindplates.nextchapter.adapter.out.persistence.layer;

import com.mindplates.nextchapter.application.layer.port.out.LoadChapterUnderstandingPort;
import com.mindplates.nextchapter.domain.layer.model.ChapterUnderstanding;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 이해도 집계. <b>신호 테이블을 그대로 접는다</b> — 집계 테이블을 두지 않았다.
 *
 * <p>이중 기록을 만들지 않기 위해서다. 신호가 원천이고 이해도는 그 투영이므로, 둘을 나란히 저장하면
 * 어긋날 수 있고 그 어긋남은 조용히 잘못된 추천으로 나타난다.
 *
 * <p>{@code payload->>'correct'} 를 문자열로 비교하는 이유는 jsonb 의 불리언이 텍스트로 나올 때
 * {@code "true"} 이기 때문이다. 진행률은 숫자로 캐스팅하며, 그 안전은 도메인이 보장한다 — 숫자가 아닌
 * 진행률은 저장되지 않는다.
 */
@Component
@RequiredArgsConstructor
public class ChapterUnderstandingPersistenceAdapter implements LoadChapterUnderstandingPort {

    private static final String AGGREGATE_COLUMNS =
            """
            SELECT chapter_id,
                   COUNT(*) FILTER (WHERE type = 'QUIZ_ANSWER')                             AS attempts,
                   COUNT(*) FILTER (WHERE type = 'QUIZ_ANSWER'
                                      AND payload ->> 'correct' = 'true')                   AS correct,
                   COUNT(*) FILTER (WHERE type = 'QUESTION')                                AS questions,
                   COALESCE(MAX((payload ->> 'percent')::numeric)
                            FILTER (WHERE type = 'PROGRESS'), 0)                            AS progress,
                   MAX(occurred_at)                                                         AS last_seen
              FROM signals
            """;

    private static final RowMapper<ChapterUnderstanding> MAPPER = (rs, rowNum) -> new ChapterUnderstanding(
            rs.getLong("chapter_id"),
            rs.getInt("attempts"),
            rs.getInt("correct"),
            rs.getInt("questions"),
            rs.getBigDecimal("progress").intValue(),
            rs.getTimestamp("last_seen") == null
                    ? null
                    : rs.getTimestamp("last_seen").toLocalDateTime());

    private final JdbcTemplate jdbcTemplate;

    /**
     * 뼈대의 챕터를 서브쿼리로 좁힌다. 챕터 ID 목록을 애플리케이션이 먼저 읽어 넘기면 왕복이 한 번 늘고,
     * 그 사이에 챕터가 추가되면 두 조회가 다른 집합을 본다.
     */
    @Override
    @Transactional(readOnly = true)
    public List<ChapterUnderstanding> summarizeBySkeleton(Long userId, Long skeletonId) {
        return jdbcTemplate.query(
                AGGREGATE_COLUMNS
                        + """
                         WHERE user_id = ?
                           AND chapter_id IN (SELECT id FROM chapters WHERE skeleton_id = ?)
                         GROUP BY chapter_id
                        """,
                MAPPER,
                userId,
                skeletonId);
    }

    /** 신호가 없으면 빈 이해도를 돌려준다 — 호출부가 null 을 다루지 않게 한다. */
    @Override
    @Transactional(readOnly = true)
    public ChapterUnderstanding findByChapter(Long userId, Long chapterId) {
        return jdbcTemplate
                .query(
                        AGGREGATE_COLUMNS + " WHERE user_id = ? AND chapter_id = ? GROUP BY chapter_id",
                        MAPPER,
                        userId,
                        chapterId)
                .stream()
                .findFirst()
                .orElseGet(() -> ChapterUnderstanding.none(chapterId));
    }
}
