package com.mindplates.nextchapter.adapter.out.persistence.catalog;

import com.mindplates.nextchapter.application.catalog.port.out.LoadTopicEmbeddingPort;
import com.mindplates.nextchapter.application.catalog.port.out.SaveTopicEmbeddingPort;
import com.mindplates.nextchapter.application.catalog.view.TopicSimilarityView;
import com.mindplates.nextchapter.domain.catalog.model.TopicEmbedding;
import java.sql.Timestamp;
import java.util.List;
import java.util.Optional;
import java.util.StringJoiner;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 임베딩만 JPA 가 아니라 {@link JdbcTemplate} 을 쓴다.
 *
 * <p>Hibernate 의 벡터 매핑은 <b>차원이 고정된</b> 컬럼을 전제한다. 이 프로젝트는 임베딩 모델을 관리
 * 화면에서 교체할 수 있어야 하므로 차원이 고정값이 아니고, 그래서 컬럼도 {@code vector}(차원 미지정)다.
 * 엔티티로 매핑하려면 차원을 박아야 하고, 그건 모델 교체를 마이그레이션이 필요한 일로 되돌린다.
 *
 * <p>벡터를 문자열 리터럴로 넘기고 {@code ::vector} 로 캐스팅한다 — pgvector 의 텍스트 입력 형식이다.
 * 값이 우리가 만든 float 배열에서만 오므로 문자열 조립이 주입 경로가 되지 않는다(실제 파라미터는
 * 바인딩된다).
 */
@Component
@RequiredArgsConstructor
public class TopicEmbeddingPersistenceAdapter implements LoadTopicEmbeddingPort, SaveTopicEmbeddingPort {

    private static final RowMapper<TopicEmbedding> EMBEDDING_MAPPER = (rs, rowNum) -> new TopicEmbedding(
            rs.getLong("topic_id"),
            rs.getString("model"),
            rs.getInt("dimension"),
            rs.getString("source_text"),
            parseVector(rs.getString("embedding")),
            rs.getTimestamp("created_at").toLocalDateTime(),
            rs.getTimestamp("updated_at").toLocalDateTime());

    private static final RowMapper<TopicSimilarityView> SIMILARITY_MAPPER = (rs, rowNum) -> new TopicSimilarityView(
            rs.getLong("topic_id"),
            rs.getString("slug"),
            rs.getString("name"),
            rs.getLong("field_id"),
            rs.getString("field_name"),
            rs.getString("domain_name"),
            rs.getDouble("score"));

    private final JdbcTemplate jdbcTemplate;

    @Override
    @Transactional(readOnly = true)
    public Optional<TopicEmbedding> findByTopicId(Long topicId) {
        List<TopicEmbedding> found = jdbcTemplate.query(
                """
                SELECT topic_id, model, dimension, source_text, embedding::text AS embedding, created_at, updated_at
                  FROM catalog_topic_embeddings
                 WHERE topic_id = ?
                """,
                EMBEDDING_MAPPER,
                topicId);
        return found.stream().findFirst();
    }

    @Override
    @Transactional(readOnly = true)
    public long countByModel(String model) {
        Long count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM catalog_topic_embeddings WHERE model = ?", Long.class, model);
        return count == null ? 0 : count;
    }

    /**
     * 대상은 "벡터가 없는 주제"와 "다른 모델로 만든 벡터를 가진 주제"의 합집합이다. 두 경우를 한 쿼리로
     * 묶는 것이 중요하다 — 나누면 신규 주제가 재임베딩 대상에서 빠져 영원히 검색되지 않는다.
     */
    @Override
    @Transactional(readOnly = true)
    public List<Long> findTopicIdsNotEmbeddedWith(String model, int limit) {
        return jdbcTemplate.queryForList(
                """
                SELECT t.id
                  FROM catalog_topics t
                  LEFT JOIN catalog_topic_embeddings e ON e.topic_id = t.id
                 WHERE e.topic_id IS NULL OR e.model <> ?
                 ORDER BY t.id
                 LIMIT ?
                """,
                Long.class,
                model,
                limit);
    }

    @Override
    @Transactional(readOnly = true)
    public long countTopicsNotEmbeddedWith(String model) {
        Long count = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                  FROM catalog_topics t
                  LEFT JOIN catalog_topic_embeddings e ON e.topic_id = t.id
                 WHERE e.topic_id IS NULL OR e.model <> ?
                """,
                Long.class,
                model);
        return count == null ? 0 : count;
    }

    /**
     * {@code <=>} 는 pgvector 의 코사인 거리다. 유사도는 {@code 1 - 거리}.
     *
     * <p>{@code model} 조건이 이 쿼리에서 가장 중요한 줄이다. 빼면 옛 모델로 만든 벡터까지 섞여
     * 비교되고, 그 결과는 틀렸는데 에러가 없다.
     */
    @Override
    @Transactional(readOnly = true)
    public List<TopicSimilarityView> searchSimilar(float[] queryVector, String model, int limit, double minScore) {
        String vector = toVectorLiteral(queryVector);
        return jdbcTemplate.query(
                """
                SELECT t.id            AS topic_id,
                       t.slug          AS slug,
                       t.name          AS name,
                       f.id            AS field_id,
                       f.name          AS field_name,
                       d.name          AS domain_name,
                       1 - (e.embedding <=> ?::vector) AS score
                  FROM catalog_topic_embeddings e
                  JOIN catalog_topics t ON t.id = e.topic_id
                  JOIN catalog_fields f ON f.id = t.field_id
                  JOIN catalog_domains d ON d.id = f.domain_id
                 WHERE e.model = ?
                   AND 1 - (e.embedding <=> ?::vector) >= ?
                 ORDER BY e.embedding <=> ?::vector
                 LIMIT ?
                """,
                SIMILARITY_MAPPER,
                vector,
                model,
                vector,
                minScore,
                vector,
                limit);
    }

    @Override
    @Transactional
    public TopicEmbedding save(TopicEmbedding embedding) {
        jdbcTemplate.update(
                """
                INSERT INTO catalog_topic_embeddings
                       (topic_id, model, dimension, source_text, embedding, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?::vector, ?, ?)
                ON CONFLICT (topic_id) DO UPDATE
                   SET model       = EXCLUDED.model,
                       dimension   = EXCLUDED.dimension,
                       source_text = EXCLUDED.source_text,
                       embedding   = EXCLUDED.embedding,
                       updated_at  = EXCLUDED.updated_at
                """,
                embedding.topicId(),
                embedding.model(),
                embedding.dimension(),
                embedding.sourceText(),
                toVectorLiteral(embedding.vector()),
                now(),
                now());
        return findByTopicId(embedding.topicId()).orElseThrow();
    }

    @Override
    @Transactional
    public void deleteByTopicId(Long topicId) {
        jdbcTemplate.update("DELETE FROM catalog_topic_embeddings WHERE topic_id = ?", topicId);
    }

    private static Timestamp now() {
        return Timestamp.valueOf(java.time.LocalDateTime.now());
    }

    private static String toVectorLiteral(float[] vector) {
        StringJoiner joiner = new StringJoiner(",", "[", "]");
        for (float value : vector) {
            joiner.add(Float.toString(value));
        }
        return joiner.toString();
    }

    private static float[] parseVector(String literal) {
        String trimmed = literal.substring(1, literal.length() - 1);
        if (trimmed.isEmpty()) {
            return new float[0];
        }
        String[] parts = trimmed.split(",");
        float[] vector = new float[parts.length];
        for (int i = 0; i < parts.length; i++) {
            vector[i] = Float.parseFloat(parts[i]);
        }
        return vector;
    }
}
