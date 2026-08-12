package com.mindplates.nextchapter.application.layer.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.mindplates.nextchapter.application.chapter.port.out.LoadChapterGraphPort;
import com.mindplates.nextchapter.application.chapter.port.out.LoadChapterGraphPort.GraphChapter;
import com.mindplates.nextchapter.application.chapter.port.out.LoadChapterGraphPort.GraphEdge;
import com.mindplates.nextchapter.application.chapter.port.out.LoadChapterGraphPort.Neighborhood;
import com.mindplates.nextchapter.application.chapter.port.out.LoadChapterPort;
import com.mindplates.nextchapter.application.layer.port.out.LoadChapterUnderstandingPort;
import com.mindplates.nextchapter.application.layer.view.NextChapterView;
import com.mindplates.nextchapter.application.skeleton.port.out.LoadSkeletonPort;
import com.mindplates.nextchapter.application.skeleton.service.PublishedSkeletonGuard;
import com.mindplates.nextchapter.common.exception.EntityNotFoundException;
import com.mindplates.nextchapter.domain.chapter.model.Chapter;
import com.mindplates.nextchapter.domain.chapter.model.ChapterRelationType;
import com.mindplates.nextchapter.domain.layer.model.ChapterUnderstanding;
import com.mindplates.nextchapter.domain.layer.model.RecommendationReason;
import com.mindplates.nextchapter.domain.layer.model.UnderstandingLevel;
import com.mindplates.nextchapter.domain.skeleton.model.Skeleton;
import com.mindplates.nextchapter.domain.skeleton.model.SkeletonStatus;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * 규칙은 도메인이 검증한다. 여기서 확인하는 것은 <b>두 저장소의 값을 그 함수의 입력으로 맞추는 부분</b>이다 —
 * 특히 간선의 <b>방향</b>이 뒤집히면 되돌리기와 전진이 뒤섞이고, 그건 에러 없이 나쁜 추천이 된다.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("다음 챕터 추천")
class NextChapterServiceTest {

    @Mock
    LoadSkeletonPort loadSkeletonPort;

    @Mock
    LoadChapterPort loadChapterPort;

    @Mock
    LoadChapterGraphPort loadChapterGraphPort;

    @Mock
    LoadChapterUnderstandingPort loadChapterUnderstandingPort;

    NextChapterService service;

    @BeforeEach
    void setUp() {
        service = new NextChapterService(
                new PublishedSkeletonGuard(loadSkeletonPort),
                loadChapterPort,
                loadChapterGraphPort,
                loadChapterUnderstandingPort);
    }

    private static GraphChapter node(Long id, String key) {
        return new GraphChapter(id, 5L, key, "제목-" + key, 1);
    }

    private void stubPublishedChapter() {
        when(loadChapterPort.findById(100L))
                .thenReturn(Optional.of(new Chapter(100L, 5L, "current", "현재", null, 1, 3, 0, null, null)));
        when(loadSkeletonPort.findById(5L))
                .thenReturn(Optional.of(new Skeleton(5L, 77L, SkeletonStatus.PUBLISHED, null, null)));
    }

    /** prev → 현재 → next, 현재 → deep(DEEPENS) */
    private void stubGraph() {
        when(loadChapterGraphPort.neighborhood(100L, 1))
                .thenReturn(Optional.of(new Neighborhood(
                        node(100L, "current"),
                        1,
                        List.of(node(10L, "prev"), node(20L, "next"), node(30L, "deep")),
                        List.of(
                                new GraphEdge(10L, 100L, ChapterRelationType.PREREQUISITE),
                                new GraphEdge(100L, 20L, ChapterRelationType.PREREQUISITE),
                                new GraphEdge(100L, 30L, ChapterRelationType.DEEPENS)))));
    }

    private void stubUnderstanding(ChapterUnderstanding... understandings) {
        when(loadChapterUnderstandingPort.summarizeBySkeleton(42L, 5L)).thenReturn(List.of(understandings));
    }

