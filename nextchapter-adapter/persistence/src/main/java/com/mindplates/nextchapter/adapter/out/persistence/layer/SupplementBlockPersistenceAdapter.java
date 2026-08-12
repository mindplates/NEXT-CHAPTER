package com.mindplates.nextchapter.adapter.out.persistence.layer;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mindplates.nextchapter.application.layer.port.out.LoadSupplementBlockPort;
import com.mindplates.nextchapter.application.layer.port.out.SaveSupplementBlockPort;
import com.mindplates.nextchapter.common.exception.InvalidOperationException;
import com.mindplates.nextchapter.domain.chapter.model.Block;
import com.mindplates.nextchapter.domain.chapter.model.BlockIds;
import com.mindplates.nextchapter.domain.chapter.model.BlockType;
import com.mindplates.nextchapter.domain.layer.model.SupplementBlock;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 보충 블록 어댑터.
 *
 * <p><b>블록 ID 를 저장하지 않고 PK 에서 만든다</b>({@code s{id}}). 순번을 계산해 발급하면 지운 뒤 다시
 * 만들 때 지워진 ID 가 되살아나고, 그러면 두 설명의 신호가 한 블록에 섞인다 — 그 오염은 에러 없이 일어난다.
 * BIGSERIAL 은 되돌아가지 않으므로 재사용이 구조적으로 불가능하다.
 */
@Component
public class SupplementBlockPersistenceAdapter implements SaveSupplementBlockPort, LoadSupplementBlockPort {

    private static final TypeReference<Map<String, Object>> ATTRIBUTES = new TypeReference<>() {};

    private static final String COLUMNS =
            "SELECT id, user_id, chapter_id, chapter_version, anchor_block_id, block_type,"
                    + " text, attributes::text AS attributes FROM layer_supplement_blocks";

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    private final RowMapper<SupplementBlock> mapper;

    public SupplementBlockPersistenceAdapter(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = new ObjectMapper();
        this.mapper = (rs, rowNum) -> new SupplementBlock(
                rs.getString("anchor_block_id"),
                new Block(
                        BlockIds.ofSupplement(rs.getLong("id")),
                        BlockType.valueOf(rs.getString("block_type")),
                        rs.getString("text"),
                        readAttributes(rs.getString("attributes"))));
    }

    @Override
    @Transactional
    public SupplementBlock create(NewSupplement supplement) {
        Long id = jdbcTemplate.queryForObject(
                """
                INSERT INTO layer_supplement_blocks
                       (user_id, chapter_id, chapter_version, anchor_block_id, block_type, text, attributes, created_at)
                VALUES (?, ?, ?, ?, ?, ?, ?::jsonb, ?)
                RETURNING id
                """,
                Long.class,
                supplement.userId(),
                supplement.chapterId(),
                supplement.chapterVersion(),
                supplement.anchorBlockId(),
                supplement.blockType().name(),
                supplement.text(),
                writeAttributes(supplement.attributes()),
                Timestamp.valueOf(LocalDateTime.now()));

        return new SupplementBlock(
                supplement.anchorBlockId(),
                new Block(
                        BlockIds.ofSupplement(id), supplement.blockType(), supplement.text(), supplement.attributes()));
    }

    /** 삽입 순서를 유지한다. 같은 앵커에 여러 개가 붙을 수 있게 되면 그 순서가 화면 순서가 된다. */
    @Override
    @Transactional(readOnly = true)
    public List<SupplementBlock> findByUserAndChapter(Long userId, Long chapterId) {
        return jdbcTemplate.query(
                COLUMNS + " WHERE user_id = ? AND chapter_id = ? ORDER BY id", mapper, userId, chapterId);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<SupplementBlock> findByAnchor(Long userId, Long chapterId, String anchorBlockId) {
        return jdbcTemplate
                .query(
                        COLUMNS + " WHERE user_id = ? AND chapter_id = ? AND anchor_block_id = ?",
                        mapper,
                        userId,
                        chapterId,
                        anchorBlockId)
                .stream()
                .findFirst();
    }

    private Map<String, Object> readAttributes(String json) {
        try {
            return json == null ? Map.of() : objectMapper.readValue(json, ATTRIBUTES);
        } catch (Exception e) {
            throw new InvalidOperationException(
                    "저장된 보충 블록 속성을 읽지 못했습니다: " + e.getClass().getSimpleName());
        }
    }

    private String writeAttributes(Map<String, Object> attributes) {
        try {
            return objectMapper.writeValueAsString(attributes == null ? Map.of() : attributes);
        } catch (Exception e) {
            throw new InvalidOperationException(
                    "보충 블록 속성을 저장 형식으로 변환하지 못했습니다: " + e.getClass().getSimpleName());
        }
    }
}
