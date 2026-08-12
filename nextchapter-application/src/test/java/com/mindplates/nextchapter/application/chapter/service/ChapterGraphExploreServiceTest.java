package com.mindplates.nextchapter.application.chapter.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.mindplates.nextchapter.application.chapter.port.out.LoadChapterGraphPort;
import com.mindplates.nextchapter.application.chapter.port.out.LoadChapterGraphPort.GraphChapter;
import com.mindplates.nextchapter.application.chapter.port.out.LoadChapterGraphPort.GraphEdge;
import com.mindplates.nextchapter.application.chapter.port.out.LoadChapterGraphPort.Neighborhood;
import com.mindplates.nextchapter.application.chapter.port.out.LoadChapterPort;
import com.mindplates.nextchapter.application.chapter.view.ChapterNeighborhoodView;
import com.mindplates.nextchapter.application.chapter.view.ChapterNodeView;
import com.mindplates.nextchapter.application.chapter.view.SkeletonEntryView;
import com.mindplates.nextchapter.application.skeleton.port.out.LoadSkeletonPort;
import com.mindplates.nextchapter.common.exception.EntityNotFoundException;
import com.mindplates.nextchapter.domain.chapter.model.Chapter;
import com.mindplates.nextchapter.domain.chapter.model.ChapterRelationType;
import com.mindplates.nextchapter.domain.skeleton.model.Skeleton;
import com.mindplates.nextchapter.domain.skeleton.model.SkeletonStatus;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * 이 서비스가 지키는 것은 <b>공개되지 않은 뼈대가 사용자에게 새지 않는 것</b>이다. 생성 중인 그래프는
 * 간선이 덜 들어와 있을 수 있고, 그 상태를 내리면 "관련 챕터 없음"으로 보인다 — 에러가 아니라 잘못된
 * 콘텐츠다.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("사용자 그래프 탐색")
class ChapterGraphExploreServiceTest {

    @Mock
    LoadSkeletonPort loadSkeletonPort;

    @Mock
    LoadChapterPort loadChapterPort;

    @Mock
    LoadChapterGraphPort loadChapterGraphPort;

    @InjectMocks
    ChapterGraphExploreService service;

    private static Skeleton skeleton(SkeletonStatus status) {
        return new Skeleton(5L, 77L, status, null, null);
    }

    private static Chapter chapter() {
        return new Chapter(100L, 5L, "a", "가", "요약", 2, 3, 0, null, null);
    }

    private static GraphChapter node(Long id, String key) {
        return new GraphChapter(id, 5L, key, "제목-" + key, 1);
    }

    @Test
    @DisplayName("published 뼈대의 진입점을 준다")
    void returnsEntryPoints() {
        when(loadSkeletonPort.findById(5L)).thenReturn(Optional.of(skeleton(SkeletonStatus.PUBLISHED)));
        when(loadChapterGraphPort.entryPoints(5L)).thenReturn(List.of(node(100L, "a"), node(104L, "e")));

        SkeletonEntryView view = service.entryPointsBySkeletonId(5L);

        assertThat(view.skeletonId()).isEqualTo(5L);
        assertThat(view.topicId()).isEqualTo(77L);
        assertThat(view.entryPoints()).extracting(ChapterNodeView::chapterKey).containsExactly("a", "e");
    }

    @Test
    @DisplayName("주제로도 진입할 수 있다 — 사용자는 뼈대 ID 를 모른다")
    void entersByTopic() {
        when(loadSkeletonPort.findByTopicId(77L)).thenReturn(Optional.of(skeleton(SkeletonStatus.PUBLISHED)));
        when(loadChapterGraphPort.entryPoints(5L)).thenReturn(List.of(node(100L, "a")));

        assertThat(service.entryPointsByTopicId(77L).entryPoints()).hasSize(1);
    }

