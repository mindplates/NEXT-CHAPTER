package com.mindplates.nextchapter.domain.layer.model;

import static org.assertj.core.api.Assertions.assertThat;

import com.mindplates.nextchapter.domain.chapter.model.ChapterRelationType;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * <b>완료 기준 1번이 여기서 값으로 고정된다</b> — 이해도가 다른 두 사용자가 같은 지점에서 서로 다른 순서를
 * 받는다. 규칙을 서비스에 두면 이 사실을 확인하려고 그래프와 신호를 함께 세워야 한다.
 *
 * <pre>
 *   prev ──PREREQUISITE──▶ 현재 ──PREREQUISITE──▶ next
 *                            │
 *                            ├──DEEPENS──▶ deep
 *                            └──RELATED──▶ side
 * </pre>
 */
@DisplayName("다음 챕터 선택")
class NextChapterChoiceTest {

    private static final Long PREV = 10L;
    private static final Long NEXT = 20L;
    private static final Long DEEP = 30L;
    private static final Long SIDE = 40L;

    private static List<NextChapterChoice.Neighbor> neighbors() {
        return List.of(
                new NextChapterChoice.Neighbor(PREV, ChapterRelationType.PREREQUISITE, true),
                new NextChapterChoice.Neighbor(NEXT, ChapterRelationType.PREREQUISITE, false),
                new NextChapterChoice.Neighbor(DEEP, ChapterRelationType.DEEPENS, false),
                new NextChapterChoice.Neighbor(SIDE, ChapterRelationType.RELATED, false));
    }

    private static ChapterUnderstanding understanding(Long chapterId, int attempts, int correct, int percent) {
        return new ChapterUnderstanding(chapterId, attempts, correct, 0, percent, LocalDateTime.now());
    }

    private static List<Long> order(UnderstandingLevel level, Map<Long, ChapterUnderstanding> understandings) {
        return NextChapterChoice.rank(level, neighbors(), understandings).stream()
                .map(NextChapterChoice.Recommendation::chapterId)
                .toList();
    }

    /** 막힌 사용자를 앞으로 밀면 같은 지점에서 다시 막힌다. */
    @Test
    @DisplayName("막혀 있으면 선행 챕터를 먼저 준다")
    void strugglingGoesBack() {
        List<NextChapterChoice.Recommendation> ranked =
                NextChapterChoice.rank(UnderstandingLevel.STRUGGLING, neighbors(), Map.of());

        assertThat(ranked.getFirst().chapterId()).isEqualTo(PREV);
        assertThat(ranked.getFirst().reason()).isEqualTo(RecommendationReason.PREREQUISITE_GAP);
    }

    /** 선행을 이미 충분히 이해했다면 되돌려도 아는 것을 다시 읽게 된다 — 막힘이 풀리지 않는다. */
    @Test
    @DisplayName("선행을 이미 이해했으면 되돌리지 않는다")
    void doesNotSendBackToMasteredPrerequisite() {
        Map<Long, ChapterUnderstanding> understandings = Map.of(PREV, understanding(PREV, 5, 5, 100));

        List<NextChapterChoice.Recommendation> ranked =
                NextChapterChoice.rank(UnderstandingLevel.STRUGGLING, neighbors(), understandings);

        assertThat(ranked.getFirst().chapterId()).isNotEqualTo(PREV);
    }

    /** 같은 지점, 다른 이해도 → 다른 순서. 이것이 개인 루프가 실제로 도는 증거다. */
    @Test
    @DisplayName("이해도가 다르면 순서가 다르다")
    void levelChangesOrder() {
        List<Long> struggling = order(UnderstandingLevel.STRUGGLING, Map.of());
        List<Long> confident = order(UnderstandingLevel.CONFIDENT, Map.of());

        assertThat(struggling).isNotEqualTo(confident);
        assertThat(struggling.getFirst()).isEqualTo(PREV);
        assertThat(confident.getFirst()).isEqualTo(DEEP);
    }

