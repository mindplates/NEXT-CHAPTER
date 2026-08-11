package com.mindplates.nextchapter.application.generation.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.mindplates.nextchapter.application.generation.port.in.AdvanceGenerationStageUseCase;
import com.mindplates.nextchapter.application.generation.port.in.RecordGenerationFailureUseCase.GenerationFailureCommand;
import com.mindplates.nextchapter.application.generation.port.out.GenerationRetryPublisherPort;
import com.mindplates.nextchapter.application.generation.port.out.LoadGenerationFailurePort;
import com.mindplates.nextchapter.application.generation.port.out.SaveGenerationFailurePort;
import com.mindplates.nextchapter.application.generation.view.GenerationFailureView;
import com.mindplates.nextchapter.application.skeleton.port.out.LoadSkeletonPort;
import com.mindplates.nextchapter.application.skeleton.port.out.SaveSkeletonPort;
import com.mindplates.nextchapter.common.exception.InvalidOperationException;
import com.mindplates.nextchapter.domain.generation.model.GenerationFailure;
import com.mindplates.nextchapter.domain.generation.model.GenerationFailureStatus;
import com.mindplates.nextchapter.domain.generation.model.GenerationStage;
import com.mindplates.nextchapter.domain.skeleton.model.Skeleton;
import com.mindplates.nextchapter.domain.skeleton.model.SkeletonStatus;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

