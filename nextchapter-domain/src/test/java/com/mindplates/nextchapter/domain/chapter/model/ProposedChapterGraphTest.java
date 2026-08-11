package com.mindplates.nextchapter.domain.chapter.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 그래프 검증을 생성 시점에 두는 이유는 잘못된 그래프가 <b>조용히</b> 문제를 만들기 때문이다. 순환·끊어진
 * 간선·고립은 저장은 성공하고 소비 시점에 "빈 추천"으로 나타나는데, 그때는 본문·자산 생성비가 다 든 뒤다.
 */
@DisplayName("챕터 그래프 제안")
class ProposedChapterGraphTest {

    @Test
    @DisplayName("정상 그래프는 진입점을 알려 준다")
    void reportsEntryPoints() {
        ProposedChapterGraph graph = new ProposedChapterGraph(
                chapters("loss-function", "gradient-descent", "learning-rate", "overfitting"),
                List.of(
                        prerequisite("loss-function", "gradient-descent"),
                        prerequisite("gradient-descent", "learning-rate"),
                        related("overfitting", "learning-rate")));

        assertThat(graph.entryPointKeys()).containsExactly("loss-function", "overfitting");
    }

    /** 순환이 있으면 어느 챕터도 선행 조건을 만족시킬 수 없어 진입점이 사라진다. */
    @Test
    @DisplayName("선행 관계의 순환을 거절한다")
    void rejectsPrerequisiteCycle() {
        assertThatThrownBy(() -> new ProposedChapterGraph(
                        chapters("a", "b", "c", "d"),
                        List.of(prerequisite("a", "b"), prerequisite("b", "c"), prerequisite("c", "a"))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("순환");
    }

    @Test
    @DisplayName("두 챕터 사이의 맞물린 선행도 순환이다")
    void rejectsTwoNodeCycle() {
        assertThatThrownBy(() -> new ProposedChapterGraph(
                        chapters("a", "b", "c", "d"), List.of(prerequisite("a", "b"), prerequisite("b", "a"))))
                .isInstanceOf(IllegalArgumentException.class);
    }

    /** 순서를 주장하지 않는 타입은 순환이 문제가 되지 않는다. */
    @Test
    @DisplayName("related·deepens 의 순환은 허용한다")
    void allowsCycleForSoftRelations() {
        ProposedChapterGraph graph = new ProposedChapterGraph(
                chapters("a", "b", "c", "d"),
                List.of(
                        related("a", "b"),
                        related("b", "a"),
                        new ProposedRelation("c", "d", ChapterRelationType.DEEPENS),
                        new ProposedRelation("d", "c", ChapterRelationType.DEEPENS)));

        assertThat(graph.relations()).hasSize(4);
        assertThat(graph.entryPointKeys()).hasSize(4);
    }

    @Test
    @DisplayName("없는 챕터를 가리키는 관계를 거절한다 — 끊어진 간선은 탐색을 조용히 막는다")
    void rejectsDanglingRelation() {
        assertThatThrownBy(
                        () -> new ProposedChapterGraph(chapters("a", "b", "c", "d"), List.of(prerequisite("a", "zzz"))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("없는 챕터");
    }

    @Test
    @DisplayName("자기 자신에 대한 관계를 거절한다")
    void rejectsSelfRelation() {
        assertThatThrownBy(
                        () -> new ProposedChapterGraph(chapters("a", "b", "c", "d"), List.of(prerequisite("a", "a"))))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("챕터 키 중복을 거절한다")
    void rejectsDuplicateKeys() {
        List<ProposedChapter> duplicated = new ArrayList<>(chapters("a", "b", "c"));
        duplicated.add(new ProposedChapter("a", "다른 제목", null, 3));

        assertThatThrownBy(() -> new ProposedChapterGraph(duplicated, List.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("중복");
    }

    @Test
    @DisplayName("챕터가 너무 적으면 거절한다 — 그래프라고 부를 것이 없다")
    void rejectsTooFewChapters() {
        assertThatThrownBy(() -> new ProposedChapterGraph(chapters("a", "b"), List.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(String.valueOf(ProposedChapterGraph.MIN_CHAPTERS));
    }

    /** 넘으면 생성비가 뼈대 하나에 몰리고 챕터당 신호가 얇아져 집단 루프가 임계치를 넘기 어려워진다. */
    @Test
    @DisplayName("챕터가 상한을 넘으면 거절한다")
    void rejectsTooManyChapters() {
        List<ProposedChapter> tooMany = new ArrayList<>();
        for (int index = 0; index <= ProposedChapterGraph.MAX_CHAPTERS; index++) {
            tooMany.add(new ProposedChapter("chapter-" + index, "제목 " + index, null, index));
        }

        assertThatThrownBy(() -> new ProposedChapterGraph(tooMany, List.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(String.valueOf(ProposedChapterGraph.MAX_CHAPTERS));
    }

    @Test
    @DisplayName("관계가 없어도 그래프는 성립한다 — 모든 챕터가 진입점이다")
    void graphWithoutRelations() {
        ProposedChapterGraph graph = new ProposedChapterGraph(chapters("a", "b", "c", "d"), null);

        assertThat(graph.relations()).isEmpty();
        assertThat(graph.entryPointKeys()).hasSize(4);
    }

    @Test
    @DisplayName("긴 선행 사슬은 순환이 아니다")
    void longChainIsNotCycle() {
        ProposedChapterGraph graph = new ProposedChapterGraph(
                chapters("a", "b", "c", "d", "e"),
                List.of(
                        prerequisite("a", "b"),
                        prerequisite("b", "c"),
                        prerequisite("c", "d"),
                        prerequisite("d", "e")));

        assertThat(graph.entryPointKeys()).containsExactly("a");
    }

    @Test
    @DisplayName("두 진입점에서 갈라져 한 챕터로 합쳐지는 그래프도 순환이 아니다")
    void diamondIsNotCycle() {
        ProposedChapterGraph graph = new ProposedChapterGraph(
                chapters("a", "b", "c", "d"),
                List.of(
                        prerequisite("a", "b"),
                        prerequisite("a", "c"),
                        prerequisite("b", "d"),
                        prerequisite("c", "d")));

        assertThat(graph.entryPointKeys()).containsExactly("a");
    }

    private static List<ProposedChapter> chapters(String... keys) {
        List<ProposedChapter> chapters = new ArrayList<>();
        for (int index = 0; index < keys.length; index++) {
            chapters.add(new ProposedChapter(keys[index], "제목-" + keys[index], "요약", index));
        }
        return chapters;
    }

    private static ProposedRelation prerequisite(String from, String to) {
        return new ProposedRelation(from, to, ChapterRelationType.PREREQUISITE);
    }

    private static ProposedRelation related(String from, String to) {
        return new ProposedRelation(from, to, ChapterRelationType.RELATED);
    }
}
