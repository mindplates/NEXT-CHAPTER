package com.mindplates.nextchapter.application.chapter.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.mindplates.nextchapter.application.chapter.port.out.ChapterDocumentCachePort;
import com.mindplates.nextchapter.application.chapter.port.out.ChapterDocumentCachePort.CachedChapterDocument;
import com.mindplates.nextchapter.application.chapter.port.out.LoadChapterPort;
import com.mindplates.nextchapter.application.chapter.port.out.LoadChapterVersionPort;
import com.mindplates.nextchapter.application.chapter.view.ComposedChapterView;
import com.mindplates.nextchapter.application.skeleton.port.out.LoadSkeletonPort;
import com.mindplates.nextchapter.application.skeleton.service.PublishedSkeletonGuard;
import com.mindplates.nextchapter.common.exception.EntityNotFoundException;
import com.mindplates.nextchapter.domain.chapter.model.Block;
import com.mindplates.nextchapter.domain.chapter.model.BlockDocument;
import com.mindplates.nextchapter.domain.chapter.model.BlockType;
import com.mindplates.nextchapter.domain.chapter.model.Chapter;
import com.mindplates.nextchapter.domain.chapter.model.ChapterVersion;
import com.mindplates.nextchapter.domain.chapter.model.ChapterVersionSource;
import com.mindplates.nextchapter.domain.chapter.model.DeliveryFormat;
import com.mindplates.nextchapter.domain.skeleton.model.Skeleton;
import com.mindplates.nextchapter.domain.skeleton.model.SkeletonStatus;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * 사용자 챕터 조회의 계약 세 가지를 고정한다 — <b>published 만</b>, <b>항상 최신 버전</b>, <b>블록 목록</b>.
 *
 * <p>버전을 응답에 싣는 것이 특히 중요하다. 신호가 "사용자가 실제로 소비한 버전"을 기록해야 하고, 무조건
 * 최신으로 기록하면 구버전을 본 사람의 오답이 최신 버전의 실패로 잘못 집계된다.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("챕터 합성 조회")
class ChapterCompositionServiceTest {

    @Mock
    LoadSkeletonPort loadSkeletonPort;

    @Mock
    LoadChapterPort loadChapterPort;

    @Mock
    LoadChapterVersionPort loadChapterVersionPort;

    @Mock
    ChapterDocumentCachePort chapterDocumentCachePort;

    ChapterCompositionService service;

    @BeforeEach
    void setUp() {
        service = new ChapterCompositionService(
                new PublishedSkeletonGuard(loadSkeletonPort),
                loadChapterPort,
                new SharedChapterDocuments(loadChapterVersionPort, chapterDocumentCachePort));
    }

    private static Chapter chapter() {
        return new Chapter(100L, 5L, "gradient-descent", "경사하강법", "요약", 2, 4, 0, null, null);
    }

    private static ChapterVersion version() {
        return new ChapterVersion(
                1L,
                100L,
                2,
                BlockDocument.of(
                        Block.text("b1", BlockType.HEADING, "경사하강법이란"),
                        Block.text("b2", BlockType.PARAGRAPH, "손실함수를"),
                        Block.text("b3", BlockType.NARRATION, "이제 학습률을"),
                        Block.figure("b4", java.util.Map.of("type", "plot"))),
                "b2 설명 보강",
                ChapterVersionSource.REVISED_BY_SIGNAL,
                "signal-loop",
                LocalDateTime.of(2026, 8, 12, 9, 0));
    }

    /** 캐시 미스 상태. 원천에서 읽고 채우는 경로가 기본 시나리오다. */
    private void stubPublished() {
        when(loadSkeletonPort.findById(5L))
                .thenReturn(Optional.of(new Skeleton(5L, 77L, SkeletonStatus.PUBLISHED, null, null)));
        when(loadChapterPort.findById(100L)).thenReturn(Optional.of(chapter()));
        when(chapterDocumentCachePort.find(eq(100L), eq(2), any())).thenReturn(Optional.empty());
        when(loadChapterVersionPort.find(100L, 2)).thenReturn(Optional.of(version()));
    }

    @Test
    @DisplayName("웹은 narration 을 뺀 블록 목록을 받는다")
    void webExcludesNarration() {
        stubPublished();

        ComposedChapterView view = service.compose(42L, 100L, DeliveryFormat.WEB);

        assertThat(view.blocks()).extracting(Block::id).containsExactly("b1", "b2", "b4");
        assertThat(view.format()).isEqualTo(DeliveryFormat.WEB);
    }

    /** 같은 챕터를 형태별로 다르게 해석하는 것이 소스를 하나로 두는 이유다. */
    @Test
    @DisplayName("영상은 narration 을 포함한다")
    void videoIncludesNarration() {
        stubPublished();

        assertThat(service.compose(42L, 100L, DeliveryFormat.VIDEO).blocks())
                .extracting(Block::id)
                .containsExactly("b1", "b2", "b3", "b4");
    }

    @Test
    @DisplayName("형태를 주지 않으면 웹이다")
    void defaultsToWeb() {
        stubPublished();

        assertThat(service.compose(42L, 100L, null).format()).isEqualTo(DeliveryFormat.WEB);
    }

