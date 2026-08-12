package com.mindplates.nextchapter.application.layer.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.mindplates.nextchapter.application.chapter.port.out.LoadChapterPort;
import com.mindplates.nextchapter.application.layer.port.out.LoadChapterUnderstandingPort;
import com.mindplates.nextchapter.application.layer.view.ChapterUnderstandingView;
import com.mindplates.nextchapter.application.layer.view.SkeletonLayerView;
import com.mindplates.nextchapter.application.skeleton.port.out.LoadSkeletonPort;
import com.mindplates.nextchapter.application.skeleton.service.PublishedSkeletonGuard;
import com.mindplates.nextchapter.common.exception.EntityNotFoundException;
import com.mindplates.nextchapter.domain.chapter.model.Chapter;
import com.mindplates.nextchapter.domain.layer.model.ChapterUnderstanding;
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
 * 레이어 조회가 지켜야 하는 것은 <b>분모가 사라지지 않는 것</b>이다. 본 챕터만 내리면 "전체 중 어디까지"를
 * 보여 줄 수 없고, 끝까지 소비된 비율(3개월 성공 기준 3번)도 계산할 수 없다.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("사용자 레이어 조회")
class ChapterLayerServiceTest {

    @Mock
    LoadSkeletonPort loadSkeletonPort;

    @Mock
    LoadChapterPort loadChapterPort;

    @Mock
    LoadChapterUnderstandingPort loadChapterUnderstandingPort;

    ChapterLayerService service;

    @BeforeEach
    void setUp() {
        service = new ChapterLayerService(
                new PublishedSkeletonGuard(loadSkeletonPort), loadChapterPort, loadChapterUnderstandingPort);
    }

    private static Chapter chapter(Long id, String key) {
        return new Chapter(id, 5L, key, "제목-" + key, null, 1, 3, 0, null, null);
    }

    private void stubPublished() {
        when(loadSkeletonPort.findById(5L))
                .thenReturn(Optional.of(new Skeleton(5L, 77L, SkeletonStatus.PUBLISHED, null, null)));
    }

    /** 보지 않은 챕터가 목록에서 빠지면 전체 진행을 계산할 수 없다. */
    @Test
    @DisplayName("보지 않은 챕터도 빈 이해도로 목록에 있다")
    void includesUnvisitedChapters() {
        stubPublished();
        when(loadChapterPort.findBySkeletonId(5L))
                .thenReturn(List.of(chapter(100L, "a"), chapter(101L, "b"), chapter(102L, "c")));
        when(loadChapterUnderstandingPort.summarizeBySkeleton(42L, 5L))
                .thenReturn(List.of(new ChapterUnderstanding(100L, 4, 4, 0, 100, LocalDateTime.now())));

        SkeletonLayerView view = service.bySkeletonId(42L, 5L);

        assertThat(view.totalChapters()).isEqualTo(3);
        assertThat(view.visitedChapters()).isEqualTo(1);
        assertThat(view.completedChapters()).isEqualTo(1);
        assertThat(view.chapters())
                .extracting(ChapterUnderstandingView::chapterId)
                .containsExactly(100L, 101L, 102L);
        assertThat(view.chapters().get(1).level()).isEqualTo(UnderstandingLevel.UNKNOWN);
        assertThat(view.chapters().get(1).lastSeenAt()).isNull();
    }

    /** 구간과 원자료를 함께 내려야 화면이 "왜 그렇게 판정됐나"를 보여 줄 수 있다. */
    @Test
    @DisplayName("구간과 원자료가 함께 나온다")
    void carriesLevelAndRawCounts() {
        stubPublished();
        when(loadChapterPort.findBySkeletonId(5L)).thenReturn(List.of(chapter(100L, "a")));
        when(loadChapterUnderstandingPort.summarizeBySkeleton(42L, 5L))
                .thenReturn(List.of(new ChapterUnderstanding(100L, 4, 1, 2, 60, LocalDateTime.now())));

        ChapterUnderstandingView view = service.bySkeletonId(42L, 5L).chapters().getFirst();

        assertThat(view.attempts()).isEqualTo(4);
        assertThat(view.correct()).isEqualTo(1);
        assertThat(view.questions()).isEqualTo(2);
        assertThat(view.progressPercent()).isEqualTo(60);
        assertThat(view.level()).isEqualTo(UnderstandingLevel.STRUGGLING);
        assertThat(view.title()).isEqualTo("제목-a");
    }

    @Test
    @DisplayName("챕터 하나만 볼 수도 있다")
    void singleChapter() {
        stubPublished();
        when(loadChapterPort.findById(100L)).thenReturn(Optional.of(chapter(100L, "a")));
        when(loadChapterUnderstandingPort.findByChapter(42L, 100L)).thenReturn(ChapterUnderstanding.none(100L));

        ChapterUnderstandingView view = service.byChapterId(42L, 100L);

        assertThat(view.chapterId()).isEqualTo(100L);
        assertThat(view.level()).isEqualTo(UnderstandingLevel.UNKNOWN);
    }

    @Test
    @DisplayName("공개되지 않은 뼈대의 레이어는 볼 수 없다")
    void hidesUnpublished() {
        when(loadSkeletonPort.findById(5L))
                .thenReturn(Optional.of(new Skeleton(5L, 77L, SkeletonStatus.GENERATING_BODIES, null, null)));

        assertThatThrownBy(() -> service.bySkeletonId(42L, 5L)).isInstanceOf(EntityNotFoundException.class);
    }

    @Test
    @DisplayName("없는 챕터는 404 다")
    void missingChapter() {
        when(loadChapterPort.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.byChapterId(42L, 999L)).isInstanceOf(EntityNotFoundException.class);
    }
}
