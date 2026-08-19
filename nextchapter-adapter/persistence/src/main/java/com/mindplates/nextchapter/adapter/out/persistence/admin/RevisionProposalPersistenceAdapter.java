package com.mindplates.nextchapter.adapter.out.persistence.admin;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mindplates.nextchapter.application.admin.port.out.LoadRevisionProposalPort;
import com.mindplates.nextchapter.application.admin.port.out.SaveRevisionProposalPort;
import com.mindplates.nextchapter.common.exception.InvalidOperationException;
import com.mindplates.nextchapter.domain.admin.model.RevisionProposal;
import com.mindplates.nextchapter.domain.admin.model.RevisionProposalStatus;
import com.mindplates.nextchapter.domain.chapter.model.BlockType;
import com.mindplates.nextchapter.domain.chapter.model.DeliveryFormat;
import com.mindplates.nextchapter.domain.chapter.model.ProposedBlock;
import com.mindplates.nextchapter.domain.signal.model.FormatBreakdown;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 수정안 어댑터.
 *
 * <p>{@code proposed_blocks}·{@code format_breakdown} 을 JSONB 로 둔다. 제안된 블록은 아직 ID 가
 * 확정되지 않은 임시 구조라 컬럼으로 펼칠 이유가 없고, 형태별 분해는 조회 조건이 되지 않는 부가
 * 정보다.
 */
@Component
public class RevisionProposalPersistenceAdapter implements SaveRevisionProposalPort, LoadRevisionProposalPort {

    private static final TypeReference<List<Map<String, Object>>> BLOCK_LIST_TYPE = new TypeReference<>() {};
    private static final TypeReference<List<Map<String, Object>>> BREAKDOWN_LIST_TYPE = new TypeReference<>() {};

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final RowMapper<RevisionProposal> mapper;

    public RevisionProposalPersistenceAdapter(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
        this.mapper = (rs, rowNum) -> new RevisionProposal(
                rs.getLong("id"),
                rs.getLong("trigger_id"),
                rs.getLong("chapter_id"),
                rs.getInt("chapter_version"),
                rs.getString("block_id"),
                rs.getString("rationale"),
                readBreakdown(rs.getString("format_breakdown")),
                readBlocks(rs.getString("proposed_blocks")),
                RevisionProposalStatus.valueOf(rs.getString("status")),
                rs.getString("verification_note"),
                rs.getTimestamp("created_at").toLocalDateTime(),
                rs.getTimestamp("decided_at") == null
                        ? null
                        : rs.getTimestamp("decided_at").toLocalDateTime(),
                rs.getString("decided_by"));
    }

    @Override
    @Transactional
    public RevisionProposal save(RevisionProposal proposal) {
        Long id = jdbcTemplate.queryForObject(
                """
                INSERT INTO revision_proposals
                       (trigger_id, chapter_id, chapter_version, block_id, rationale, format_breakdown,
                        proposed_blocks, status, verification_note, created_at, decided_at, decided_by)
                VALUES (?, ?, ?, ?, ?, ?::jsonb, ?::jsonb, ?, ?, ?, ?, ?)
                RETURNING id
                """,
                Long.class,
                proposal.triggerId(),
                proposal.chapterId(),
                proposal.chapterVersion(),
                proposal.blockId(),
                proposal.rationale(),
                writeBreakdown(proposal.formatBreakdown()),
                writeBlocks(proposal.proposedBlocks()),
                proposal.status().name(),
                proposal.verificationNote(),
                Timestamp.valueOf(LocalDateTime.now()),
                proposal.decidedAt() == null ? null : Timestamp.valueOf(proposal.decidedAt()),
                proposal.decidedBy());
        return findById(id).orElseThrow(() -> new IllegalStateException("방금 저장한 수정안을 다시 읽지 못했습니다. id=" + id));
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<RevisionProposal> findByTriggerId(Long triggerId) {
        return jdbcTemplate.query(selectSql() + " WHERE trigger_id = ?", mapper, triggerId).stream()
                .findFirst();
    }

    private Optional<RevisionProposal> findById(Long id) {
        return jdbcTemplate.query(selectSql() + " WHERE id = ?", mapper, id).stream()
                .findFirst();
    }

    private static String selectSql() {
        return """
                SELECT id, trigger_id, chapter_id, chapter_version, block_id, rationale,
                       format_breakdown::text AS format_breakdown, proposed_blocks::text AS proposed_blocks,
                       status, verification_note, created_at, decided_at, decided_by
                  FROM revision_proposals
                """;
    }

    private List<FormatBreakdown> readBreakdown(String json) {
        return readJson(json, BREAKDOWN_LIST_TYPE, "형태별 분해").stream()
                .map(entry -> new FormatBreakdown(
                        DeliveryFormat.valueOf((String) entry.get("format")),
                        ((Number) entry.get("attemptCount")).longValue(),
                        ((Number) entry.get("wrongCount")).longValue()))
                .toList();
    }

    private String writeBreakdown(List<FormatBreakdown> breakdown) {
        List<Map<String, Object>> serialized = breakdown.stream()
                .map(entry -> {
                    Map<String, Object> node = new LinkedHashMap<>();
                    node.put("format", entry.format().name());
                    node.put("attemptCount", entry.attemptCount());
                    node.put("wrongCount", entry.wrongCount());
                    return (Map<String, Object>) node;
                })
                .toList();
        return writeJson(serialized, "형태별 분해");
    }

    private List<ProposedBlock> readBlocks(String json) {
        return readJson(json, BLOCK_LIST_TYPE, "제안된 본문").stream()
                .map(entry -> new ProposedBlock(
                        (String) entry.get("rawId"),
                        BlockType.valueOf((String) entry.get("type")),
                        (String) entry.get("text"),
                        castAttributes(entry.get("attributes"))))
                .toList();
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> castAttributes(Object raw) {
        return raw == null ? Map.of() : (Map<String, Object>) raw;
    }

    private String writeBlocks(List<ProposedBlock> blocks) {
        List<Map<String, Object>> serialized = blocks.stream()
                .map(block -> {
                    Map<String, Object> node = new LinkedHashMap<>();
                    node.put("rawId", block.rawId());
                    node.put("type", block.type().name());
                    node.put("text", block.text());
                    node.put("attributes", block.attributes());
                    return (Map<String, Object>) node;
                })
                .toList();
        return writeJson(serialized, "제안된 본문");
    }

    private <T> T readJson(String json, TypeReference<T> type, String label) {
        try {
            return objectMapper.readValue(json, type);
        } catch (Exception e) {
            throw new InvalidOperationException(
                    "저장된 " + label + "을 읽지 못했습니다: " + e.getClass().getSimpleName());
        }
    }

    private String writeJson(Object value, String label) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            throw new InvalidOperationException(
                    label + "을 저장 형식으로 변환하지 못했습니다: " + e.getClass().getSimpleName());
        }
    }
}