    /** 신호가 이 값을 그대로 실어야 한다 — 배지와 본문이 어긋나면 개선 효과 측정이 무의미해진다. */
    @Test
    @DisplayName("소비한 버전과 개선 배지가 함께 온다")
    void carriesVersionAndBadge() {
        stubPublished();

        ComposedChapterView view = service.compose(42L, 100L, DeliveryFormat.WEB);

        assertThat(view.version()).isEqualTo(2);
        assertThat(view.improvedFromPrevious()).isTrue();
        assertThat(view.changeSummary()).isEqualTo("b2 설명 보강");
        assertThat(view.versionCreatedAt()).isEqualTo(LocalDateTime.of(2026, 8, 12, 9, 0));
    }

    /** M3 시점 레이어는 비어 있다. 그 사실이 응답에 드러나야 캐시 경계(P4.5)를 코드로 나눌 수 있다. */
    @Test
    @DisplayName("레이어가 비면 개인화되지 않았다고 표시한다")
    void reportsNotPersonalizedWhenLayerEmpty() {
        stubPublished();

        assertThat(service.compose(42L, 100L, DeliveryFormat.WEB).personalized())
                .isFalse();
    }

    @Test
    @DisplayName("공개되지 않은 뼈대의 챕터는 404 다 — 본문을 읽지도 않는다")
    void hidesUnpublished() {
        when(loadChapterPort.findById(100L)).thenReturn(Optional.of(chapter()));
        when(loadSkeletonPort.findById(5L))
                .thenReturn(Optional.of(new Skeleton(5L, 77L, SkeletonStatus.GENERATING_BODIES, null, null)));

        assertThatThrownBy(() -> service.compose(42L, 100L, DeliveryFormat.WEB))
                .isInstanceOf(EntityNotFoundException.class);
        verify(loadChapterVersionPort, never()).find(anyLong(), anyInt());
    }

    @Test
    @DisplayName("없는 챕터는 404 다")
    void missingChapterIsNotFound() {
        when(loadChapterPort.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.compose(42L, 999L, DeliveryFormat.WEB))
                .isInstanceOf(EntityNotFoundException.class);
    }

    /**
     * published 전환 조건이 "전 챕터 완료"이므로 본문 없는 published 챕터는 정상 상태가 아니다. 빈 블록
     * 목록으로 내리면 그 불일치가 "내용 없는 챕터"로 보인다.
     */
    @Test
    @DisplayName("본문이 없으면 빈 목록이 아니라 404 다")
    void missingBodyIsNotFound() {
        when(loadChapterPort.findById(100L))
                .thenReturn(Optional.of(new Chapter(100L, 5L, "gradient-descent", "경사하강법", "요약", 0, 0, 0, null, null)));
        when(loadSkeletonPort.findById(5L))
                .thenReturn(Optional.of(new Skeleton(5L, 77L, SkeletonStatus.PUBLISHED, null, null)));

        assertThatThrownBy(() -> service.compose(42L, 100L, DeliveryFormat.WEB))
                .isInstanceOf(EntityNotFoundException.class)
                .hasMessageContaining("챕터 본문");
    }

    /** 캐시 히트일 때 본문 스냅샷(JSONB)을 읽지 않는 것이 이 캐시의 이득 전부다. */
    @Test
    @DisplayName("캐시 히트면 본문 스냅샷을 읽지 않는다")
    void cacheHitSkipsSnapshotRead() {
        when(loadSkeletonPort.findById(5L))
                .thenReturn(Optional.of(new Skeleton(5L, 77L, SkeletonStatus.PUBLISHED, null, null)));
        when(loadChapterPort.findById(100L)).thenReturn(Optional.of(chapter()));
        when(chapterDocumentCachePort.find(100L, 2, DeliveryFormat.WEB))
                .thenReturn(Optional.of(new CachedChapterDocument(
                        100L,
                        2,
                        DeliveryFormat.WEB,
                        List.of(Block.text("b1", BlockType.HEADING, "캐시된 제목")),
                        true,
                        "b2 설명 보강",
                        LocalDateTime.of(2026, 8, 12, 9, 0))));

        ComposedChapterView view = service.compose(42L, 100L, DeliveryFormat.WEB);

        assertThat(view.blocks()).extracting(Block::text).containsExactly("캐시된 제목");
        verify(loadChapterVersionPort, never()).find(anyLong(), anyInt());
    }

    /** 미스일 때 채워 두지 않으면 캐시가 영원히 비어 있다 — 조회는 성공하므로 에러 없이 느려진다. */
    @Test
    @DisplayName("캐시 미스면 원천을 읽고 형태별로 걸러 채운다")
    void cacheMissFillsFilteredDocument() {
        stubPublished();

        service.compose(42L, 100L, DeliveryFormat.WEB);

        ArgumentCaptor<CachedChapterDocument> stored = ArgumentCaptor.forClass(CachedChapterDocument.class);
        verify(chapterDocumentCachePort).put(stored.capture());
        assertThat(stored.getValue().version()).isEqualTo(2);
        assertThat(stored.getValue().format()).isEqualTo(DeliveryFormat.WEB);
        // 저장 시점에 걸러 둔다 — 형태가 키에 있으므로 값에 다른 형태의 블록이 섞이면 안 된다.
        assertThat(stored.getValue().blocks()).extracting(Block::id).containsExactly("b1", "b2", "b4");
    }
}
