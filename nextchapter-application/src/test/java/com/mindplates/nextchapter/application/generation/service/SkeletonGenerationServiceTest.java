package com.mindplates.nextchapter.application.generation.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.mindplates.nextchapter.application.chapter.port.out.LoadChapterPort;
import com.mindplates.nextchapter.application.chapter.port.out.LoadOutboxEventPort;
import com.mindplates.nextchapter.application.generation.port.out.GenerationEventPublisherPort;
import com.mindplates.nextchapter.application.generation.view.GenerationProgressView;
import com.mindplates.nextchapter.application.skeleton.port.out.LoadSkeletonPort;
import com.mindplates.nextchapter.application.skeleton.port.out.SaveSkeletonPort;
import com.mindplates.nextchapter.common.exception.EntityNotFoundException;
import com.mindplates.nextchapter.domain.chapter.model.Chapter;
import com.mindplates.nextchapter.domain.skeleton.model.Skeleton;
import com.mindplates.nextchapter.domain.skeleton.model.SkeletonStatus;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("뼈대 생성 잡 상태 머신")
class SkeletonGenerationServiceTest {

    private static final Long SKELETON_ID = 5L;
    private static final Long TOPIC_ID = 42L;

    @Mock
    LoadSkeletonPort loadSkeletonPort;

    @Mock
    SaveSkeletonPort saveSkeletonPort;

    @Mock
    LoadChapterPort loadChapterPort;

    @Mock
    LoadOutboxEventPort loadOutboxEventPort;

    @Mock
    GenerationEventPublisherPort eventPublisher;

    @InjectMocks
    SkeletonGenerationService service;

    /** 재생성이 되면 신호가 붙어 있는 본문이 사라진다 — 개선 루프의 근거를 지우는 일이다. */
    @Test
    @DisplayName("이미 뼈대가 있으면 만들지 않고 합류한다")
    void startIsIdempotent() {
        when(loadSkeletonPort.findByTopicId(TOPIC_ID))
                .thenReturn(Optional.of(skeleton(SkeletonStatus.GENERATING_BODIES)));
        when(loadChapterPort.countBySkeletonId(SKELETON_ID)).thenReturn(10L);
        when(loadChapterPort.countBySkeletonIdWithoutBody(SKELETON_ID)).thenReturn(4L);

        GenerationProgressView view = service.start(TOPIC_ID);

        assertThat(view.status()).isEqualTo(SkeletonStatus.GENERATING_BODIES);
        assertThat(view.totalChapters()).isEqualTo(10);
        assertThat(view.chaptersWithBody()).isEqualTo(6);
        verify(saveSkeletonPort, never()).save(any());
        verifyNoInteractions(eventPublisher);
    }

    @Test
    @DisplayName("뼈대가 없으면 만들고 그래프 생성을 발행한다")
    void startCreatesAndPublishes() {
        when(loadSkeletonPort.findByTopicId(TOPIC_ID)).thenReturn(Optional.empty());
        when(saveSkeletonPort.save(any())).thenReturn(skeleton(SkeletonStatus.GENERATING_GRAPH));

        GenerationProgressView view = service.start(TOPIC_ID);

        assertThat(view.status()).isEqualTo(SkeletonStatus.GENERATING_GRAPH);
        assertThat(view.percent()).isZero();
        verify(eventPublisher).requestGraph(SKELETON_ID, TOPIC_ID);
    }

