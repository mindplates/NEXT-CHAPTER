package com.mindplates.nextchapter.adapter.out.persistence.admin;

import static org.assertj.core.api.Assertions.assertThat;

import com.mindplates.nextchapter.adapter.out.persistence.AbstractPostgresIT;
import com.mindplates.nextchapter.adapter.out.persistence.catalog.CatalogDomainPersistenceAdapter;
import com.mindplates.nextchapter.adapter.out.persistence.catalog.CatalogFieldPersistenceAdapter;
import com.mindplates.nextchapter.adapter.out.persistence.catalog.CatalogPersistenceMapper;
import com.mindplates.nextchapter.adapter.out.persistence.catalog.CatalogTopicPersistenceAdapter;
import com.mindplates.nextchapter.adapter.out.persistence.chapter.ChapterPersistenceAdapter;
import com.mindplates.nextchapter.adapter.out.persistence.signal.RevisionTriggerPersistenceAdapter;
import com.mindplates.nextchapter.adapter.out.persistence.skeleton.SkeletonPersistenceAdapter;
import com.mindplates.nextchapter.domain.admin.model.RevisionProposal;
import com.mindplates.nextchapter.domain.admin.model.RevisionProposalStatus;
import com.mindplates.nextchapter.domain.catalog.model.CatalogDomain;
import com.mindplates.nextchapter.domain.catalog.model.CatalogField;
import com.mindplates.nextchapter.domain.catalog.model.CatalogTopic;
import com.mindplates.nextchapter.domain.chapter.model.BlockType;
import com.mindplates.nextchapter.domain.chapter.model.Chapter;
import com.mindplates.nextchapter.domain.chapter.model.DeliveryFormat;
import com.mindplates.nextchapter.domain.chapter.model.ProposedBlock;
import com.mindplates.nextchapter.domain.signal.model.BlockSignalAggregate;
import com.mindplates.nextchapter.domain.signal.model.FormatBreakdown;
import com.mindplates.nextchapter.domain.signal.model.RevisionTrigger;
import com.mindplates.nextchapter.domain.skeleton.model.Skeleton;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;

/**
 * 수정안 어댑터를 실제 PostgreSQL 에 붙여 검증한다.
 *
 * <p>여기서만 확인되는 것 — {@code proposed_blocks}·{@code format_breakdown} JSONB 가 구조를 잃지 않고
 * 왕복하는지, 그리고 트리거 하나에 제안이 두 번 만들어지지 않는지(UNIQUE 제약).
 */
@Import({
    CatalogPersistenceMapper.class,
    CatalogDomainPersistenceAdapter.class,
    CatalogFieldPersistenceAdapter.class,
    CatalogTopicPersistenceAdapter.class,
    SkeletonPersistenceAdapter.class,
    ChapterPersistenceAdapter.class,
    RevisionTriggerPersistenceAdapter.class,
    RevisionProposalPersistenceAdapter.class
})
@DisplayName("수정안 어댑터 (실제 PostgreSQL)")
class RevisionProposalPersistenceAdapterIT extends AbstractPostgresIT {

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

    @Autowired
    RevisionProposalPersistenceAdapter proposals;

    private Long chapterId;
    private Long triggerId;

    @BeforeEach
    void setUp() {
        CatalogDomain domain = domains.save(CatalogDomain.create("cs-proposal", "컴퓨터과학", null, 0));
        CatalogField field = fields.save(CatalogField.create(domain.id(), "ai-proposal", "인공지능", null, 0));
        CatalogTopic topic = topics.save(CatalogTopic.create(field.id(), "ml-proposal", "머신러닝", null, 0));
        Skeleton skeleton = skeletons.save(Skeleton.start(topic.id()));
        Chapter chapter = chapters.save(Chapter.create(skeleton.id(), "gradient-descent", "경사하강법", null, 0));
        chapterId = chapter.id();

        triggers.saveIfAbsent(RevisionTrigger.of(new BlockSignalAggregate(chapterId, 2, "b6", 5, 20, 9)));
        triggerId = triggers.claimUnprocessed(10).get(0).id();
    }

    private RevisionProposal proposal() {
        return RevisionProposal.verified(
                triggerId,
                chapterId,
                2,
                "b6",
                "학습률 설명이 갑작스러워 예시를 추가했다.",
                List.of(
                        new FormatBreakdown(DeliveryFormat.WEB, 20, 9),
                        new FormatBreakdown(DeliveryFormat.VIDEO, 3, 0)),
                List.of(ProposedBlock.inheriting(
                        "b6",
                        BlockType.PARAGRAPH,
                        "학습률이 크면 발산할 수 있다",
                        Map.of("sources", List.of(Map.of("title", "출처"))))));
    }

    @Test
    @DisplayName("저장하면 ID 가 채워진다")
    void savesAndAssignsId() {
        RevisionProposal saved = proposals.save(proposal());

        assertThat(saved.id()).isNotNull();
        assertThat(saved.status()).isEqualTo(RevisionProposalStatus.PENDING_APPROVAL);
    }

    /** JSONB 가 구조를 잃으면 승인 화면이 제안된 본문을 보여줄 수 없다. */
    @Test
    @DisplayName("제안된 블록과 형태별 분해가 구조를 잃지 않고 왕복한다")
    void roundTripsBlocksAndBreakdown() {
        proposals.save(proposal());

        RevisionProposal found = proposals.findByTriggerId(triggerId).orElseThrow();

        assertThat(found.proposedBlocks()).hasSize(1);
        assertThat(found.proposedBlocks().get(0).rawId()).isEqualTo("b6");
        assertThat(found.proposedBlocks().get(0).type()).isEqualTo(BlockType.PARAGRAPH);
        assertThat(found.proposedBlocks().get(0).attributes()).containsKey("sources");

        assertThat(found.formatBreakdown())
                .extracting(FormatBreakdown::format, FormatBreakdown::attemptCount, FormatBreakdown::wrongCount)
                .containsExactlyInAnyOrder(
                        org.assertj.core.groups.Tuple.tuple(DeliveryFormat.WEB, 20L, 9L),
                        org.assertj.core.groups.Tuple.tuple(DeliveryFormat.VIDEO, 3L, 0L));
    }

    @Test
    @DisplayName("트리거로 제안을 찾는다")
    void findsByTriggerId() {
        proposals.save(proposal());

        Optional<RevisionProposal> found = proposals.findByTriggerId(triggerId);

        assertThat(found).isPresent();
        assertThat(found.get().chapterId()).isEqualTo(chapterId);
    }

    @Test
    @DisplayName("트리거가 없는 제안 조회는 빈 값이다")
    void emptyWhenNoProposal() {
        assertThat(proposals.findByTriggerId(9999L)).isEmpty();
    }

    /** 트리거 하나가 제안 하나를 만든다는 불변식을 DB 가 지킨다. */
    @Test
    @DisplayName("같은 트리거로 두 번째 제안을 저장하면 거절된다")
    void rejectsSecondProposalForSameTrigger() {
        proposals.save(proposal());

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> proposals.save(proposal()))
                .isInstanceOf(Exception.class);
    }
}
