package com.mindplates.nextchapter.adapter.out.persistence.chapter;

import com.mindplates.nextchapter.application.chapter.port.out.LoadOutlineFindingPort;
import com.mindplates.nextchapter.application.chapter.port.out.SaveOutlineFindingPort;
import com.mindplates.nextchapter.domain.chapter.model.OutlineFinding;
import com.mindplates.nextchapter.domain.chapter.model.OutlineFindingType;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** 소견도 JDBC 다 — 개요와 같은 이유이고, 두 어댑터가 같은 테이블 묶음을 다룬다. */
@Component
@RequiredArgsConstructor
public class OutlineFindingPersistenceAdapter implements LoadOutlineFindingPort, SaveOutlineFindingPort {

    private static final RowMapper<OutlineFinding> FINDING_MAPPER = (rs, rowNum) -> new OutlineFinding(
            rs.getLong("id"),
            rs.getLong("skeleton_id"),
            OutlineFindingType.valueOf(rs.getString("finding_type")),
            splitKeys(rs.getString("chapter_keys")),
            rs.getString("detail"),
            rs.getTimestamp("created_at").toLocalDateTime());

    private final JdbcTemplate jdbcTemplate;

    @Override
    @Transactional(readOnly = true)
    public List<OutlineFinding> findBySkeletonId(Long skeletonId) {
        return jdbcTemplate.query(
                """
                SELECT id, skeleton_id, finding_type, chapter_keys, detail, created_at
                  FROM chapter_outline_findings
                 WHERE skeleton_id = ?
                 ORDER BY id
                """,
                FINDING_MAPPER,
                skeletonId);
    }

    @Override
    @Transactional
    public List<OutlineFinding> saveAll(List<OutlineFinding> findings) {
        Timestamp now = Timestamp.valueOf(LocalDateTime.now());
        for (OutlineFinding finding : findings) {
            jdbcTemplate.update(
                    """
                    INSERT INTO chapter_outline_findings
                           (skeleton_id, finding_type, chapter_keys, detail, created_at)
                    VALUES (?, ?, ?, ?, ?)
                    """,
                    finding.skeletonId(),
                    finding.findingType().name(),
                    finding.chapterKeys().isEmpty() ? null : String.join(",", finding.chapterKeys()),
                    finding.detail(),
                    now);
        }
        return findings.isEmpty() ? List.of() : findBySkeletonId(findings.get(0).skeletonId());
    }

    @Override
    @Transactional
    public void deleteBySkeletonId(Long skeletonId) {
        jdbcTemplate.update("DELETE FROM chapter_outline_findings WHERE skeleton_id = ?", skeletonId);
    }

    private static List<String> splitKeys(String joined) {
        if (joined == null || joined.isBlank()) {
            return List.of();
        }
        return Arrays.stream(joined.split(",")).map(String::trim).toList();
    }
}
