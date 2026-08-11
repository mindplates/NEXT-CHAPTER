package com.mindplates.nextchapter.adapter.out.persistence.chapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.mindplates.nextchapter.adapter.out.persistence.AbstractPostgresIT;
import com.mindplates.nextchapter.adapter.out.persistence.catalog.CatalogDomainPersistenceAdapter;
import com.mindplates.nextchapter.adapter.out.persistence.catalog.CatalogFieldPersistenceAdapter;
import com.mindplates.nextchapter.adapter.out.persistence.catalog.CatalogPersistenceMapper;
import com.mindplates.nextchapter.adapter.out.persistence.catalog.CatalogTopicPersistenceAdapter;
import com.mindplates.nextchapter.adapter.out.persistence.skeleton.SkeletonPersistenceAdapter;
import com.mindplates.nextchapter.domain.catalog.model.CatalogDomain;
import com.mindplates.nextchapter.domain.catalog.model.CatalogField;
import com.mindplates.nextchapter.domain.catalog.model.CatalogTopic;
import com.mindplates.nextchapter.domain.chapter.model.Chapter;
import com.mindplates.nextchapter.domain.chapter.model.ChapterRelation;
import com.mindplates.nextchapter.domain.chapter.model.ChapterRelationType;
import com.mindplates.nextchapter.domain.skeleton.model.Skeleton;
import jakarta.persistence.EntityManager;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;

/**
 * 관계 어댑터를 실제 PostgreSQL 에 붙여 검증한다.
 *
 * <p>여기서만 확인되는 것은 <b>제약</b>이다 — 중복 간선과 자기 참조를 DB 가 실제로 막는지. 둘 다 애플리케이션
 * 검증을 지나온 뒤에도 동시 요청이나 재생성 경로로 들어올 수 있고, 들어오면 경로 결정의 가중치를 왜곡하면서도
 * 에러를 내지 않는다.
 */
@Import({
    CatalogPersistenceMapper.class,
    CatalogDomainPersistenceAdapter.class,
    CatalogFieldPersistenceAdapter.class,
    CatalogTopicPersistenceAdapter.class,
    SkeletonPersistenceAdapter.class,
    ChapterPersistenceAdapter.class,
    ChapterRelationPersistenceAdapter.class
})
@DisplayName("챕터 관계 어댑터 (실제 PostgreSQL)")
class ChapterRelationPersistenceAdapterIT extends AbstractPostgresIT {

    @Autowired
    CatalogDomainPersistenceAdapter domains;

    @Autowired
    CatalogFieldPersistenceAdapter fields;

    @Autowired
    CatalogTopicPersistenceAdapter topics;

    @Autowired
    SkeletonPersistenceAdapter skeletons;

    @Autowired
    ChapterPersistenceAdapter chapters;

    @Autowired
    ChapterRelationPersistenceAdapter relations;

    @Autowired
    EntityManager entityManager;

    private Long skeletonId;
    private Long lossFunction;
    private Long gradientDescent;

    @BeforeEach
    void seed() {
        CatalogDomain domain = domains.save(CatalogDomain.create("cs", "컴퓨터과학", null, 0));
        CatalogField field = fields.save(CatalogField.create(domain.id(), "ai", "인공지능", null, 0));
        CatalogTopic topic = topics.save(CatalogTopic.create(field.id(), "machine-learning", "머신러닝", null, 0));
        skeletonId = skeletons.save(Skeleton.start(topic.id())).id();
        lossFunction = chapter("loss-function");
        gradientDescent = chapter("gradient-descent");
    }

    @Test
    @DisplayName("관계를 저장하고 뼈대 단위로 읽는다")
    void savesAndReadsRelations() {
        relations.saveAll(List.of(
                ChapterRelation.of(skeletonId, lossFunction, gradientDescent, ChapterRelationType.PREREQUISITE),
                ChapterRelation.of(skeletonId, gradientDescent, lossFunction, ChapterRelationType.RELATED)));

        assertThat(relations.findBySkeletonId(skeletonId))
                .extracting(ChapterRelation::relationType)
                .containsExactly(ChapterRelationType.PREREQUISITE, ChapterRelationType.RELATED);
        assertThat(relations.countBySkeletonId(skeletonId)).isEqualTo(2);
    }

    /** 중복 간선은 경로 결정의 가중치를 왜곡하면서도 에러를 내지 않는다. DB 가 막아야 한다. */
    @Test
    @DisplayName("같은 방향·같은 타입의 관계를 두 번 넣을 수 없다")
    void rejectsDuplicateEdge() {
        relations.saveAll(List.of(
                ChapterRelation.of(skeletonId, lossFunction, gradientDescent, ChapterRelationType.PREREQUISITE)));

        assertThatThrownBy(() -> {
                    relations.saveAll(List.of(ChapterRelation.of(
                            skeletonId, lossFunction, gradientDescent, ChapterRelationType.PREREQUISITE)));
                    entityManager.flush();
                })
                .isInstanceOf(Exception.class);
    }

    @Test
    @DisplayName("같은 쌍이라도 타입이 다르면 둘 다 저장된다")
    void allowsDifferentTypesForSamePair() {
        relations.saveAll(List.of(
                ChapterRelation.of(skeletonId, lossFunction, gradientDescent, ChapterRelationType.PREREQUISITE),
                ChapterRelation.of(skeletonId, lossFunction, gradientDescent, ChapterRelationType.DEEPENS)));

        assertThat(relations.countBySkeletonId(skeletonId)).isEqualTo(2);
    }

    @Test
    @DisplayName("재생성은 뼈대의 관계를 전부 지우고 다시 넣는다")
    void deleteBySkeletonReplacesGraph() {
        relations.saveAll(List.of(
                ChapterRelation.of(skeletonId, lossFunction, gradientDescent, ChapterRelationType.PREREQUISITE)));

        relations.deleteBySkeletonId(skeletonId);
        entityManager.flush();

        assertThat(relations.findBySkeletonId(skeletonId)).isEmpty();
    }

    @Test
    @DisplayName("챕터를 지우면 그 챕터에 걸린 관계도 사라진다")
    void cascadesOnChapterDelete() {
        relations.saveAll(List.of(
                ChapterRelation.of(skeletonId, lossFunction, gradientDescent, ChapterRelationType.PREREQUISITE)));
        entityManager.flush();

        chapters.save(chapters.findById(gradientDescent).orElseThrow());
        entityManager
                .createNativeQuery("DELETE FROM chapters WHERE id = :id")
                .setParameter("id", gradientDescent)
                .executeUpdate();

        assertThat(relations.findBySkeletonId(skeletonId)).isEmpty();
    }

    private Long chapter(String key) {
        return chapters.save(Chapter.create(skeletonId, key, "제목-" + key, null, 0))
                .id();
    }
}
