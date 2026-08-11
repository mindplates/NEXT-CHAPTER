package com.mindplates.nextchapter.adapter.out.persistence.chapter;

import static org.assertj.core.api.Assertions.assertThat;

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
import com.mindplates.nextchapter.domain.chapter.model.ChapterOutline;
import com.mindplates.nextchapter.domain.chapter.model.OutlineFinding;
import com.mindplates.nextchapter.domain.chapter.model.OutlineFindingType;
import com.mindplates.nextchapter.domain.skeleton.model.Skeleton;
import jakarta.persistence.EntityManager;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;

@Import({
    CatalogPersistenceMapper.class,
    CatalogDomainPersistenceAdapter.class,
    CatalogFieldPersistenceAdapter.class,
    CatalogTopicPersistenceAdapter.class,
    SkeletonPersistenceAdapter.class,
    ChapterPersistenceAdapter.class,
    ChapterOutlinePersistenceAdapter.class,
    OutlineFindingPersistenceAdapter.class
})
@DisplayName("개요·소견 어댑터 (실제 PostgreSQL)")
class ChapterOutlinePersistenceAdapterIT extends AbstractPostgresIT {

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
    ChapterOutlinePersistenceAdapter outlines;

    @Autowired
    OutlineFindingPersistenceAdapter findings;

    @Autowired
    EntityManager entityManager;

    private Long skeletonId;
    private Long firstChapter;
    private Long secondChapter;

    @BeforeEach
    void seed() {
        CatalogDomain domain = domains.save(CatalogDomain.create("cs", "컴퓨터과학", null, 0));
        CatalogField field = fields.save(CatalogField.create(domain.id(), "ai", "인공지능", null, 0));
        CatalogTopic topic = topics.save(CatalogTopic.create(field.id(), "machine-learning", "머신러닝", null, 0));
        skeletonId = skeletons.save(Skeleton.start(topic.id())).id();
        firstChapter = chapter("loss-function", 0);
        secondChapter = chapter("gradient-descent", 1);
        entityManager.flush();
    }

    @Test
    @DisplayName("개요 항목의 순서가 보존된다 — 순서가 의미를 갖는다")
    void preservesPointOrder() {
        outlines.saveAll(List.of(ChapterOutline.of(firstChapter, List.of("첫째", "둘째", "셋째"))));

        assertThat(outlines.findByChapterId(firstChapter))
                .isPresent()
                .get()
                .extracting(ChapterOutline::points)
                .isEqualTo(List.of("첫째", "둘째", "셋째"));
    }

    @Test
    @DisplayName("챕터당 하나다 — 다시 저장하면 덮어쓴다")
    void saveIsUpsert() {
        outlines.saveAll(List.of(ChapterOutline.of(firstChapter, List.of("옛 항목", "둘째"))));

        outlines.saveAll(List.of(ChapterOutline.of(firstChapter, List.of("새 항목", "둘째", "셋째"))));

        assertThat(outlines.findByChapterId(firstChapter))
                .get()
                .extracting(ChapterOutline::points)
                .isEqualTo(List.of("새 항목", "둘째", "셋째"));
        assertThat(outlines.findBySkeletonId(skeletonId)).hasSize(1);
    }

    @Test
    @DisplayName("뼈대 단위 조회는 챕터 정렬 순서를 따른다")
    void readsInChapterOrder() {
        outlines.saveAll(List.of(
                ChapterOutline.of(secondChapter, List.of("b1", "b2")),
                ChapterOutline.of(firstChapter, List.of("a1", "a2"))));

        assertThat(outlines.findBySkeletonId(skeletonId))
                .extracting(ChapterOutline::chapterId)
                .containsExactly(firstChapter, secondChapter);
    }

    @Test
    @DisplayName("소견을 저장하고 뼈대 단위로 읽는다")
    void savesFindings() {
        findings.saveAll(List.of(
                OutlineFinding.of(
                        skeletonId,
                        OutlineFindingType.DUPLICATE,
                        List.of("loss-function", "gradient-descent"),
                        "두 챕터가 미분을 모두 설명한다"),
                OutlineFinding.of(skeletonId, OutlineFindingType.GAP, List.of(), "정규화 챕터가 없다")));

        List<OutlineFinding> saved = findings.findBySkeletonId(skeletonId);

        assertThat(saved).hasSize(2);
        assertThat(saved.get(0).chapterKeys()).containsExactly("loss-function", "gradient-descent");
        // 관련 챕터가 없는 소견도 있다 — gap 은 "없는 챕터"를 가리키므로 키가 비어 있다.
        assertThat(saved.get(1).chapterKeys()).isEmpty();
    }

    @Test
    @DisplayName("재생성은 옛 소견을 지운다")
    void deletesFindings() {
        findings.saveAll(List.of(OutlineFinding.of(skeletonId, OutlineFindingType.GAP, List.of(), "무언가")));

        findings.deleteBySkeletonId(skeletonId);

        assertThat(findings.findBySkeletonId(skeletonId)).isEmpty();
    }

    @Test
    @DisplayName("챕터를 지우면 개요도 사라진다")
    void cascadesOnChapterDelete() {
        outlines.saveAll(List.of(ChapterOutline.of(firstChapter, List.of("a", "b"))));

        entityManager
                .createNativeQuery("DELETE FROM chapters WHERE id = :id")
                .setParameter("id", firstChapter)
                .executeUpdate();

        assertThat(outlines.findByChapterId(firstChapter)).isEmpty();
    }

    private Long chapter(String key, int sortOrder) {
        return chapters.save(Chapter.create(skeletonId, key, "제목-" + key, null, sortOrder))
                .id();
    }
}
