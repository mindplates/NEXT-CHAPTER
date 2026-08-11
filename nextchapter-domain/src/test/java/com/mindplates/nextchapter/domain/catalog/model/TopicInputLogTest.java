package com.mindplates.nextchapter.domain.catalog.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("주제 입력 로그")
class TopicInputLogTest {

    @Test
    @DisplayName("정규화 값은 원문에서 파생된다 — 넘긴 값을 신뢰하지 않는다")
    void normalizedIsDerived() {
        TopicInputLog log = TopicInputLog.needsReview("기계 학습!", null, null, "user-1");

        assertThat(log.rawInput()).isEqualTo("기계 학습!");
        assertThat(log.normalized())
                .isEqualTo(TopicInputLog.needsReview("기계학습", null, null, null).normalized());
    }

    @Test
    @DisplayName("후보 제시 상태에서만 사용자 판정을 받는다 — 같은 토큰을 두 번 쓸 수 없다")
    void onlyPendingAwaitsDecision() {
        TopicInputLog pending = TopicInputLog.candidatesPresented("강화학습", List.of(1L), 2L, "강화학습", "rl", "user-1");

        assertThat(pending.awaitsUserDecision()).isTrue();
        assertThat(pending.resolvedByUser(1L).awaitsUserDecision()).isFalse();
        assertThat(pending.resolvedByNewTopic(5L).awaitsUserDecision()).isFalse();
        assertThat(pending.escalatedToReview().awaitsUserDecision()).isFalse();
    }

    @Test
    @DisplayName("사용자 확인과 신규 등록이 서로 다른 결말로 남는다 — 뼈대가 왜 생겼는지의 근거다")
    void outcomesAreDistinct() {
        TopicInputLog pending = TopicInputLog.candidatesPresented("강화학습", List.of(1L), 2L, "강화학습", "rl", "user-1");

        assertThat(pending.resolvedByUser(1L).resolution()).isEqualTo(TopicInputResolution.MATCHED);
        assertThat(pending.resolvedByUser(1L).matchSource()).isEqualTo(TopicMatchSource.USER_CONFIRMED);
        assertThat(pending.resolvedByNewTopic(5L).resolution()).isEqualTo(TopicInputResolution.NEW_TOPIC_CREATED);
        assertThat(pending.resolvedByNewTopic(5L).matchedTopicId()).isEqualTo(5L);
        assertThat(pending.escalatedToReview().resolution()).isEqualTo(TopicInputResolution.NEEDS_REVIEW);
    }

    @Test
    @DisplayName("결말이 바뀌어도 제안 정보는 남는다 — 거절 경로가 그 값으로 주제를 만든다")
    void keepsProposalAcrossTransition() {
        TopicInputLog pending = TopicInputLog.candidatesPresented("강화학습", List.of(1L), 2L, "강화학습", "rl", "user-1");

        TopicInputLog escalated = pending.escalatedToReview();

        assertThat(escalated.proposedFieldId()).isEqualTo(2L);
        assertThat(escalated.proposedTopicName()).isEqualTo("강화학습");
        assertThat(escalated.proposedTopicSlug()).isEqualTo("rl");
        assertThat(escalated.candidateTopicIds()).containsExactly(1L);
    }

    @Test
    @DisplayName("후보 목록은 복제된다 — 넘긴 리스트를 고쳐도 기록이 바뀌지 않는다")
    void copiesCandidateIds() {
        List<Long> candidates = new ArrayList<>(List.of(1L, 2L));

        TopicInputLog log = TopicInputLog.candidatesPresented("입력", candidates, null, null, null, null);
        candidates.add(3L);

        assertThat(log.candidateTopicIds()).containsExactly(1L, 2L);
    }

    @Test
    @DisplayName("검토 표시는 시각과 검토자를 함께 남긴다")
    void reviewRecordsWhoAndWhen() {
        TopicInputLog reviewed =
                TopicInputLog.needsReview("바이올린", null, null, "user-1").reviewed("admin@nextchapter.local", "기각");

        assertThat(reviewed.isReviewed()).isTrue();
        assertThat(reviewed.reviewedAt()).isNotNull();
        assertThat(reviewed.reviewedBy()).isEqualTo("admin@nextchapter.local");
    }

    @Test
    @DisplayName("빈 입력은 기록하지 않는다")
    void rejectsBlankInput() {
        assertThatThrownBy(() -> TopicInputLog.needsReview("   ", null, null, null))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
