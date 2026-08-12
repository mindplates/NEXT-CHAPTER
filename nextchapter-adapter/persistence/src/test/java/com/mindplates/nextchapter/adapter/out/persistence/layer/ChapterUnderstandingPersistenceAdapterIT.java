package com.mindplates.nextchapter.adapter.out.persistence.layer;

import static org.assertj.core.api.Assertions.assertThat;

import com.mindplates.nextchapter.adapter.out.persistence.AbstractPostgresIT;
import com.mindplates.nextchapter.adapter.out.persistence.catalog.CatalogDomainPersistenceAdapter;
import com.mindplates.nextchapter.adapter.out.persistence.catalog.CatalogFieldPersistenceAdapter;
import com.mindplates.nextchapter.adapter.out.persistence.catalog.CatalogPersistenceMapper;
import com.mindplates.nextchapter.adapter.out.persistence.catalog.CatalogTopicPersistenceAdapter;
import com.mindplates.nextchapter.adapter.out.persistence.chapter.ChapterPersistenceAdapter;
import com.mindplates.nextchapter.adapter.out.persistence.signal.SignalPersistenceAdapter;
import com.mindplates.nextchapter.adapter.out.persistence.skeleton.SkeletonPersistenceAdapter;
import com.mindplates.nextchapter.adapter.out.persistence.user.UserPersistenceAdapter;
import com.mindplates.nextchapter.domain.catalog.model.CatalogDomain;
import com.mindplates.nextchapter.domain.catalog.model.CatalogField;
import com.mindplates.nextchapter.domain.catalog.model.CatalogTopic;
import com.mindplates.nextchapter.domain.chapter.model.Chapter;
import com.mindplates.nextchapter.domain.chapter.model.DeliveryFormat;
import com.mindplates.nextchapter.domain.layer.model.ChapterUnderstanding;
import com.mindplates.nextchapter.domain.layer.model.UnderstandingLevel;
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
 * 이해도 집계를 실제 PostgreSQL 에 붙여 검증한다.
 *
 * <p>여기서만 확인되는 것이 <b>jsonb 조건 집계</b>다 — {@code payload->>'correct' = 'true'} 가 실제로
 * 맞히는지, 진행률 캐스팅이 되는지, 다른 사용자의 신호가 섞이지 않는지. 목으로 덮으면 세 가지가 정확히
 * 테스트에서 빠지고, 틀려도 에러가 아니라 <b>잘못된 이해도</b>가 나온다.
 *
 * <p>그리고 <b>버전이 올라가도 레이어가 유지되는지</b>를 본다 — M4 완료 기준 4번이다.
 */
@Import({
    CatalogPersistenceMapper.class,
    CatalogDomainPersistenceAdapter.class,
    CatalogFieldPersistenceAdapter.class,
    CatalogTopicPersistenceAdapter.class,
    SkeletonPersistenceAdapter.class,
    ChapterPersistenceAdapter.class,
    UserPersistenceAdapter.class,
    SignalPersistenceAdapter.class,
    ChapterUnderstandingPersistenceAdapter.class
})
@DisplayName("이해도 집계 (실제 PostgreSQL)")
class ChapterUnderstandingPersistenceAdapterIT extends AbstractPostgresIT {

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

    @Autowired
    ChapterUnderstandingPersistenceAdapter understandings;

    private Long userId;
    private Long otherUserId;
    private Long skeletonId;
    private Long chapterId;
    private Long otherChapterId;

    @BeforeEach
    void setUp() {
        CatalogDomain domain = domains.save(CatalogDomain.create("cs-layer", "컴퓨터과학", null, 0));
        CatalogField field = fields.save(CatalogField.create(domain.id(), "ai-layer", "인공지능", null, 0));
        CatalogTopic topic = topics.save(CatalogTopic.create(field.id(), "ml-layer", "머신러닝", null, 0));
        skeletonId = skeletons.save(Skeleton.start(topic.id())).id();
        chapterId = chapters.save(Chapter.create(skeletonId, "a", "가", null, 0)).id();
        otherChapterId =
                chapters.save(Chapter.create(skeletonId, "b", "나", null, 1)).id();
        userId = users.save(User.of(SocialProvider.GOOGLE, "layer-user", null, "학습자"))
                .id();
        otherUserId = users.save(User.of(SocialProvider.KAKAO, "layer-other", null, "다른사람"))
                .id();
    }

    private void quiz(Long user, Long chapter, boolean correct) {
        signals.save(Signal.of(
                user,
                chapter,
                1,
                "b6",
                DeliveryFormat.WEB,
                SignalType.QUIZ_ANSWER,
                Map.of("choice", "가", "correct", correct)));
    }

