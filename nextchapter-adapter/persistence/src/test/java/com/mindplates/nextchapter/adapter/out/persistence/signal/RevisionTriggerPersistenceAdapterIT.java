package com.mindplates.nextchapter.adapter.out.persistence.signal;

import static org.assertj.core.api.Assertions.assertThat;

import com.mindplates.nextchapter.adapter.out.persistence.AbstractPostgresIT;
import com.mindplates.nextchapter.adapter.out.persistence.catalog.CatalogDomainPersistenceAdapter;
import com.mindplates.nextchapter.adapter.out.persistence.catalog.CatalogFieldPersistenceAdapter;
import com.mindplates.nextchapter.adapter.out.persistence.catalog.CatalogPersistenceMapper;
import com.mindplates.nextchapter.adapter.out.persistence.catalog.CatalogTopicPersistenceAdapter;
import com.mindplates.nextchapter.adapter.out.persistence.chapter.ChapterPersistenceAdapter;
import com.mindplates.nextchapter.adapter.out.persistence.skeleton.SkeletonPersistenceAdapter;
import com.mindplates.nextchapter.domain.catalog.model.CatalogDomain;
import com.mindplates.nextchapter.domain.catalog.model.CatalogField;
import com.mindplates.nextchapter.domain.catalog.model.CatalogTopic;
import com.mindplates.nextchapter.domain.chapter.model.Chapter;
import com.mindplates.nextchapter.domain.signal.model.BlockSignalAggregate;
import com.mindplates.nextchapter.domain.signal.model.RevisionTrigger;
import com.mindplates.nextchapter.domain.skeleton.model.Skeleton;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;

/**
 * 수정 트리거 어댑터를 실제 PostgreSQL 에 붙여 검증한다.
 *
 * <p>여기서만 확인되는 것 — 같은 (챕터, 버전, 블록) 조합을 다시 저장해도 한 줄만 남는지. 이 멱등성이
 * 깨지면 같은 블록이 승인 대기열에 여러 번 오른다.
 */
@Import({
    CatalogPersistenceMapper.class,
    CatalogDomainPersistenceAdapter.class,
    CatalogFieldPersistenceAdapter.class,
    CatalogTopicPersistenceAdapter.class,
    SkeletonPersistenceAdapter.class,
    ChapterPersistenceAdapter.class,
    RevisionTriggerPersistenceAdapter.class
})
@DisplayName("수정 트리거 어댑터 (실제 PostgreSQL)")
class RevisionTriggerPersistenceAdapterIT extends AbstractPostgresIT {

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
    RevisionTriggerPersistenceAdapter triggers;

    private Long chapterId;

    @BeforeEach
    void setUp() {
        CatalogDomain domain = domains.save(CatalogDomain.create("cs-trigger", "컴퓨터과학", null, 0));
        CatalogField field = fields.save(CatalogField.create(domain.id(), "ai-trigger", "인공지능", null, 0));
        CatalogTopic topic = topics.save(CatalogTopic.create(field.id(), "ml-trigger", "머신러닝", null, 0));
        Skeleton skeleton = skeletons.save(Skeleton.start(topic.id()));
        chapterId = chapters.save(Chapter.create(skeleton.id(), "gradient-descent", "경사하강법", null, 0))
                .id();
    }

    private RevisionTrigger trigger() {
        return RevisionTrigger.of(new BlockSignalAggregate(chapterId, 2, "b6", 5, 20, 9));
    }

    @Test
    @DisplayName("새 트리거는 저장되고 true 를 반환한다")
    void savesNewTrigger() {
        assertThat(triggers.saveIfAbsent(trigger())).isTrue();
    }

    @Test
    @DisplayName("같은 (챕터, 버전, 블록) 은 다시 저장해도 한 줄만 남는다")
    void skipsDuplicateBlock() {
        triggers.saveIfAbsent(trigger());

        boolean savedAgain = triggers.saveIfAbsent(trigger());

        assertThat(savedAgain).isFalse();
    }

    @Test
    @DisplayName("버전이 다르면 새로 트리거된다 — 수정 이후 다시 임계치를 넘을 수 있다")
    void triggersAgainOnNewVersion() {
        triggers.saveIfAbsent(trigger());

        boolean savedForNewVersion =
                triggers.saveIfAbsent(RevisionTrigger.of(new BlockSignalAggregate(chapterId, 3, "b6", 5, 20, 9)));

        assertThat(savedForNewVersion).isTrue();
    }

    @Test
    @DisplayName("새 트리거는 미처리 목록에 잡힌다")
    void newTriggerIsClaimable() {
        triggers.saveIfAbsent(trigger());

        assertThat(triggers.claimUnprocessed(10)).singleElement().satisfies(claimed -> {
            assertThat(claimed.id()).isNotNull();
            assertThat(claimed.chapterId()).isEqualTo(chapterId);
            assertThat(claimed.blockId()).isEqualTo("b6");
        });
    }

    /** 처리 표시가 듣지 않으면 같은 트리거로 제안이 반복 생성돼 AI 호출 비용이 배가 된다. */
    @Test
    @DisplayName("처리 표시된 트리거는 다시 집히지 않는다")
    void processedTriggerIsNotReclaimed() {
        triggers.saveIfAbsent(trigger());
        Long triggerId = triggers.claimUnprocessed(10).get(0).id();

        triggers.markProcessed(triggerId);

        assertThat(triggers.claimUnprocessed(10)).isEmpty();
    }
}