    @Test
    @DisplayName("그래프 완료는 상태를 올리고 개요 생성을 발행한다")
    void graphCompletedChainsToOutlines() {
        stub(SkeletonStatus.GENERATING_GRAPH);
        when(saveSkeletonPort.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        service.graphCompleted(SKELETON_ID);

        ArgumentCaptor<Skeleton> saved = ArgumentCaptor.forClass(Skeleton.class);
        verify(saveSkeletonPort).save(saved.capture());
        assertThat(saved.getValue().status()).isEqualTo(SkeletonStatus.GENERATING_OUTLINES);
        verify(eventPublisher).requestOutlines(SKELETON_ID);
    }

    /** Kafka 는 at-least-once 다. 같은 완료 메시지를 두 번 받는 것은 정상 상황이다. */
    @Test
    @DisplayName("같은 단계 완료 메시지를 두 번 받아도 상태가 망가지지 않는다")
    void duplicateCompletionIsSafe() {
        stub(SkeletonStatus.GENERATING_OUTLINES);

        service.graphCompleted(SKELETON_ID);

        verify(saveSkeletonPort, never()).save(any());
        verify(eventPublisher).requestOutlines(SKELETON_ID);
    }

    @Test
    @DisplayName("개요 완료는 챕터 수만큼 본문 생성을 발행한다 — 발행 측에서 개수를 줄이지 않는다")
    void outlinesCompletedFansOutPerChapter() {
        stub(SkeletonStatus.GENERATING_OUTLINES);
        when(saveSkeletonPort.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(loadChapterPort.findBySkeletonId(SKELETON_ID))
                .thenReturn(List.of(chapter(1L, "gradient-descent"), chapter(2L, "loss-function")));

        service.outlinesCompleted(SKELETON_ID);

        verify(eventPublisher).requestBody(SKELETON_ID, 1L, "gradient-descent");
        verify(eventPublisher).requestBody(SKELETON_ID, 2L, "loss-function");
    }

    /** 챕터가 없으면 완료 판정이 즉시 참이 되어 빈 뼈대가 published 로 간다. 부분 공개보다 나쁘다. */
    @Test
    @DisplayName("개요 이후 챕터가 하나도 없으면 실패로 처리한다")
    void outlinesWithoutChaptersFails() {
        stub(SkeletonStatus.GENERATING_OUTLINES);
        when(saveSkeletonPort.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(loadChapterPort.findBySkeletonId(SKELETON_ID)).thenReturn(List.of());

        service.outlinesCompleted(SKELETON_ID);

        ArgumentCaptor<Skeleton> saved = ArgumentCaptor.forClass(Skeleton.class);
        verify(saveSkeletonPort, org.mockito.Mockito.atLeastOnce()).save(saved.capture());
        assertThat(saved.getAllValues()).extracting(Skeleton::status).contains(SkeletonStatus.FAILED);
        verify(eventPublisher, never()).requestBody(anyLong(), anyLong(), anyString());
    }

    @Test
    @DisplayName("본문이 남아 있으면 다음 단계로 넘어가지 않는다")
    void waitsForAllChapters() {
        when(loadChapterPort.countBySkeletonIdWithoutBody(SKELETON_ID)).thenReturn(3L);

        service.chapterBodyCompleted(SKELETON_ID);

        verify(saveSkeletonPort, never()).save(any());
    }

    @Test
    @DisplayName("전 챕터 본문이 끝나면 파생 자산 단계로 넘어간다")
    void advancesWhenAllChaptersDone() {
        when(loadChapterPort.countBySkeletonIdWithoutBody(SKELETON_ID)).thenReturn(0L);
        stub(SkeletonStatus.GENERATING_BODIES);
        when(saveSkeletonPort.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        service.chapterBodyCompleted(SKELETON_ID);

        ArgumentCaptor<Skeleton> saved = ArgumentCaptor.forClass(Skeleton.class);
        verify(saveSkeletonPort).save(saved.capture());
        assertThat(saved.getValue().status()).isEqualTo(SkeletonStatus.GENERATING_ASSETS);
    }

    /** 마지막 두 챕터가 동시에 끝나면 둘 다 완료 판정을 할 수 있다. 두 번째는 넘어가야 한다. */
    @Test
    @DisplayName("이미 다음 단계면 중복 완료 판정을 무시한다")
    void ignoresDuplicateCompletion() {
        when(loadChapterPort.countBySkeletonIdWithoutBody(SKELETON_ID)).thenReturn(0L);
        stub(SkeletonStatus.GENERATING_ASSETS);

        service.chapterBodyCompleted(SKELETON_ID);

        verify(saveSkeletonPort, never()).save(any());
    }

    @Test
    @DisplayName("실패는 한 번만 기록되고 진행률에 숫자를 남기지 않는다")
    void failIsIdempotentAndZeroesPercent() {
        stub(SkeletonStatus.GENERATING_BODIES);
        when(saveSkeletonPort.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        service.failed(SKELETON_ID, "본문 생성이 3회 실패했다");

        verify(saveSkeletonPort).save(any());

        stub(SkeletonStatus.FAILED);
        service.failed(SKELETON_ID, "다시 보고됨");
        verify(saveSkeletonPort).save(any());

        when(loadChapterPort.countBySkeletonId(SKELETON_ID)).thenReturn(10L);
        when(loadChapterPort.countBySkeletonIdWithoutBody(SKELETON_ID)).thenReturn(2L);
        GenerationProgressView view = service.bySkeletonId(SKELETON_ID);
        assertThat(view.failed()).isTrue();
        assertThat(view.percent()).isZero();
    }

    @Test
    @DisplayName("진행률은 본문 완료 비율로 보간된다 — 퍼센트만 보여주면 정지와 지연이 구분되지 않는다")
    void interpolatesBodyProgress() {
        stub(SkeletonStatus.GENERATING_BODIES);
        when(loadChapterPort.countBySkeletonId(SKELETON_ID)).thenReturn(10L);
        when(loadChapterPort.countBySkeletonIdWithoutBody(SKELETON_ID)).thenReturn(5L);

        assertThat(service.bySkeletonId(SKELETON_ID).percent()).isEqualTo(50);
    }

    @Test
    @DisplayName("published 는 100% 다")
    void publishedIsHundred() {
        stub(SkeletonStatus.PUBLISHED);
        when(loadChapterPort.countBySkeletonId(SKELETON_ID)).thenReturn(10L);
        when(loadChapterPort.countBySkeletonIdWithoutBody(SKELETON_ID)).thenReturn(0L);

        GenerationProgressView view = service.bySkeletonId(SKELETON_ID);

        assertThat(view.percent()).isEqualTo(100);
        assertThat(view.published()).isTrue();
    }

    @Test
    @DisplayName("없는 뼈대·주제 조회는 404 로 이어진다")
    void missingSkeleton() {
        when(loadSkeletonPort.findById(SKELETON_ID)).thenReturn(Optional.empty());
        when(loadSkeletonPort.findByTopicId(eq(TOPIC_ID))).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.bySkeletonId(SKELETON_ID)).isInstanceOf(EntityNotFoundException.class);
        assertThatThrownBy(() -> service.byTopicId(TOPIC_ID)).isInstanceOf(EntityNotFoundException.class);
    }

    /**
     * 공개 조건에 outbox 가 비었는지가 들어간다. 없으면 Neo4j 에 아직 반영되지 않은 뼈대가 공개되고,
     * 그 상태의 그래프 탐색은 <b>에러가 아니라</b> "관련 챕터 없음"으로 나타난다 — 사용자에게는 빈 화면이고
     * 로그에도 아무것도 남지 않는다.
     */
    @Test
    @DisplayName("미발행 outbox 가 남아 있으면 공개하지 않는다")
    void doesNotPublishWhileOutboxPending() {
        when(loadSkeletonPort.findByStatus(SkeletonStatus.GENERATING_ASSETS))
                .thenReturn(List.of(skeleton(SkeletonStatus.GENERATING_ASSETS)));
        when(loadChapterPort.countBySkeletonIdWithoutBody(SKELETON_ID)).thenReturn(0L);
        when(loadOutboxEventPort.countPendingBySkeletonId(SKELETON_ID)).thenReturn(2L);

        assertThat(service.publishReady()).isZero();
        verify(saveSkeletonPort, never()).save(any());
    }

    @Test
    @DisplayName("본문 없는 챕터가 남아 있으면 공개하지 않는다")
    void doesNotPublishWithMissingBodies() {
        when(loadSkeletonPort.findByStatus(SkeletonStatus.GENERATING_ASSETS))
                .thenReturn(List.of(skeleton(SkeletonStatus.GENERATING_ASSETS)));
        when(loadChapterPort.countBySkeletonIdWithoutBody(SKELETON_ID)).thenReturn(1L);

        assertThat(service.publishReady()).isZero();
        verify(loadOutboxEventPort, never()).countPendingBySkeletonId(anyLong());
        verify(saveSkeletonPort, never()).save(any());
    }

    @Test
    @DisplayName("전 챕터 본문 + outbox 비었으면 공개한다")
    void publishesWhenEverythingSettled() {
        when(loadSkeletonPort.findByStatus(SkeletonStatus.GENERATING_ASSETS))
                .thenReturn(List.of(skeleton(SkeletonStatus.GENERATING_ASSETS)));
        when(loadChapterPort.countBySkeletonIdWithoutBody(SKELETON_ID)).thenReturn(0L);
        when(loadOutboxEventPort.countPendingBySkeletonId(SKELETON_ID)).thenReturn(0L);

        assertThat(service.publishReady()).isEqualTo(1);

        ArgumentCaptor<Skeleton> saved = ArgumentCaptor.forClass(Skeleton.class);
        verify(saveSkeletonPort).save(saved.capture());
        assertThat(saved.getValue().status()).isEqualTo(SkeletonStatus.PUBLISHED);
    }

    /** 자산 단계가 아닌 뼈대는 애초에 후보가 아니다 — 스윕이 상태로 걸러 온다. */
    @Test
    @DisplayName("자산 단계 뼈대만 훑는다")
    void sweepsOnlyAssetStage() {
        when(loadSkeletonPort.findByStatus(SkeletonStatus.GENERATING_ASSETS)).thenReturn(List.of());

        assertThat(service.publishReady()).isZero();
        verifyNoInteractions(loadOutboxEventPort);
    }

    private void stub(SkeletonStatus status) {
        when(loadSkeletonPort.findById(SKELETON_ID)).thenReturn(Optional.of(skeleton(status)));
    }

    private static Skeleton skeleton(SkeletonStatus status) {
        return new Skeleton(SKELETON_ID, TOPIC_ID, status, null, null);
    }

    private static Chapter chapter(Long id, String key) {
        return new Chapter(id, SKELETON_ID, key, "제목-" + key, null, 0, 0, 0, null, null);
    }
}