/**
 * 큐가 지켜야 하는 것은 둘이다 — 실패를 <b>남기고</b>, 그 항목만 <b>다시 돌린다</b>.
 *
 * <p>둘 중 하나만 되면 조용히 틀린다. 남기지 않으면 뼈대가 생성 중 상태로 남은 이유를 아무도 모르고,
 * 항목 단위 재시도가 없으면 운영자가 할 수 있는 것은 처음부터 다시뿐이라 이미 성공한 챕터의 생성비를
 * 다시 쓴다.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("생성 실패 운영자 큐")
class GenerationFailureServiceTest {

    private static final Long SKELETON_ID = 5L;

    @Mock
    SaveGenerationFailurePort saveGenerationFailurePort;

    @Mock
    LoadGenerationFailurePort loadGenerationFailurePort;

    @Mock
    GenerationRetryPublisherPort retryPublisher;

    @Mock
    LoadSkeletonPort loadSkeletonPort;

    @Mock
    SaveSkeletonPort saveSkeletonPort;

    @Mock
    AdvanceGenerationStageUseCase advanceGenerationStageUseCase;

    @InjectMocks
    GenerationFailureService service;

    private static GenerationFailure failure(GenerationStage stage, GenerationFailureStatus status, Long skeletonId) {
        return new GenerationFailure(
                77L,
                skeletonId,
                stage == GenerationStage.BODY ? 101L : null,
                stage,
                "skeleton.body.requested",
                3,
                420L,
                "{\"skeletonId\":5,\"chapterId\":101}",
                "com.example.VendorTimeout",
                "타임아웃",
                3,
                status,
                null,
                null,
                null,
                null);
    }

    private void stubSkeleton(SkeletonStatus status) {
        when(loadSkeletonPort.findById(SKELETON_ID))
                .thenReturn(Optional.of(new Skeleton(SKELETON_ID, 42L, status, null, null)));
    }

    private void echoSave() {
        when(saveGenerationFailurePort.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Nested
    @DisplayName("기록")
    class Record {

        @Test
        @DisplayName("큐에 올리고 뼈대를 실패로 표시한다")
        void recordsAndFailsSkeleton() {
            echoSave();

            service.record(new GenerationFailureCommand(
                    SKELETON_ID,
                    101L,
                    GenerationStage.BODY,
                    "skeleton.body.requested",
                    3,
                    420L,
                    "{}",
                    "com.example.VendorTimeout",
                    "타임아웃",
                    3));

            ArgumentCaptor<GenerationFailure> captor = ArgumentCaptor.forClass(GenerationFailure.class);
            verify(saveGenerationFailurePort).save(captor.capture());
            assertThat(captor.getValue().status()).isEqualTo(GenerationFailureStatus.OPEN);
            assertThat(captor.getValue().attempts()).isEqualTo(3);
            verify(advanceGenerationStageUseCase).failed(eq(SKELETON_ID), contains("BODY"));
        }

        /**
         * 페이로드를 읽지 못한 메시지는 어느 뼈대인지 알 수 없다. 그래도 큐에는 올린다 — 읽을 수 없는
         * 메시지가 조용히 사라지는 것이 이 큐를 만든 이유 그 자체다.
         */
        @Test
        @DisplayName("뼈대를 모르면 큐에만 올린다")
        void recordsWithoutSkeleton() {
            echoSave();

            service.record(new GenerationFailureCommand(
                    null,
                    null,
                    GenerationStage.BODY,
                    "skeleton.body.requested",
                    3,
                    421L,
                    "not-json",
                    "JsonParseException",
                    null,
                    3));

            verify(saveGenerationFailurePort).save(any());
            verifyNoInteractions(advanceGenerationStageUseCase);
        }
    }

    @Nested
    @DisplayName("재시도")
    class Retry {

        /**
         * 실패로 덮인 상태를 그대로 두면 컨슈머가 일을 끝내고 부르는 단계 전환이 거절되고, 생성은 성공했는데
         * 파이프라인이 그 자리에서 멈춘다.
         */
        @Test
        @DisplayName("뼈대 상태를 그 단계로 되돌린 뒤 재발행한다")
        void restoresStageThenRepublishes() {
            when(loadGenerationFailurePort.findById(77L))
                    .thenReturn(Optional.of(failure(GenerationStage.BODY, GenerationFailureStatus.OPEN, SKELETON_ID)));
            stubSkeleton(SkeletonStatus.FAILED);
            echoSave();

            GenerationFailureView view = service.retry(77L, "admin");

            ArgumentCaptor<Skeleton> skeleton = ArgumentCaptor.forClass(Skeleton.class);
            verify(saveSkeletonPort).save(skeleton.capture());
            assertThat(skeleton.getValue().status()).isEqualTo(SkeletonStatus.GENERATING_BODIES);
            verify(retryPublisher).republish(GenerationStage.BODY, "{\"skeletonId\":5,\"chapterId\":101}", 5L, 101L);
            assertThat(view.status()).isEqualTo(GenerationFailureStatus.RETRIED);
        }

        /** 페이로드를 다시 만들면 그 사이 바뀐 데이터가 섞여 원래 실패의 재시도가 아니게 된다. */
        @Test
        @DisplayName("저장된 페이로드를 그대로 보낸다")
        void republishesStoredPayload() {
            when(loadGenerationFailurePort.findById(77L))
                    .thenReturn(Optional.of(failure(GenerationStage.GRAPH, GenerationFailureStatus.OPEN, SKELETON_ID)));
            stubSkeleton(SkeletonStatus.GENERATING_GRAPH);
            echoSave();

            service.retry(77L, "admin");

            verify(retryPublisher).republish(eq(GenerationStage.GRAPH), contains("skeletonId"), eq(5L), any());
            // 이미 그 단계면 전이하지 않는다 — 같은 상태로의 전이는 상태 머신이 거절한다.
            verify(saveSkeletonPort, never()).save(any());
        }

        @Test
        @DisplayName("닫힌 항목은 다시 돌릴 수 없다")
        void rejectsClosed() {
            when(loadGenerationFailurePort.findById(77L))
                    .thenReturn(
                            Optional.of(failure(GenerationStage.BODY, GenerationFailureStatus.RESOLVED, SKELETON_ID)));

            assertThatThrownBy(() -> service.retry(77L, "admin")).isInstanceOf(InvalidOperationException.class);
            verifyNoInteractions(retryPublisher);
        }

        @Test
        @DisplayName("뼈대를 모르는 항목은 다시 돌릴 수 없다")
        void rejectsUnknownSkeleton() {
            when(loadGenerationFailurePort.findById(77L))
                    .thenReturn(Optional.of(failure(GenerationStage.BODY, GenerationFailureStatus.OPEN, null)));

            assertThatThrownBy(() -> service.retry(77L, "admin")).isInstanceOf(InvalidOperationException.class);
            verifyNoInteractions(retryPublisher);
        }

        /** 이미 공개된 뼈대를 되돌리면 "재생성 중에는 형태를 잠근다"가 되어 개선이 잦을수록 서비스가 나빠진다. */
        @Test
        @DisplayName("공개된 뼈대는 되돌리지 않는다")
        void rejectsPublishedSkeleton() {
            when(loadGenerationFailurePort.findById(77L))
                    .thenReturn(Optional.of(failure(GenerationStage.BODY, GenerationFailureStatus.OPEN, SKELETON_ID)));
            stubSkeleton(SkeletonStatus.PUBLISHED);

            assertThatThrownBy(() -> service.retry(77L, "admin")).isInstanceOf(InvalidOperationException.class);
            verifyNoInteractions(retryPublisher);
        }
    }

    @Nested
    @DisplayName("조회와 닫기")
    class ListAndResolve {

        @Test
        @DisplayName("상태로 걸러 조회한다")
        void listsByStatus() {
            when(loadGenerationFailurePort.findByStatus(GenerationFailureStatus.OPEN))
                    .thenReturn(List.of(failure(GenerationStage.BODY, GenerationFailureStatus.OPEN, SKELETON_ID)));

            List<GenerationFailureView> views = service.list(GenerationFailureStatus.OPEN);

            assertThat(views).hasSize(1);
            assertThat(views.getFirst().stage()).isEqualTo(GenerationStage.BODY);
        }

        /**
         * 목록 응답에 페이로드를 싣지 않는다. 큐 화면이 필요한 것은 "무엇이 어디서 왜 실패했나"이고,
         * 페이로드에는 프롬프트와 챕터 내용이 섞여 있다.
         */
        @Test
        @DisplayName("응답에 페이로드가 없다")
        void viewOmitsPayload() {
            GenerationFailure source = failure(GenerationStage.BODY, GenerationFailureStatus.OPEN, SKELETON_ID);

            GenerationFailureView view = GenerationFailureView.from(source);

            assertThat(view.toString()).doesNotContain(source.payload());
            assertThat(view.errorType()).isEqualTo(source.errorType());
            assertThat(view.attempts()).isEqualTo(source.attempts());
        }

        @Test
        @DisplayName("이미 닫힌 항목을 닫으면 그대로 돌려준다")
        void resolveIsIdempotent() {
            when(loadGenerationFailurePort.findById(77L))
                    .thenReturn(
                            Optional.of(failure(GenerationStage.BODY, GenerationFailureStatus.RESOLVED, SKELETON_ID)));

            GenerationFailureView view = service.resolve(77L, "admin");

            assertThat(view.status()).isEqualTo(GenerationFailureStatus.RESOLVED);
            verify(saveGenerationFailurePort, never()).save(any());
        }

        @Test
        @DisplayName("닫으면 처리자와 시각이 남는다")
        void resolveRecordsActor() {
            when(loadGenerationFailurePort.findById(77L))
                    .thenReturn(Optional.of(failure(GenerationStage.BODY, GenerationFailureStatus.OPEN, SKELETON_ID)));
            echoSave();

            GenerationFailureView view = service.resolve(77L, "admin");

            assertThat(view.status()).isEqualTo(GenerationFailureStatus.RESOLVED);
            assertThat(view.resolvedBy()).isEqualTo("admin");
            assertThat(view.resolvedAt()).isNotNull();
        }

        @Test
        @DisplayName("뼈대별로 조회한다")
        void listsBySkeleton() {
            when(loadGenerationFailurePort.findBySkeletonId(SKELETON_ID))
                    .thenReturn(List.of(failure(GenerationStage.GRAPH, GenerationFailureStatus.OPEN, SKELETON_ID)));

            assertThat(service.bySkeletonId(SKELETON_ID)).hasSize(1);
        }

        @Test
        @DisplayName("없는 항목은 404 로 이어진다")
        void missingFailure() {
            when(loadGenerationFailurePort.findById(99L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.resolve(99L, "admin"))
                    .isInstanceOf(com.mindplates.nextchapter.common.exception.EntityNotFoundException.class);
        }
    }
}