    /** 방향이 뒤집히면 잘 이해한 사용자를 선행으로 되돌린다. */
    @Test
    @DisplayName("들어오는 선행은 되돌리기, 나가는 선행은 다음 단계다")
    void mapsEdgeDirection() {
        stubPublishedChapter();
        stubGraph();
        stubUnderstanding(new ChapterUnderstanding(100L, 4, 1, 0, 50, LocalDateTime.now()));

        NextChapterView view = service.next(42L, 100L);

        assertThat(view.currentLevel()).isEqualTo(UnderstandingLevel.STRUGGLING);
        assertThat(view.candidates().getFirst().chapterId()).isEqualTo(10L);
        assertThat(view.candidates().getFirst().reason()).isEqualTo(RecommendationReason.PREREQUISITE_GAP);
    }

    /** 같은 그래프, 다른 이해도 → 다른 첫 후보. 개인 루프가 실제로 도는 증거다. */
    @Test
    @DisplayName("이해도가 높으면 심화가 먼저다")
    void confidentGetsDeepFirst() {
        stubPublishedChapter();
        stubGraph();
        stubUnderstanding(new ChapterUnderstanding(100L, 5, 5, 0, 100, LocalDateTime.now()));

        NextChapterView view = service.next(42L, 100L);

        assertThat(view.currentLevel()).isEqualTo(UnderstandingLevel.CONFIDENT);
        assertThat(view.candidates().getFirst().chapterId()).isEqualTo(30L);
        assertThat(view.candidates().getFirst().reason()).isEqualTo(RecommendationReason.DEEPEN);
    }

    @Test
    @DisplayName("후보에 제목과 이해도가 함께 실린다")
    void carriesTitleAndLevel() {
        stubPublishedChapter();
        stubGraph();
        stubUnderstanding(
                new ChapterUnderstanding(100L, 4, 3, 0, 50, LocalDateTime.now()),
                new ChapterUnderstanding(20L, 4, 4, 0, 100, LocalDateTime.now()));

        NextChapterView view = service.next(42L, 100L);

        NextChapterView.Candidate reviewed = view.candidates().stream()
                .filter(candidate -> candidate.chapterId().equals(20L))
                .findFirst()
                .orElseThrow();
        assertThat(reviewed.title()).isEqualTo("제목-next");
        assertThat(reviewed.completed()).isTrue();
        assertThat(reviewed.reason()).isEqualTo(RecommendationReason.REVIEW);
        assertThat(reviewed.level()).isEqualTo(UnderstandingLevel.CONFIDENT);
    }

    /** 신호가 없는 사용자를 되돌리면 진입 자체가 막힌다. */
    @Test
    @DisplayName("아무 기록이 없으면 앞으로 보낸다")
    void freshLearnerGoesForward() {
        stubPublishedChapter();
        stubGraph();
        stubUnderstanding();

        NextChapterView view = service.next(42L, 100L);

        assertThat(view.currentLevel()).isEqualTo(UnderstandingLevel.UNKNOWN);
        assertThat(view.candidates().getFirst().chapterId()).isEqualTo(20L);
    }

    @Test
    @DisplayName("공개되지 않은 뼈대는 추천하지 않는다")
    void hidesUnpublished() {
        when(loadChapterPort.findById(100L))
                .thenReturn(Optional.of(new Chapter(100L, 5L, "current", "현재", null, 1, 3, 0, null, null)));
        when(loadSkeletonPort.findById(5L))
                .thenReturn(Optional.of(new Skeleton(5L, 77L, SkeletonStatus.GENERATING_ASSETS, null, null)));

        assertThatThrownBy(() -> service.next(42L, 100L)).isInstanceOf(EntityNotFoundException.class);
    }

    /** published 뼈대인데 그래프에 노드가 없으면 파생 저장소 유실이다 — "갈 곳이 없다"와 구분해야 한다. */
    @Test
    @DisplayName("그래프에 노드가 없으면 빈 추천이 아니라 404 다")
    void missingGraphNode() {
        stubPublishedChapter();
        when(loadChapterGraphPort.neighborhood(100L, 1)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.next(42L, 100L))
                .isInstanceOf(EntityNotFoundException.class)
                .hasMessageContaining("챕터 그래프");
    }

    @Test
    @DisplayName("고립된 챕터는 후보가 없다")
    void isolatedChapter() {
        stubPublishedChapter();
        when(loadChapterGraphPort.neighborhood(100L, 1))
                .thenReturn(Optional.of(new Neighborhood(node(100L, "current"), 1, List.of(), List.of())));
        stubUnderstanding();

        assertThat(service.next(42L, 100L).candidates()).isEmpty();
    }
}
