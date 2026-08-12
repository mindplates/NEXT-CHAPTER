package com.mindplates.nextchapter.domain.layer.model;

import com.mindplates.nextchapter.domain.chapter.model.ChapterRelationType;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * "다음에 볼 챕터"의 규칙 — 순수 함수다.
 *
 * <p>도메인에 두는 이유는 이 규칙이 <b>제품의 핵심 동작</b>이고 저장소와 무관하기 때문이다. 서비스에 두면
 * 규칙을 확인하려고 그래프와 신호를 함께 세워야 하고, 그러면 "이해도가 다른 두 사용자가 다른 경로를
 * 받는다"(완료 기준 1번)를 값으로 고정할 수 없다.
 *
 * <p>규칙의 순서가 곧 설계다.
 *
 * <ol>
 *   <li><b>막혀 있으면 되돌린다</b> — 앞으로 밀어도 같은 지점에서 다시 막힌다
 *   <li>충분히 이해했으면 심화를 먼저 — 다음 단계는 어차피 열려 있다
 *   <li>그다음이 다음 단계(이 챕터를 선행으로 두는 챕터)
 *   <li>순서 없는 연관
 *   <li>이미 끝낸 챕터는 <b>맨 뒤</b> — 다른 후보가 없을 때만 제시한다
 * </ol>
 */
public final class NextChapterChoice {

    private NextChapterChoice() {}

    /**
     * @param chapterId 이웃 챕터
     * @param relation 관계 종류
     * @param incoming 이웃 → 현재 챕터 방향인지. 들어오는 {@link ChapterRelationType#PREREQUISITE} 가
     *     "이 챕터의 선행"이고, 나가는 것이 "다음 단계"다 — 방향을 잃으면 되돌리기와 전진이 뒤섞인다
     */
    public record Neighbor(Long chapterId, ChapterRelationType relation, boolean incoming) {}

    public record Recommendation(Long chapterId, RecommendationReason reason) {}

    /**
     * @param understandings 챕터별 이해도. 없는 챕터는 아직 아무 반응이 없다는 뜻이다
     * @return 우선순위 순서. 같은 챕터가 두 번 나오지 않는다 — 먼저 매긴 이유를 유지한다
     */
    public static List<Recommendation> rank(
            UnderstandingLevel currentLevel, List<Neighbor> neighbors, Map<Long, ChapterUnderstanding> understandings) {
        List<Recommendation> ranked = new ArrayList<>();
        Set<Long> taken = new LinkedHashSet<>();

        if (currentLevel == UnderstandingLevel.STRUGGLING) {
            // 선행이 이미 충분한데 되돌리면 아는 것을 다시 읽게 된다 — 그건 막힘을 풀지 못한다.
            add(
                    ranked,
                    taken,
                    neighbors,
                    understandings,
                    RecommendationReason.PREREQUISITE_GAP,
                    neighbor -> neighbor.incoming()
                            && neighbor.relation() == ChapterRelationType.PREREQUISITE
                            && levelOf(understandings, neighbor) != UnderstandingLevel.CONFIDENT);
        }

        if (currentLevel == UnderstandingLevel.CONFIDENT) {
            add(
                    ranked,
                    taken,
                    neighbors,
                    understandings,
                    RecommendationReason.DEEPEN,
                    neighbor -> !neighbor.incoming() && neighbor.relation() == ChapterRelationType.DEEPENS);
        }

        add(
                ranked,
                taken,
                neighbors,
                understandings,
                RecommendationReason.NEXT_STEP,
                neighbor -> !neighbor.incoming() && neighbor.relation() == ChapterRelationType.PREREQUISITE);

        add(
                ranked,
                taken,
                neighbors,
                understandings,
                RecommendationReason.DEEPEN,
                neighbor -> !neighbor.incoming() && neighbor.relation() == ChapterRelationType.DEEPENS);

        add(
                ranked,
                taken,
                neighbors,
                understandings,
                RecommendationReason.RELATED,
                neighbor -> neighbor.relation() == ChapterRelationType.RELATED);

        // 남은 것 — 되돌릴 필요 없는 선행, 이미 끝낸 챕터. 맨 뒤에 복습으로 붙인다.
        add(ranked, taken, neighbors, understandings, RecommendationReason.REVIEW, neighbor -> true);
        return List.copyOf(ranked);
    }

    /**
     * 끝까지 본 챕터는 <b>앞 단계 전부를 건너뛰고</b> 마지막 복습 단계에서만 붙는다. 단계 안에서만 뒤로
     * 밀면 그 단계의 유일한 후보일 때 여전히 맨 앞에 오고, 아직 보지 않은 챕터가 있는데 복습이 먼저
     * 나오면 진행이 멈춘 것처럼 보인다.
     */
    private static void add(
            List<Recommendation> ranked,
            Set<Long> taken,
            List<Neighbor> neighbors,
            Map<Long, ChapterUnderstanding> understandings,
            RecommendationReason reason,
            java.util.function.Predicate<Neighbor> matches) {
        boolean review = reason == RecommendationReason.REVIEW;
        neighbors.stream()
                .filter(matches)
                .filter(neighbor -> review || !completed(understandings, neighbor))
                .forEach(neighbor -> {
                    if (taken.add(neighbor.chapterId())) {
                        ranked.add(new Recommendation(
                                neighbor.chapterId(),
                                completed(understandings, neighbor) ? RecommendationReason.REVIEW : reason));
                    }
                });
    }

    private static boolean completed(Map<Long, ChapterUnderstanding> understandings, Neighbor neighbor) {
        ChapterUnderstanding understanding = understandings.get(neighbor.chapterId());
        return understanding != null && understanding.completed();
    }

    private static UnderstandingLevel levelOf(Map<Long, ChapterUnderstanding> understandings, Neighbor neighbor) {
        ChapterUnderstanding understanding = understandings.get(neighbor.chapterId());
        return understanding == null ? UnderstandingLevel.UNKNOWN : understanding.level();
    }
}
