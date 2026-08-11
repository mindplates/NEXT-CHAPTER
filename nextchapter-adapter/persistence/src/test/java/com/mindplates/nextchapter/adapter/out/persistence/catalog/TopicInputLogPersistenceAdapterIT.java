package com.mindplates.nextchapter.adapter.out.persistence.catalog;

import static org.assertj.core.api.Assertions.assertThat;

import com.mindplates.nextchapter.adapter.out.persistence.AbstractPostgresIT;
import com.mindplates.nextchapter.common.PagedResult;
import com.mindplates.nextchapter.domain.catalog.model.CatalogDomain;
import com.mindplates.nextchapter.domain.catalog.model.CatalogField;
import com.mindplates.nextchapter.domain.catalog.model.TopicInputLog;
import com.mindplates.nextchapter.domain.catalog.model.TopicInputResolution;
import com.mindplates.nextchapter.domain.catalog.model.TopicMatchSource;
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
    TopicInputLogPersistenceAdapter.class
})
@DisplayName("주제 입력 로그 어댑터 (실제 PostgreSQL)")
class TopicInputLogPersistenceAdapterIT extends AbstractPostgresIT {

    @Autowired
    TopicInputLogPersistenceAdapter logs;

    @Autowired
    CatalogDomainPersistenceAdapter domains;

    @Autowired
    CatalogFieldPersistenceAdapter fields;

    private Long fieldId;

    /**
     * {@code proposed_field_id} 에 실제 FK 가 걸려 있어 분야를 먼저 만들어야 한다. FK 를 둔 이유는
     * 이 값이 "신규 주제를 붙일 자리"이고, 없는 분야를 가리키는 대기 상태는 거절 시점에야 터진다.
     */
    @BeforeEach
    void seedField() {
        CatalogDomain domain = domains.save(CatalogDomain.create("cs", "컴퓨터과학", null, 0));
        fieldId = fields.save(CatalogField.create(domain.id(), "ai", "인공지능", null, 0))
                .id();
    }

    @Test
    @DisplayName("후보 ID 목록이 왕복한다 — 확인 요청의 후보 검증에 쓰인다")
    void roundTripsCandidateIds() {
        TopicInputLog saved = logs.save(TopicInputLog.candidatesPresented(
                "강화학습 알려줘", List.of(3L, 7L, 11L), fieldId, "강화학습", "reinforcement-learning", "user-1"));

        assertThat(logs.findById(saved.id())).isPresent().get().satisfies(found -> {
            assertThat(found.candidateTopicIds()).containsExactly(3L, 7L, 11L);
            assertThat(found.proposedFieldId()).isEqualTo(fieldId);
            assertThat(found.proposedTopicSlug()).isEqualTo("reinforcement-learning");
            assertThat(found.awaitsUserDecision()).isTrue();
        });
    }

    @Test
    @DisplayName("후보가 없으면 빈 목록으로 읽힌다")
    void handlesEmptyCandidates() {
        TopicInputLog saved = logs.save(TopicInputLog.needsReview("바이올린 배우기", "바이올린", null, "user-1"));

        assertThat(logs.findById(saved.id())).isPresent().get().satisfies(found -> {
            assertThat(found.candidateTopicIds()).isEmpty();
            assertThat(found.proposedTopicSlug()).isNull();
        });
    }

    /**
     * 같은 표현이 몇 번 들어왔는지가 검토 우선순위다. 정규화 값으로 세므로 "기계 학습"과 "기계학습!"이
     * 같은 건으로 묶인다 — 표기 차이로 갈라지면 반복 입력이 반복으로 보이지 않는다.
     */
    @Test
    @DisplayName("정규화 값으로 반복 횟수를 센다")
    void countsRepeatedInputs() {
        logs.save(TopicInputLog.needsReview("기계 학습", null, null, "user-1"));
        logs.save(TopicInputLog.needsReview("기계학습!", null, null, "user-2"));
        logs.save(TopicInputLog.needsReview("딥러닝", null, null, "user-3"));

        TopicInputLog probe = TopicInputLog.needsReview("기계학습", null, null, null);

        assertThat(logs.countByNormalized(probe.normalized())).isEqualTo(2);
    }

    @Test
    @DisplayName("검토 대기열은 상태와 미검토 여부로 걸러진다")
    void filtersQueue() {
        logs.save(TopicInputLog.needsReview("바이올린", null, null, "user-1"));
        TopicInputLog reviewed = logs.save(TopicInputLog.needsReview("첼로", null, null, "user-2"));
        logs.save(TopicInputLog.matched("머신러닝", null, TopicMatchSource.ALIAS, "user-3"));
        logs.save(reviewed.reviewed("admin@nextchapter.local", "카탈로그에 반영했다"));

        PagedResult<TopicInputLog> queue = logs.search(List.of(TopicInputResolution.NEEDS_REVIEW), true, 0, 20);

        assertThat(queue.total()).isEqualTo(1);
        assertThat(queue.items()).singleElement().satisfies(log -> {
            assertThat(log.rawInput()).isEqualTo("바이올린");
            assertThat(log.isReviewed()).isFalse();
        });
    }

    @Test
    @DisplayName("상태 필터가 비어 있으면 전체를 본다 — 매칭 품질 확인 경로")
    void emptyFilterReturnsAll() {
        logs.save(TopicInputLog.needsReview("바이올린", null, null, "user-1"));
        logs.save(TopicInputLog.matched("머신러닝", null, TopicMatchSource.ALIAS, "user-2"));

        assertThat(logs.search(List.of(), false, 0, 20).total()).isEqualTo(2);
    }

    @Test
    @DisplayName("검토 표시가 남는다")
    void marksReviewed() {
        TopicInputLog saved = logs.save(TopicInputLog.needsReview("바이올린", null, null, "user-1"));

        TopicInputLog reviewed = logs.save(saved.reviewed("admin@nextchapter.local", "기각"));

        assertThat(reviewed.isReviewed()).isTrue();
        assertThat(reviewed.reviewedBy()).isEqualTo("admin@nextchapter.local");
        assertThat(reviewed.reviewNote()).isEqualTo("기각");
    }
}
