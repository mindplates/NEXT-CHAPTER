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
import com.mindplates.nextchapter.domain.chapter.model.DeliveryFormat;
import com.mindplates.nextchapter.domain.signal.model.BlockSignalAggregate;
import com.mindplates.nextchapter.domain.signal.model.CollectiveSignalEvent;
import com.mindplates.nextchapter.domain.signal.model.SignalType;
import com.mindplates.nextchapter.domain.skeleton.model.Skeleton;
import java.time.LocalDateTime;
import org.assertj.core.data.Offset;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;

/**
 * 집단 신호 원장 어댑터를 실제 PostgreSQL 에 붙여 검증한다.
 *
 * <p>여기서만 확인되는 것 — 같은 {@code signalId} 를 두 번 저장해도 원장에는 한 줄만 남는지(재수신에 대한
 * 멱등성), 그리고 {@code FILTER} 집계가 타입별로 정확히 세는지. 멱등성이 깨지면 재전송 한 번이 임계치를
 * 조용히 앞당긴다.
 */
@Import({
    CatalogPersistenceMapper.class,
    CatalogDomainPersistenceAdapter.class,
    CatalogFieldPersistenceAdapter.class,
    CatalogTopicPersistenceAdapter.class,
    SkeletonPersistenceAdapter.class,
    ChapterPersistenceAdapter.class,
    CollectiveSignalPersistenceAdapter.class
})
@DisplayName("집단 신호 원장 어댑터 (실제 PostgreSQL)")
class CollectiveSignalPersistenceAdapterIT extends AbstractPostgresIT {

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
    CollectiveSignalPersistenceAdapter ledger;

    private Long chapterId;

    @BeforeEach
    void setUp() {
        CatalogDomain domain = domains.save(CatalogDomain.create("cs-collective", "컴퓨터과학", null, 0));
        CatalogField field = fields.save(CatalogField.create(domain.id(), "ai-collective", "인공지능", null, 0));
        CatalogTopic topic = topics.save(CatalogTopic.create(field.id(), "ml-collective", "머신러닝", null, 0));
        Skeleton skeleton = skeletons.save(Skeleton.start(topic.id()));
        chapterId = chapters.save(Chapter.create(skeleton.id(), "gradient-descent", "경사하강법", null, 0))
                .id();
    }

    private CollectiveSignalEvent question(long signalId) {
        return new CollectiveSignalEvent(
                signalId, chapterId, 2, "b6", DeliveryFormat.WEB, SignalType.QUESTION, null, LocalDateTime.now());
    }

    private CollectiveSignalEvent quizAnswer(long signalId, boolean correct) {
        return new CollectiveSignalEvent(
                signalId, chapterId, 2, "b6", DeliveryFormat.WEB, SignalType.QUIZ_ANSWER, correct, LocalDateTime.now());
    }

    @Test
    @DisplayName("새 신호는 저장되고 true 를 반환한다")
    void savesNewEvent() {
        assertThat(ledger.saveIfAbsent(question(1L))).isTrue();
    }

    /** 재수신 한 번이 집계를 두 번 세면 임계치가 조용히 앞당겨진다. */
    @Test
    @DisplayName("같은 signalId 를 다시 저장하면 건너뛰고 false 를 반환한다")
    void skipsDuplicateSignalId() {
        ledger.saveIfAbsent(question(1L));

        boolean savedAgain = ledger.saveIfAbsent(question(1L));

        assertThat(savedAgain).isFalse();
        assertThat(ledger.aggregate(chapterId, 2, "b6").questionCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("블록 단위로 질문 수·시도 수·오답 수를 센다")
    void aggregatesByBlock() {
        ledger.saveIfAbsent(question(1L));
        ledger.saveIfAbsent(question(2L));
        ledger.saveIfAbsent(quizAnswer(3L, true));
        ledger.saveIfAbsent(quizAnswer(4L, false));
        ledger.saveIfAbsent(quizAnswer(5L, false));

        BlockSignalAggregate aggregate = ledger.aggregate(chapterId, 2, "b6");

        assertThat(aggregate.questionCount()).isEqualTo(2);
        assertThat(aggregate.attemptCount()).isEqualTo(3);
        assertThat(aggregate.wrongCount()).isEqualTo(2);
        assertThat(aggregate.wrongRate()).isCloseTo(2.0 / 3, Offset.offset(0.0001));
    }

    @Test
    @DisplayName("다른 블록의 신호는 섞이지 않는다")
    void doesNotMixOtherBlocks() {
        ledger.saveIfAbsent(question(1L));
        ledger.saveIfAbsent(new CollectiveSignalEvent(
                2L, chapterId, 2, "b7", DeliveryFormat.WEB, SignalType.QUESTION, null, LocalDateTime.now()));

        assertThat(ledger.aggregate(chapterId, 2, "b6").questionCount()).isEqualTo(1);
        assertThat(ledger.aggregate(chapterId, 2, "b7").questionCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("아무 신호도 없는 블록은 0 이다")
    void zeroWhenNoSignals() {
        BlockSignalAggregate aggregate = ledger.aggregate(chapterId, 2, "b99");

        assertThat(aggregate.questionCount()).isZero();
        assertThat(aggregate.attemptCount()).isZero();
        assertThat(aggregate.wrongCount()).isZero();
        assertThat(aggregate.wrongRate()).isZero();
    }
}
