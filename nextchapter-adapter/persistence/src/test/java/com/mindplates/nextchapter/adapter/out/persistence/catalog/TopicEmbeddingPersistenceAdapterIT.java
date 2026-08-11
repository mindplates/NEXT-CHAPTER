package com.mindplates.nextchapter.adapter.out.persistence.catalog;

import static org.assertj.core.api.Assertions.assertThat;

import com.mindplates.nextchapter.adapter.out.persistence.AbstractPostgresIT;
import com.mindplates.nextchapter.application.catalog.view.TopicSimilarityView;
import com.mindplates.nextchapter.domain.catalog.model.CatalogDomain;
import com.mindplates.nextchapter.domain.catalog.model.CatalogField;
import com.mindplates.nextchapter.domain.catalog.model.CatalogTopic;
import com.mindplates.nextchapter.domain.catalog.model.TopicEmbedding;
import jakarta.persistence.EntityManager;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;

/**
 * 임베딩 어댑터를 실제 pgvector 에 붙여 검증한다.
 *
 * <p>여기서만 확인할 수 있는 것이 셋이다 — (1) 차원 미지정 {@code vector} 컬럼이 실제로 동작하는지,
 * (2) {@code <=>} 코사인 거리 정렬이 기대대로인지, (3) <b>모델 필터가 옛 벡터를 실제로 배제하는지</b>.
 * 세 번째가 가장 중요하다. 모델이 다른 벡터와의 비교는 값이 나오는데 의미가 없고, 에러도 나지 않는다.
 */
@Import({
    CatalogPersistenceMapper.class,
    CatalogDomainPersistenceAdapter.class,
    CatalogFieldPersistenceAdapter.class,
    CatalogTopicPersistenceAdapter.class,
    TopicEmbeddingPersistenceAdapter.class
})
@DisplayName("주제 임베딩 어댑터 (실제 pgvector)")
class TopicEmbeddingPersistenceAdapterIT extends AbstractPostgresIT {

    private static final String MODEL = "voyage-3-large";

    @Autowired
    CatalogDomainPersistenceAdapter domains;

    @Autowired
    CatalogFieldPersistenceAdapter fields;

    @Autowired
    CatalogTopicPersistenceAdapter topics;

    @Autowired
    TopicEmbeddingPersistenceAdapter embeddings;

    @Autowired
    EntityManager entityManager;

    private Long fieldId;

    @BeforeEach
    void seedCatalog() {
        CatalogDomain domain = domains.save(CatalogDomain.create("cs", "컴퓨터과학", null, 0));
        fieldId = fields.save(CatalogField.create(domain.id(), "ai", "인공지능", null, 0))
                .id();
    }

    @Test
    @DisplayName("벡터를 저장하고 그대로 읽어 온다 — 차원 미지정 컬럼도 왕복이 된다")
    void savesAndReadsVector() {
        Long topicId = topic("machine-learning", "머신러닝");
        embeddings.save(TopicEmbedding.of(topicId, MODEL, "머신러닝 기계학습", new float[] {1.0f, 0.0f, 0.0f}));

        assertThat(embeddings.findByTopicId(topicId)).isPresent().get().satisfies(embedding -> {
            assertThat(embedding.model()).isEqualTo(MODEL);
            assertThat(embedding.dimension()).isEqualTo(3);
            assertThat(embedding.sourceText()).isEqualTo("머신러닝 기계학습");
            assertThat(embedding.vector()).containsExactly(1.0f, 0.0f, 0.0f);
        });
    }

    @Test
    @DisplayName("주제당 하나다 — 다시 저장하면 덮어쓴다")
    void saveIsUpsert() {
        Long topicId = topic("machine-learning", "머신러닝");
        embeddings.save(TopicEmbedding.of(topicId, MODEL, "첫 텍스트", new float[] {1.0f, 0.0f}));

        embeddings.save(TopicEmbedding.of(topicId, "voyage-3.5", "둘째 텍스트", new float[] {0.0f, 1.0f}));

        assertThat(embeddings.findByTopicId(topicId)).isPresent().get().satisfies(embedding -> {
            assertThat(embedding.model()).isEqualTo("voyage-3.5");
            assertThat(embedding.vector()).containsExactly(0.0f, 1.0f);
        });
        assertThat(embeddings.countByModel(MODEL)).isZero();
    }