    /** 생성 중인 뼈대를 열면 미완성 그래프가 사용자 화면에 나간다. 그래프를 조회하지도 않아야 한다. */
    @Test
    @DisplayName("생성 중인 뼈대는 없는 것으로 답한다")
    void hidesGeneratingSkeleton() {
        when(loadSkeletonPort.findById(5L)).thenReturn(Optional.of(skeleton(SkeletonStatus.GENERATING_BODIES)));

        assertThatThrownBy(() -> service.entryPointsBySkeletonId(5L)).isInstanceOf(EntityNotFoundException.class);
        verify(loadChapterGraphPort, never()).entryPoints(anyLong());
    }

    @Test
    @DisplayName("실패한 뼈대도 없는 것으로 답한다")
    void hidesFailedSkeleton() {
        when(loadSkeletonPort.findByTopicId(77L)).thenReturn(Optional.of(skeleton(SkeletonStatus.FAILED)));

        assertThatThrownBy(() -> service.entryPointsByTopicId(77L)).isInstanceOf(EntityNotFoundException.class);
    }

    @Test
    @DisplayName("뼈대가 없는 주제는 404 다")
    void missingSkeletonIsNotFound() {
        when(loadSkeletonPort.findByTopicId(77L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.entryPointsByTopicId(77L)).isInstanceOf(EntityNotFoundException.class);
    }

    @Test
    @DisplayName("이웃과 간선을 그대로 옮긴다 — 적용된 홉 수가 함께 온다")
    void mapsNeighborhood() {
        when(loadChapterPort.findById(100L)).thenReturn(Optional.of(chapter()));
        when(loadSkeletonPort.findById(5L)).thenReturn(Optional.of(skeleton(SkeletonStatus.PUBLISHED)));
        when(loadChapterGraphPort.neighborhood(100L, 99))
                .thenReturn(Optional.of(new Neighborhood(
                        node(100L, "a"),
                        3,
                        List.of(node(101L, "b")),
                        List.of(new GraphEdge(100L, 101L, ChapterRelationType.PREREQUISITE)))));

        ChapterNeighborhoodView view = service.neighbors(100L, 99);

        assertThat(view.center().chapterId()).isEqualTo(100L);
        assertThat(view.depth()).isEqualTo(3);
        assertThat(view.neighbors()).extracting(ChapterNodeView::chapterKey).containsExactly("b");
        assertThat(view.relations())
                .extracting(ChapterNeighborhoodView.Relation::relationType)
                .containsExactly(ChapterRelationType.PREREQUISITE);
    }

    @Test
    @DisplayName("생성 중인 뼈대의 챕터 이웃은 보이지 않는다")
    void hidesNeighborsOfUnpublished() {
        when(loadChapterPort.findById(100L)).thenReturn(Optional.of(chapter()));
        when(loadSkeletonPort.findById(5L)).thenReturn(Optional.of(skeleton(SkeletonStatus.GENERATING_ASSETS)));

        assertThatThrownBy(() -> service.neighbors(100L, 2)).isInstanceOf(EntityNotFoundException.class);
        verify(loadChapterGraphPort, never()).neighborhood(anyLong(), anyInt());
    }

    @Test
    @DisplayName("없는 챕터는 404 다")
    void missingChapterIsNotFound() {
        when(loadChapterPort.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.neighbors(999L, 2)).isInstanceOf(EntityNotFoundException.class);
    }

    /**
     * published 뼈대인데 그래프에 노드가 없는 것은 파생 저장소 유실이다. 빈 이웃으로 답하면 "관련 챕터
     * 없음"과 구분되지 않아 그 유실이 조용히 지나간다.
     */
    @Test
    @DisplayName("그래프에 노드가 없으면 빈 이웃이 아니라 404 다")
    void missingGraphNodeIsNotFound() {
        when(loadChapterPort.findById(100L)).thenReturn(Optional.of(chapter()));
        when(loadSkeletonPort.findById(5L)).thenReturn(Optional.of(skeleton(SkeletonStatus.PUBLISHED)));
        when(loadChapterGraphPort.neighborhood(100L, 2)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.neighbors(100L, 2))
                .isInstanceOf(EntityNotFoundException.class)
                .hasMessageContaining("챕터 그래프");
    }
}
