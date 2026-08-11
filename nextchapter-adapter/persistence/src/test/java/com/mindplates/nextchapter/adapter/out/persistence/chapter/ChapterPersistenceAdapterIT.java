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
import com.mindplates.nextchapter.domain.chapter.model.Block;
import com.mindplates.nextchapter.domain.chapter.model.BlockDocument;
import com.mindplates.nextchapter.domain.chapter.model.BlockType;
import com.mindplates.nextchapter.domain.chapter.model.Chapter;
import com.mindplates.nextchapter.domain.chapter.model.ChapterVersion;
import com.mindplates.nextchapter.domain.chapter.model.ChapterVersionSource;
import com.mindplates.nextchapter.domain.skeleton.model.Skeleton;
import jakarta.persistence.EntityManager;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;

/**
 * 챕터·버전 어댑터를 실제 PostgreSQL 에 붙여 검증한다.
 *
 * <p>여기서만 확인되는 것 — {@code jsonb} 블록 문서가 구조를 잃지 않고 왕복하는지, 같은 챕터에 같은
 * 버전을 두 번 넣을 수 없는지, 그리고 <b>이전 버전 스냅샷이 수정 후에도 그대로 남는지</b>. 마지막
 * 항목이 3개월 성공 기준 2번(수정 전후 오답률 비교)의 전제다.
 */
@Import({
    CatalogPersistenceMapper.class,
    CatalogDomainPersistenceAdapter.class,
    CatalogFieldPersistenceAdapter.class,
    CatalogTopicPersistenceAdapter.class,
    SkeletonPersistenceAdapter.class,
    BlockDocumentJsonMapper.class,
    ChapterPersistenceAdapter.class,
    ChapterVersionPersistenceAdapter.class
})
@DisplayName("챕터 어댑터 (실제 PostgreSQL)")
class ChapterPersistenceAdapterIT extends AbstractPostgresIT {

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
    ChapterVersionPersistenceAdapter versions;

    @Autowired
    EntityManager entityManager;

    private Long skeletonId;

    @BeforeEach
    void seedSkeleton() {
        CatalogDomain domain = domains.save(CatalogDomain.create("cs", "컴퓨터과학", null, 0));
        CatalogField field = fields.save(CatalogField.create(domain.id(), "ai", "인공지능", null, 0));
        CatalogTopic topic = topics.save(CatalogTopic.create(field.id(), "machine-learning", "머신러닝", null, 0));
        skeletonId = skeletons.save(Skeleton.start(topic.id())).id();
    }

    @Test
    @DisplayName("블록 문서가 구조를 잃지 않고 왕복한다 — 이형 블록과 중첩 속성 포함")
    void blockDocumentRoundTrips() {
        Long chapterId = chapter("gradient-descent");
        BlockDocument body = BlockDocument.of(
                Block.text("b1", BlockType.HEADING, "경사하강법이란"),
                Block.text("b2", BlockType.PARAGRAPH, "손실함수를 줄이는 방향으로 파라미터를 옮긴다"),
                Block.figure("b3", Map.of("type", "plot", "spec", Map.of("x", "step", "y", "loss"))),
                Block.text("b4", BlockType.FORMULA, "\\theta := \\theta - \\alpha \\nabla J"),
                Block.text("b5", BlockType.NARRATION, "이제 학습률을 봅시다"),
                Block.quiz("b6", "학습률이 너무 크면?", List.of("발산한다", "수렴한다"), 0));

        versions.save(
                new ChapterVersion(null, chapterId, 1, body, null, ChapterVersionSource.GENERATED, "pipeline", null));

        assertThat(versions.findLatest(chapterId)).isPresent().get().satisfies(found -> {
            assertThat(found.body().blocks()).hasSize(6);
            assertThat(found.body().findById("b4"))
                    .get()
                    .extracting(Block::text)
                    .isEqualTo("\\theta := \\theta - \\alpha \\nabla J");
            assertThat(found.body().quizBlocks()).singleElement().satisfies(quiz -> assertThat(
                            quiz.attributes().get("choices"))
                    .isEqualTo(List.of("발산한다", "수렴한다")));
            assertThat(found.body().narrationBlocks()).hasSize(1);
            assertThat(found.body().webBlocks()).hasSize(5);
        });
    }