    @Test
    @DisplayName("코사인 거리 순으로 정렬되고 계층 이름이 함께 나온다")
    void ordersByCosineDistance() {
        Long ml = topic("machine-learning", "머신러닝");
        Long dl = topic("deep-learning", "딥러닝");
        embeddings.save(TopicEmbedding.of(ml, MODEL, "머신러닝", new float[] {1.0f, 0.0f, 0.0f}));
        embeddings.save(TopicEmbedding.of(dl, MODEL, "딥러닝", new float[] {0.0f, 1.0f, 0.0f}));

        List<TopicSimilarityView> result = embeddings.searchSimilar(new float[] {0.9f, 0.1f, 0.0f}, MODEL, 10, 0.0);

        assertThat(result).extracting(TopicSimilarityView::topicId).containsExactly(ml, dl);
        assertThat(result.get(0).score()).isGreaterThan(result.get(1).score());
        assertThat(result.get(0).name()).isEqualTo("머신러닝");
        assertThat(result.get(0).fieldName()).isEqualTo("인공지능");
        assertThat(result.get(0).domainName()).isEqualTo("컴퓨터과학");
    }

    @Test
    @DisplayName("컷오프 아래는 결과에서 빠진다")
    void appliesMinScore() {
        Long ml = topic("machine-learning", "머신러닝");
        embeddings.save(TopicEmbedding.of(ml, MODEL, "머신러닝", new float[] {1.0f, 0.0f}));

        // 직교 벡터의 코사인 유사도는 0 이다.
        assertThat(embeddings.searchSimilar(new float[] {0.0f, 1.0f}, MODEL, 10, 0.5))
                .isEmpty();
        assertThat(embeddings.searchSimilar(new float[] {1.0f, 0.0f}, MODEL, 10, 0.5))
                .hasSize(1);
    }

    @Test
    @DisplayName("다른 모델로 만든 벡터는 검색에서 배제된다 — 비교하면 값은 나오지만 의미가 없다")
    void filtersByModel() {
        Long ml = topic("machine-learning", "머신러닝");
        embeddings.save(TopicEmbedding.of(ml, "old-model", "머신러닝", new float[] {1.0f, 0.0f}));

        assertThat(embeddings.searchSimilar(new float[] {1.0f, 0.0f}, MODEL, 10, 0.0))
                .isEmpty();
        assertThat(embeddings.searchSimilar(new float[] {1.0f, 0.0f}, "old-model", 10, 0.0))
                .hasSize(1);
    }

    @Test
    @DisplayName("재임베딩 대상은 '벡터 없음'과 '다른 모델'의 합집합이다")
    void outdatedIncludesMissingAndStale() {
        Long withCurrent = topic("machine-learning", "머신러닝");
        Long withOld = topic("deep-learning", "딥러닝");
        Long withNone = topic("reinforcement-learning", "강화학습");
        embeddings.save(TopicEmbedding.of(withCurrent, MODEL, "머신러닝", new float[] {1.0f, 0.0f}));
        embeddings.save(TopicEmbedding.of(withOld, "old-model", "딥러닝", new float[] {0.0f, 1.0f}));

        assertThat(embeddings.countByModel(MODEL)).isEqualTo(1);
        assertThat(embeddings.countTopicsNotEmbeddedWith(MODEL)).isEqualTo(2);
        assertThat(embeddings.findTopicIdsNotEmbeddedWith(MODEL, 10)).containsExactlyInAnyOrder(withOld, withNone);
    }

    @Test
    @DisplayName("배치 크기만큼만 대상을 돌려준다 — 전량을 한 트랜잭션에 넣지 않는다")
    void limitsBatch() {
        topic("machine-learning", "머신러닝");
        topic("deep-learning", "딥러닝");
        topic("reinforcement-learning", "강화학습");

        assertThat(embeddings.findTopicIdsNotEmbeddedWith(MODEL, 2)).hasSize(2);
    }

    /**
     * {@code flush()} 가 필요한 이유가 이 테스트의 부수적인 교훈이다. 주제 삭제는 JPA 가, 임베딩 조회는
     * {@link org.springframework.jdbc.core.JdbcTemplate} 이 한다. JPA 의 쓰기는 flush 전까지 영속성
     * 컨텍스트에만 있어서 <b>같은 트랜잭션의 JDBC 조회에는 보이지 않는다.</b> 운영에서는 커밋이
     * flush 를 대신하지만, 한 트랜잭션 안에서 두 기술을 섞어 쓰는 코드를 쓸 때는 명시해야 한다.
     */
    @Test
    @DisplayName("주제를 지우면 임베딩도 함께 사라진다 (ON DELETE CASCADE)")
    void cascadesOnTopicDelete() {
        Long topicId = topic("machine-learning", "머신러닝");
        embeddings.save(TopicEmbedding.of(topicId, MODEL, "머신러닝", new float[] {1.0f, 0.0f}));

        topics.deleteById(topicId);
        entityManager.flush();

        assertThat(embeddings.findByTopicId(topicId)).isEmpty();
    }

    private Long topic(String slug, String name) {
        return topics.save(CatalogTopic.create(fieldId, slug, name, null, 0)).id();
    }
}
