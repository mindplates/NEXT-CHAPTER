package com.mindplates.nextchapter.adapter.out.persistence.chapter;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mindplates.nextchapter.application.chapter.port.out.LoadChapterOutlinePort;
import com.mindplates.nextchapter.application.chapter.port.out.SaveChapterOutlinePort;
import com.mindplates.nextchapter.common.exception.InvalidOperationException;
import com.mindplates.nextchapter.domain.chapter.model.ChapterOutline;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 개요는 {@link JdbcTemplate} 을 쓴다.
 *
 * <p>jsonb 배열 하나가 전부인 테이블이라 엔티티·리포지터리·매퍼 세 파일을 두는 것이 오히려 비용이다.
 * 임베딩 어댑터가 JDBC 를 쓴 이유(Hibernate 가 표현하지 못하는 타입)와는 다르고, 여기서는 단순함이
 * 이유다 — 이 어댑터가 하는 일은 upsert 와 두 가지 조회뿐이다.
 */
@Component
@RequiredArgsConstructor
public class ChapterOutlinePersistenceAdapter implements LoadChapterOutlinePort, SaveChapterOutlinePort {

    private static final TypeReference<List<String>> POINTS = new TypeReference<>() {};

    private final JdbcTemplate jdbcTemplate;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private final RowMapper<ChapterOutline> outlineMapper = (rs, rowNum) -> new ChapterOutline(
            rs.getLong("chapter_id"),
            readPoints(rs.getString("points")),
            rs.getTimestamp("updated_at").toLocalDateTime());

    @Override
    @Transactional(readOnly = true)
    public Optional<ChapterOutline> findByChapterId(Long chapterId) {
        return jdbcTemplate
                .query(
                        "SELECT chapter_id, points::text AS points, updated_at FROM chapter_outlines WHERE chapter_id = ?",
                        outlineMapper,
                        chapterId)
                .stream()
                .findFirst();
    }

    @Override
    @Transactional(readOnly = true)
    public List<ChapterOutline> findBySkeletonId(Long skeletonId) {
        return jdbcTemplate.query(
                """
                SELECT o.chapter_id, o.points::text AS points, o.updated_at
                  FROM chapter_outlines o
                  JOIN chapters c ON c.id = o.chapter_id
                 WHERE c.skeleton_id = ?
                 ORDER BY c.sort_order, c.id
                """,
                outlineMapper,
                skeletonId);
    }

    @Override
    @Transactional
    public List<ChapterOutline> saveAll(List<ChapterOutline> outlines) {
        Timestamp now = Timestamp.valueOf(LocalDateTime.now());
        for (ChapterOutline outline : outlines) {
            jdbcTemplate.update(
                    """
                    INSERT INTO chapter_outlines (chapter_id, points, created_at, updated_at)
                    VALUES (?, ?::jsonb, ?, ?)
                    ON CONFLICT (chapter_id) DO UPDATE
                       SET points = EXCLUDED.points, updated_at = EXCLUDED.updated_at
                    """,
                    outline.chapterId(),
                    writePoints(outline.points()),
                    now,
                    now);
        }
        return outlines.stream()
                .map(outline -> findByChapterId(outline.chapterId()).orElseThrow())
                .toList();
    }

    private String writePoints(List<String> points) {
        try {
            return objectMapper.writeValueAsString(points);
        } catch (Exception e) {
            throw new InvalidOperationException(
                    "개요를 저장 형식으로 변환하지 못했습니다: " + e.getClass().getSimpleName());
        }
    }

    private List<String> readPoints(String json) {
        try {
            return objectMapper.readValue(json, POINTS);
        } catch (Exception e) {
            throw new InvalidOperationException(
                    "저장된 개요를 읽지 못했습니다: " + e.getClass().getSimpleName());
        }
    }
}
