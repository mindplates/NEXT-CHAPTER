package com.mindplates.nextchapter.adapter.out.persistence.catalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.mindplates.nextchapter.adapter.out.persistence.AbstractPostgresIT;
import com.mindplates.nextchapter.adapter.out.persistence.skeleton.SkeletonPersistenceAdapter;
import com.mindplates.nextchapter.domain.catalog.model.CatalogDomain;
import com.mindplates.nextchapter.domain.catalog.model.CatalogField;
import com.mindplates.nextchapter.domain.catalog.model.CatalogTopic;
import com.mindplates.nextchapter.domain.catalog.model.TopicAlias;
import com.mindplates.nextchapter.domain.skeleton.model.Skeleton;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;

/**
 * 카탈로그 어댑터를 실제 PostgreSQL 에 붙여 검증한다.
 *
 * <p>여기서 확인하려는 것은 매핑과 <b>제약</b>이다. 특히 {@code skeletons.topic_id} 의 UNIQUE 가
 * 실제로 걸려 있는지 — 그것이 "뼈대는 주제에 1:1"을 지키는 유일한 수단이고, 애플리케이션 검사만으로는
 * 동시 요청 두 건을 막지 못한다.
 *
 * <p>어댑터·매퍼를 {@code @Import} 로 넣는 이유는 {@code @DataJpaTest} 슬라이스가 엔티티와
 * 리포지터리만 스캔하고 {@code @Component} 는 올리지 않기 때문이다.
 */
@Import({
    CatalogPersistenceMapper.class,
    CatalogDomainPersistenceAdapter.class,
    CatalogFieldPersistenceAdapter.class,
    CatalogTopicPersistenceAdapter.class,
    TopicAliasPersistenceAdapter.class,
    SkeletonPersistenceAdapter.class
})
@DisplayName("카탈로그 어댑터 (실제 PostgreSQL)")
class CatalogPersistenceAdapterIT extends AbstractPostgresIT {

    @Autowired
    CatalogDomainPersistenceAdapter domains;

    @Autowired
    CatalogFieldPersistenceAdapter fields;

    @Autowired
    CatalogTopicPersistenceAdapter topics;

    @Autowired
    TopicAliasPersistenceAdapter aliases;

    @Autowired
    SkeletonPersistenceAdapter skeletons;

    @Autowired
    EntityManager entityManager;

    @Test
    @DisplayName("3단계를 저장하고 계층별로 조회한다")
    void savesAndReadsThreeLevels() {
        CatalogDomain domain = domains.save(CatalogDomain.create("computer-science", "컴퓨터과학", "설명", 0));
        CatalogField field = fields.save(CatalogField.create(domain.id(), "ai", "인공지능", null, 0));
        CatalogTopic topic = topics.save(CatalogTopic.create(field.id(), "machine-learning", "머신러닝", null, 0));

        assertThat(domain.id()).isNotNull();
        assertThat(domain.createdAt()).isNotNull();
        assertThat(fields.findByDomainId(domain.id()))
                .extracting(CatalogField::slug)
                .containsExactly("ai");
        assertThat(topics.findByFieldId(field.id()))
                .extracting(CatalogTopic::slug)
                .containsExactly("machine-learning");
        assertThat(topics.findBySlug("machine-learning")).contains(topic);
        assertThat(fields.countByDomainId(domain.id())).isEqualTo(1);
        assertThat(topics.countByFieldId(field.id())).isEqualTo(1);
    }

    @Test
    @DisplayName("주제 slug 는 전역 유일이다 — 뼈대의 부착점이 두 벌 생기면 신호가 갈린다")
    void topicSlugIsGloballyUnique() {
        CatalogDomain domain = domains.save(CatalogDomain.create("cs", "컴퓨터과학", null, 0));
        CatalogField first = fields.save(CatalogField.create(domain.id(), "ai", "인공지능", null, 0));
        CatalogField second = fields.save(CatalogField.create(domain.id(), "stats", "통계", null, 0));
        topics.save(CatalogTopic.create(first.id(), "machine-learning", "머신러닝", null, 0));

        assertThatThrownBy(() -> {
                    topics.save(CatalogTopic.create(second.id(), "machine-learning", "기계학습", null, 0));
                    entityManager.flush();
                })
                .isInstanceOf(Exception.class);
    }

    @Test
    @DisplayName("분야 slug 는 도메인 안에서만 유일하다 — 다른 도메인에는 같은 slug 를 쓸 수 있다")
    void fieldSlugIsUniquePerDomain() {
        CatalogDomain cs = domains.save(CatalogDomain.create("cs", "컴퓨터과학", null, 0));
        CatalogDomain math = domains.save(CatalogDomain.create("math", "수학", null, 0));

        fields.save(CatalogField.create(cs.id(), "statistics", "통계", null, 0));
        CatalogField underMath = fields.save(CatalogField.create(math.id(), "statistics", "통계", null, 0));

        assertThat(underMath.id()).isNotNull();
        assertThat(fields.findByDomainIdAndSlug(cs.id(), "statistics")).isPresent();
        assertThat(fields.findByDomainIdAndSlug(math.id(), "statistics")).isPresent();
    }

    @Test
    @DisplayName("별칭은 정규화된 표현으로 찾을 수 있고, 표기가 달라도 같은 행을 가리킨다")
    void aliasLookupIsNormalized() {
        CatalogTopic topic = seedTopic("machine-learning");
        aliases.save(TopicAlias.create(topic.id(), "기계 학습"));

        TopicAlias probe = TopicAlias.create(topic.id(), "기계학습");

        assertThat(aliases.findByNormalized(probe.normalized()))
                .isPresent()
                .get()
                .extracting(TopicAlias::alias)
                .isEqualTo("기계 학습");
    }

    @Test
    @DisplayName("같은 정규화 표현은 두 주제에 등록할 수 없다")
    void normalizedAliasIsGloballyUnique() {
        CatalogTopic first = seedTopic("machine-learning");
        CatalogTopic second = topics.save(CatalogTopic.create(first.fieldId(), "deep-learning", "딥러닝", null, 0));
        aliases.save(TopicAlias.create(first.id(), "기계학습"));

        assertThatThrownBy(() -> {
                    aliases.save(TopicAlias.create(second.id(), "기계 학습"));
                    entityManager.flush();
                })
                .isInstanceOf(Exception.class);
    }

    @Test
    @DisplayName("한 주제에 뼈대는 하나만 붙는다 — DB 제약으로 막힌다")
    void oneSkeletonPerTopic() {
        CatalogTopic topic = seedTopic("machine-learning");
        Skeleton saved = skeletons.save(Skeleton.start(topic.id()));

        assertThat(saved.id()).isNotNull();
        assertThat(skeletons.findByTopicId(topic.id())).isPresent();
        assertThat(skeletons.findTopicIdsWithSkeleton()).containsExactly(topic.id());

        assertThatThrownBy(() -> {
                    skeletons.save(Skeleton.start(topic.id()));
                    entityManager.flush();
                })
                .isInstanceOf(Exception.class);
    }

    private CatalogTopic seedTopic(String slug) {
        CatalogDomain domain = domains.save(CatalogDomain.create("cs", "컴퓨터과학", null, 0));
        CatalogField field = fields.save(CatalogField.create(domain.id(), "ai", "인공지능", null, 0));
        return topics.save(CatalogTopic.create(field.id(), slug, "머신러닝", null, 0));
    }
}