    private void question(Long user, Long chapter) {
        signals.save(Signal.of(user, chapter, 1, "b2", DeliveryFormat.WEB, SignalType.QUESTION, Map.of("text", "왜?")));
    }

    private void progress(Long user, Long chapter, int percent) {
        signals.save(
                Signal.of(user, chapter, 1, null, DeliveryFormat.WEB, SignalType.PROGRESS, Map.of("percent", percent)));
    }

    @Test
    @DisplayName("퀴즈·질문·진행을 챕터별로 접는다")
    void aggregatesByChapter() {
        quiz(userId, chapterId, true);
        quiz(userId, chapterId, false);
        quiz(userId, chapterId, false);
        question(userId, chapterId);
        progress(userId, chapterId, 40);
        progress(userId, chapterId, 90);

        ChapterUnderstanding understanding = understandings.findByChapter(userId, chapterId);

        assertThat(understanding.attempts()).isEqualTo(3);
        assertThat(understanding.correct()).isEqualTo(1);
        assertThat(understanding.questions()).isEqualTo(1);
        // 같은 챕터에서 여러 번 왔다면 가장 큰 진행률이 그 사람의 진행이다.
        assertThat(understanding.progressPercent()).isEqualTo(90);
        assertThat(understanding.lastSeenAt()).isNotNull();
        assertThat(understanding.level()).isEqualTo(UnderstandingLevel.STRUGGLING);
    }

    /** 다른 사용자의 신호가 섞이면 그 사람의 오답이 내 이해도를 낮춘다 — 에러 없이 나쁜 추천이 된다. */
    @Test
    @DisplayName("다른 사용자의 신호는 섞이지 않는다")
    void isolatesUsers() {
        quiz(userId, chapterId, true);
        quiz(otherUserId, chapterId, false);
        quiz(otherUserId, chapterId, false);

        assertThat(understandings.findByChapter(userId, chapterId).attempts()).isEqualTo(1);
        assertThat(understandings.findByChapter(otherUserId, chapterId).attempts())
                .isEqualTo(2);
    }

    @Test
    @DisplayName("뼈대 요약은 신호가 있는 챕터만 담는다 — 없는 것과 0은 다르다")
    void summarizesOnlyTouchedChapters() {
        quiz(userId, chapterId, true);
        quiz(userId, chapterId, true);

        List<ChapterUnderstanding> summary = understandings.summarizeBySkeleton(userId, skeletonId);

        assertThat(summary).extracting(ChapterUnderstanding::chapterId).containsExactly(chapterId);
        assertThat(understandings.findByChapter(userId, otherChapterId).visited())
                .isFalse();
    }

    /**
     * <b>M4 완료 기준 4번</b> — 뼈대가 수정돼도 개인 학습 기록이 살아남는다. 신호는 챕터 ID 에 붙고 버전은
     * 데이터로만 담기므로, 새 버전의 신호가 더해져도 이전 기록이 사라지지 않는다.
     */
    @Test
    @DisplayName("버전이 올라가도 이전 기록이 남는다")
    void survivesVersionBump() {
        quiz(userId, chapterId, false);
        quiz(userId, chapterId, false);

        // 챕터가 수정돼 v2 가 됐고, 그 뒤의 응답은 v2 로 기록된다.
        signals.save(Signal.of(
                userId,
                chapterId,
                2,
                "b6",
                DeliveryFormat.WEB,
                SignalType.QUIZ_ANSWER,
                Map.of("choice", "가", "correct", true)));

        ChapterUnderstanding understanding = understandings.findByChapter(userId, chapterId);

        assertThat(understanding.attempts()).isEqualTo(3);
        assertThat(understanding.correct()).isEqualTo(1);
    }

    @Test
    @DisplayName("다른 뼈대의 챕터는 요약에 들어오지 않는다")
    void scopedToSkeleton() {
        quiz(userId, chapterId, true);

        assertThat(understandings.summarizeBySkeleton(userId, 999_999L)).isEmpty();
    }

    @Test
    @DisplayName("신호가 없으면 빈 이해도를 준다")
    void emptyWhenNoSignals() {
        ChapterUnderstanding understanding = understandings.findByChapter(userId, otherChapterId);

        assertThat(understanding.chapterId()).isEqualTo(otherChapterId);
        assertThat(understanding.attempts()).isZero();
        assertThat(understanding.level()).isEqualTo(UnderstandingLevel.UNKNOWN);
    }
}
