package com.mindplates.nextchapter.adapter.out.persistence.layer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.mindplates.nextchapter.adapter.out.persistence.AbstractPostgresIT;
import com.mindplates.nextchapter.adapter.out.persistence.catalog.CatalogDomainPersistenceAdapter;
import com.mindplates.nextchapter.adapter.out.persistence.catalog.CatalogFieldPersistenceAdapter;
import com.mindplates.nextchapter.adapter.out.persistence.catalog.CatalogPersistenceMapper;
import com.mindplates.nextchapter.adapter.out.persistence.catalog.CatalogTopicPersistenceAdapter;
import com.mindplates.nextchapter.adapter.out.persistence.chapter.ChapterPersistenceAdapter;
import com.mindplates.nextchapter.adapter.out.persistence.skeleton.SkeletonPersistenceAdapter;
import com.mindplates.nextchapter.adapter.out.persistence.user.UserPersistenceAdapter;
import com.mindplates.nextchapter.application.layer.port.out.SaveSupplementBlockPort.NewSupplement;
import com.mindplates.nextchapter.domain.catalog.model.CatalogDomain;
import com.mindplates.nextchapter.domain.catalog.model.CatalogField;
import com.mindplates.nextchapter.domain.catalog.model.CatalogTopic;
import com.mindplates.nextchapter.domain.chapter.model.BlockType;
import com.mindplates.nextchapter.domain.chapter.model.Chapter;
import com.mindplates.nextchapter.domain.layer.model.SupplementBlock;
import com.mindplates.nextchapter.domain.skeleton.model.Skeleton;
import com.mindplates.nextchapter.domain.user.model.SocialProvider;
import com.mindplates.nextchapter.domain.user.model.User;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;

/**
 * 보충 블록 어댑터를 실제 PostgreSQL 에 붙여 검증한다.
 *
 * <p>여기서만 확인되는 것 둘 — <b>블록 ID 가 PK 에서 나오고 재사용되지 않는지</b>, 그리고 같은 지점에 두 번
 * 만들 수 없는지. 후자가 비용 방어선의 마지막 층이고, 뚫리면 새로고침 한 번이 LLM 호출 한 번이 된다.
 */
@Import({
    CatalogPersistenceMapper.class,
    CatalogDomainPersistenceAdapter.class,
    CatalogFieldPersistenceAdapter.class,
    CatalogTopicPersistenceAdapter.class,
    SkeletonPersistenceAdapter.class,
    ChapterPersistenceAdapter.class,
    UserPersistenceAdapter.class,
    SupplementBlockPersistenceAdapter.class
})
@DisplayName("보충 블록 어댑터 (실제 PostgreSQL)")
class SupplementBlockPersistenceAdapterIT extends AbstractPostgresIT {

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
    SupplementBlockPersistenceAdapter supplements;

    private Long userId;
    private Long otherUserId;
    private Long chapterId;

    @BeforeEach
    void setUp() {
        CatalogDomain domain = domains.save(CatalogDomain.create("cs-sup", "컴퓨터과학", null, 0));
        CatalogField field = fields.save(CatalogField.create(domain.id(), "ai-sup", "인공지능", null, 0));
        CatalogTopic topic = topics.save(CatalogTopic.create(field.id(), "ml-sup", "머신러닝", null, 0));
        Skeleton skeleton = skeletons.save(Skeleton.start(topic.id()));
        chapterId =
                chapters.save(Chapter.create(skeleton.id(), "a", "가", null, 0)).id();
        userId = users.save(User.of(SocialProvider.GOOGLE, "sup-user", null, "학습자"))
                .id();
        otherUserId = users.save(User.of(SocialProvider.KAKAO, "sup-other", null, "다른사람"))
                .id();
    }

    private NewSupplement supplement(Long user, String anchor, String text) {
        return new NewSupplement(user, chapterId, 2, anchor, BlockType.PARAGRAPH, text, Map.of("source", "layer"));
    }

    /** ID 를 저장하지 않고 PK 에서 만든다 — 순번을 계산해 발급하면 지운 뒤 다시 만들 때 되살아난다. */
    @Test
    @DisplayName("블록 ID 가 PK 에서 나온다")
    void blockIdComesFromPk() {
        SupplementBlock created = supplements.create(supplement(userId, "b2", "예를 들어"));

        assertThat(created.block().id()).startsWith("s");
        assertThat(created.anchorBlockId()).isEqualTo("b2");
        assertThat(created.block().text()).isEqualTo("예를 들어");
        assertThat(created.block().attributes()).containsEntry("source", "layer");
    }

    /** 같은 앵커에 두 번 만들 수 있으면 새로고침 한 번이 LLM 호출 한 번이 된다. */
    @Test
    @DisplayName("같은 지점에 두 번 만들 수 없다")
    void oneSupplementPerAnchor() {
        supplements.create(supplement(userId, "b2", "첫 설명"));

        NewSupplement duplicate = supplement(userId, "b2", "두 번째 설명");

        assertThatThrownBy(() -> supplements.create(duplicate)).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("다른 사용자는 같은 지점에 각자 만들 수 있다")
    void perUserAnchor() {
        supplements.create(supplement(userId, "b2", "내 설명"));
        supplements.create(supplement(otherUserId, "b2", "남의 설명"));

        assertThat(supplements.findByUserAndChapter(userId, chapterId)).hasSize(1);
        assertThat(supplements.findByUserAndChapter(otherUserId, chapterId)).hasSize(1);
    }

    /** 다른 사용자의 보충 블록이 섞이면 개인화가 아니라 유출이다. */
    @Test
    @DisplayName("합성 조회는 그 사용자의 것만 읽는다")
    void isolatesUsers() {
        supplements.create(supplement(userId, "b2", "내 설명"));
        supplements.create(supplement(otherUserId, "b3", "남의 설명"));

        assertThat(supplements.findByUserAndChapter(userId, chapterId))
                .singleElement()
                .satisfies(block -> assertThat(block.block().text()).isEqualTo("내 설명"));
    }

    @Test
    @DisplayName("앵커로 이미 있는지 확인할 수 있다")
    void findsByAnchor() {
        SupplementBlock created = supplements.create(supplement(userId, "b2", "설명"));

        assertThat(supplements.findByAnchor(userId, chapterId, "b2"))
                .get()
                .extracting(block -> block.block().id())
                .isEqualTo(created.block().id());
        assertThat(supplements.findByAnchor(userId, chapterId, "b9")).isEmpty();
    }

    /** 여러 개가 붙으면 그 순서가 화면 순서가 된다 — 만든 순서를 유지해야 설명이 뒤바뀌지 않는다. */
    @Test
    @DisplayName("만든 순서대로 나온다")
    void keepsInsertionOrder() {
        SupplementBlock first = supplements.create(supplement(userId, "b1", "첫째"));
        SupplementBlock second = supplements.create(supplement(userId, "b2", "둘째"));

        assertThat(supplements.findByUserAndChapter(userId, chapterId))
                .extracting(block -> block.block().id())
                .containsExactly(first.block().id(), second.block().id());
    }
}