    @Test
    @DisplayName("충분히 이해했으면 심화를 먼저 준다")
    void confidentGoesDeeper() {
        List<NextChapterChoice.Recommendation> ranked =
                NextChapterChoice.rank(UnderstandingLevel.CONFIDENT, neighbors(), Map.of());

        assertThat(ranked.getFirst().reason()).isEqualTo(RecommendationReason.DEEPEN);
        assertThat(ranked.get(1).chapterId()).isEqualTo(NEXT);
    }

    @Test
    @DisplayName("보통이면 다음 단계를 먼저 준다")
    void learningGoesForward() {
        List<NextChapterChoice.Recommendation> ranked =
                NextChapterChoice.rank(UnderstandingLevel.LEARNING, neighbors(), Map.of());

        assertThat(ranked.getFirst().chapterId()).isEqualTo(NEXT);
        assertThat(ranked.getFirst().reason()).isEqualTo(RecommendationReason.NEXT_STEP);
    }

    /** 처음 온 사용자를 되돌리면 진입 자체가 막힌다 — UNKNOWN 은 낮은 것이 아니다. */
    @Test
    @DisplayName("이해도를 모르면 앞으로 보낸다")
    void unknownGoesForward() {
        assertThat(order(UnderstandingLevel.UNKNOWN, Map.of()).getFirst()).isEqualTo(NEXT);
    }

    /** 아직 보지 않은 챕터가 있는데 복습을 먼저 내밀면 진행이 멈춘 것처럼 보인다. */
    @Test
    @DisplayName("끝까지 본 챕터는 뒤로 밀리고 이유가 REVIEW 다")
    void completedGoesLast() {
        Map<Long, ChapterUnderstanding> understandings = Map.of(NEXT, understanding(NEXT, 4, 4, 100));

        List<NextChapterChoice.Recommendation> ranked =
                NextChapterChoice.rank(UnderstandingLevel.LEARNING, neighbors(), understandings);

        assertThat(ranked.getFirst().chapterId()).isNotEqualTo(NEXT);
        assertThat(ranked.stream()
                        .filter(recommendation -> recommendation.chapterId().equals(NEXT))
                        .findFirst()
                        .orElseThrow()
                        .reason())
                .isEqualTo(RecommendationReason.REVIEW);
    }

    /** 같은 챕터가 두 번 나오면 화면에 같은 카드가 두 개 보인다. */
    @Test
    @DisplayName("두 관계로 연결된 챕터도 한 번만 나온다")
    void deduplicates() {
        List<NextChapterChoice.Neighbor> doubled = List.of(
                new NextChapterChoice.Neighbor(NEXT, ChapterRelationType.PREREQUISITE, false),
                new NextChapterChoice.Neighbor(NEXT, ChapterRelationType.RELATED, false));

        List<NextChapterChoice.Recommendation> ranked =
                NextChapterChoice.rank(UnderstandingLevel.LEARNING, doubled, Map.of());

        assertThat(ranked).hasSize(1);
        assertThat(ranked.getFirst().reason()).isEqualTo(RecommendationReason.NEXT_STEP);
    }

    @Test
    @DisplayName("이웃이 없으면 후보도 없다")
    void noNeighbors() {
        assertThat(NextChapterChoice.rank(UnderstandingLevel.LEARNING, List.of(), Map.of()))
                .isEmpty();
    }

    /** 모든 이웃이 한 번은 나와야 한다 — 규칙에 걸리지 않은 이웃이 조용히 사라지면 갈 곳이 줄어든다. */
    @Test
    @DisplayName("모든 이웃이 후보에 들어간다")
    void keepsEveryNeighbor() {
        assertThat(order(UnderstandingLevel.LEARNING, Map.of())).containsExactlyInAnyOrder(PREV, NEXT, DEEP, SIDE);
    }
}