    @Test
    @DisplayName("같은 챕터에 같은 버전을 두 번 넣을 수 없다")
    void versionIsUniquePerChapter() {
        Long chapterId = chapter("gradient-descent");
        versions.save(
                new ChapterVersion(null, chapterId, 1, body("첫 본문"), null, ChapterVersionSource.GENERATED, null, null));

        assertThatThrownBy(() -> {
                    versions.save(new ChapterVersion(
                            null, chapterId, 1, body("다른 본문"), null, ChapterVersionSource.GENERATED, null, null));
                    entityManager.flush();
                })
                .isInstanceOf(Exception.class);
    }

    /** 이전 버전이 남지 않으면 "개선이 개선이었는지"를 확인할 방법이 없다. */
    @Test
    @DisplayName("수정 후에도 이전 버전 스냅샷이 그대로 남는다")
    void previousSnapshotSurvivesRevision() {
        Long chapterId = chapter("gradient-descent");
        versions.save(new ChapterVersion(
                null, chapterId, 1, body("원래 설명"), null, ChapterVersionSource.GENERATED, null, null));
        versions.save(new ChapterVersion(
                null,
                chapterId,
                2,
                body("보강한 설명"),
                "b1 오답 집중",
                ChapterVersionSource.REVISED_BY_SIGNAL,
                "signal",
                null));

        assertThat(versions.find(chapterId, 1)).get().satisfies(v1 -> {
            assertThat(v1.body().findById("b1")).get().extracting(Block::text).isEqualTo("원래 설명");
            assertThat(v1.isRevision()).isFalse();
        });
        assertThat(versions.findLatest(chapterId)).get().satisfies(v2 -> {
            assertThat(v2.version()).isEqualTo(2);
            assertThat(v2.changeSummary()).isEqualTo("b1 오답 집중");
        });
        assertThat(versions.findAllByChapterId(chapterId))
                .extracting(ChapterVersion::version)
                .containsExactly(2, 1);
    }

    @Test
    @DisplayName("챕터 키는 뼈대 안에서 유일하다 — 그래프가 키로 관계를 표현한다")
    void chapterKeyIsUniquePerSkeleton() {
        chapter("gradient-descent");

        assertThatThrownBy(() -> {
                    chapter("gradient-descent");
                    entityManager.flush();
                })
                .isInstanceOf(Exception.class);
    }

    @Test
    @DisplayName("본문 없는 챕터 수를 셀 수 있다 — published 전환 조건의 재료")
    void countsChaptersWithoutBody() {
        Long withBody = chapter("gradient-descent");
        chapter("loss-function");
        versions.save(
                new ChapterVersion(null, withBody, 1, body("본문"), null, ChapterVersionSource.GENERATED, null, null));
        chapters.save(chapters.findById(withBody).orElseThrow().withNewVersion(1, 1));

        assertThat(chapters.countBySkeletonId(skeletonId)).isEqualTo(2);
        assertThat(chapters.countBySkeletonIdWithoutBody(skeletonId)).isEqualTo(1);
    }

    @Test
    @DisplayName("키로 챕터를 찾고 뼈대 단위로 정렬해 읽는다")
    void findsByKeyAndSkeleton() {
        chapter("gradient-descent");
        chapter("loss-function");

        assertThat(chapters.findBySkeletonIdAndKey(skeletonId, "loss-function")).isPresent();
        assertThat(chapters.findBySkeletonIdAndKey(skeletonId, "nope")).isEmpty();
        assertThat(chapters.findBySkeletonId(skeletonId))
                .extracting(Chapter::chapterKey)
                .containsExactly("gradient-descent", "loss-function");
    }

    @Test
    @DisplayName("블록 ID 카운터가 저장되고 되돌아가지 않는다 — ID 재사용을 막는 장치다")
    void blockIdSequencePersists() {
        Long chapterId = chapter("gradient-descent");

        chapters.save(chapters.findById(chapterId).orElseThrow().withNewVersion(1, 7));

        assertThat(chapters.findById(chapterId)).get().satisfies(saved -> {
            assertThat(saved.blockIdSequence()).isEqualTo(7);
            assertThat(saved.nextBlockSequence()).isEqualTo(8);
        });
    }

    private Long chapter(String key) {
        return chapters.save(Chapter.create(skeletonId, key, "제목-" + key, "요약", 0))
                .id();
    }

    private static BlockDocument body(String text) {
        return BlockDocument.of(Block.text("b1", BlockType.PARAGRAPH, text));
    }
}
