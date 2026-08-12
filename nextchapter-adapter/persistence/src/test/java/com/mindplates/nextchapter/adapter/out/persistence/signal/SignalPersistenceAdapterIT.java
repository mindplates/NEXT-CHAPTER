package com.mindplates.nextchapter.adapter.out.persistence.signal;

import static org.assertj.core.api.Assertions.assertThat;

import com.mindplates.nextchapter.adapter.out.persistence.AbstractPostgresIT;
import com.mindplates.nextchapter.adapter.out.persistence.catalog.CatalogDomainPersistenceAdapter;
import com.mindplates.nextchapter.adapter.out.persistence.catalog.CatalogFieldPersistenceAdapter;
import com.mindplates.nextchapter.adapter.out.persistence.catalog.CatalogPersistenceMapper;
import com.mindplates.nextchapter.adapter.out.persistence.catalog.CatalogTopicPersistenceAdapter;
import com.mindplates.nextchapter.adapter.out.persistence.chapter.ChapterPersistenceAdapter;
import com.mindplates.nextchapter.adapter.out.persistence.skeleton.SkeletonPersistenceAdapter;
import com.mindplates.nextchapter.adapter.out.persistence.user.UserPersistenceAdapter;
import com.mindplates.nextchapter.domain.catalog.model.CatalogDomain;
import com.mindplates.nextchapter.domain.catalog.model.CatalogField;
import com.mindplates.nextchapter.domain.catalog.model.CatalogTopic;
import com.mindplates.nextchapter.domain.chapter.model.Chapter;
import com.mindplates.nextchapter.domain.chapter.model.DeliveryFormat;
import com.mindplates.nextchapter.domain.signal.model.Signal;
import com.mindplates.nextchapter.domain.signal.model.SignalType;
import com.mindplates.nextchapter.domain.skeleton.model.Skeleton;
import com.mindplates.nextchapter.domain.user.model.SocialProvider;
import com.mindplates.nextchapter.domain.user.model.User;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;

/**
 * 신호 어댑터를 실제 PostgreSQL 에 붙여 검증한다.
 *
 * <p>여기서만 확인되는 것 — {@code jsonb} 페이로드가 구조를 잃지 않고 왕복하는지, {@code FOR UPDATE SKIP
 * LOCKED} 로 집어 온 신호가 발행 표시 후 다시 잡히지 않는지, 그리고 개인 루프 조회가 사용자·챕터로
 * 좁혀지는지. 발행 표시가 듣지 않으면 같은 신호가 반복 발행돼 집계가 여러 번 세는데, 그건 에러 없이
 * 임계치를 앞당긴다.
 */
@Import({
    CatalogPersistenceMapper.class,
    CatalogDomainPersistenceAdapter.class,
    CatalogFieldPersistenceAdapter.class,
    CatalogTopicPersistenceAdapter.class,
    SkeletonPersistenceAdapter.class,
    ChapterPersistenceAdapter.class,
    UserPersistenceAdapter.class,
    SignalPersistenceAdapter.class
})
@DisplayName("신호 어댑터 (실제 PostgreSQL)")
class SignalPersistenceAdapterIT extends AbstractPostgresIT {

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
    UserPersistenceAdapter users;

    @Autowired
    SignalPersistenceAdapter signals;

    private Long userId;
    private Long chapterId;

    @BeforeEach
    void setUp() {
        CatalogDomain domain = domains.save(CatalogDomain.create("cs-signal", "컴퓨터과학", null, 0));
        CatalogField field = fields.save(CatalogField.create(domain.id(), "ai-signal", "인공지능", null, 0));
        CatalogTopic topic = topics.save(CatalogTopic.create(field.id(), "ml-signal", "머신러닝", null, 0));
        Skeleton skeleton = skeletons.save(Skeleton.start(topic.id()));
        chapterId = chapters.save(Chapter.create(skeleton.id(), "gradient-descent", "경사하강법", null, 0))
                .id();
        userId = users.save(User.of(SocialProvider.GOOGLE, "sub-signal", "learner@example.com", "학습자"))
                .id();
    }

    private Signal quizAnswer(boolean correct) {
        return Signal.of(
                userId,
                chapterId,
                2,
                "b6",
                DeliveryFormat.WEB,
                SignalType.QUIZ_ANSWER,
                Map.of("correct", correct, "choice", "가"));
    }

    /** 페이로드가 구조를 잃으면 오답률을 계산할 수 없다 — 그 손실은 저장 시점에 에러를 내지 않는다. */
    @Test
    @DisplayName("페이로드가 구조를 잃지 않고 왕복한다")
    void payloadRoundTrips() {
        Signal saved = signals.save(quizAnswer(false));

        assertThat(saved.id()).isNotNull();
        assertThat(signals.claimUnpublished(10)).singleElement().satisfies(signal -> {
            assertThat(signal.payload()).containsEntry("correct", false).containsEntry("choice", "가");
            assertThat(signal.blockId()).isEqualTo("b6");
            assertThat(signal.format()).isEqualTo(DeliveryFormat.WEB);
            assertThat(signal.type()).isEqualTo(SignalType.QUIZ_ANSWER);
            assertThat(signal.chapterVersion()).isEqualTo(2);
            assertThat(signal.occurredAt()).isNotNull();
            assertThat(signal.isPublished()).isFalse();
        });
    }

    /** 발행 표시가 듣지 않으면 같은 신호가 반복 발행돼 집계가 여러 번 세고, 임계치가 앞당겨진다. */
    @Test
    @DisplayName("발행 표시된 신호는 다시 집히지 않는다")
    void publishedIsNotReclaimed() {
        Signal saved = signals.save(quizAnswer(true));

        signals.markPublished(saved.id());

        assertThat(signals.claimUnpublished(10)).isEmpty();
    }

    @Test
    @DisplayName("개인 루프는 사용자·챕터로 좁혀 읽는다")
    void findsByUserAndChapter() {
        signals.save(quizAnswer(true));
        signals.save(quizAnswer(false));
        Long otherUser =
                users.save(User.of(SocialProvider.KAKAO, "other", null, "다른사람")).id();
        signals.save(Signal.of(
                otherUser, chapterId, 2, "b6", DeliveryFormat.WEB, SignalType.QUIZ_ANSWER, Map.of("correct", true)));

        List<Signal> mine = signals.findByUserAndChapter(userId, chapterId);

        assertThat(mine).hasSize(2);
        assertThat(mine).allSatisfy(signal -> assertThat(signal.userId()).isEqualTo(userId));
    }

    /** 소비 진행은 블록이 없다 — 컬럼이 NOT NULL 이면 그 신호를 저장할 수 없다. */
    @Test
    @DisplayName("블록 없는 신호도 저장된다")
    void storesSignalWithoutBlock() {
        signals.save(Signal.of(
                userId, chapterId, 2, null, DeliveryFormat.VIDEO, SignalType.PROGRESS, Map.of("percent", 100)));

        assertThat(signals.claimUnpublished(10)).singleElement().satisfies(signal -> {
            assertThat(signal.blockId()).isNull();
            assertThat(signal.format()).isEqualTo(DeliveryFormat.VIDEO);
        });
    }

    @Test
    @DisplayName("집어 오는 순서는 ID 순이다 — 발생 순서가 유지된다")
    void claimsInIdOrder() {
        Signal first = signals.save(quizAnswer(true));
        Signal second = signals.save(quizAnswer(false));

        assertThat(signals.claimUnpublished(10)).extracting(Signal::id).containsExactly(first.id(), second.id());
    }
}
