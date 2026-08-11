package com.mindplates.nextchapter.adapter.out.messaging.generation;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.mindplates.nextchapter.application.generation.port.in.AdvanceGenerationStageUseCase;
import com.mindplates.nextchapter.application.generation.port.in.GenerateChapterBodyUseCase;
import com.mindplates.nextchapter.application.generation.port.in.GenerateChapterOutlinesUseCase;
import com.mindplates.nextchapter.application.generation.port.in.GenerateSkeletonGraphUseCase;
import com.mindplates.nextchapter.common.exception.BudgetExceededException;
import com.mindplates.nextchapter.common.exception.ExternalApiException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

/**
 * 컨슈머는 얇지만 <b>이음새</b>다. 여기서 틀리면 파이프라인이 조용히 멈춘다 — 생성은 성공했는데 완료 보고를
 * 하지 않으면 아무도 다음 단계를 시작하지 않고, 뼈대는 생성 중 상태로 영원히 남는다.
 *
 * <p>브로커를 띄우지 않는다. 확인할 것이 "페이로드를 읽고 유스케이스를 부르고 완료를 보고하는가"뿐이고,
 * 발행 쪽(토픽·키·파티션)은 별도 컨테이너 테스트가 본다.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("생성 파이프라인 컨슈머")
class GenerationListenersTest {

    @Mock
    GenerateSkeletonGraphUseCase generateSkeletonGraphUseCase;

    @Mock
    GenerateChapterOutlinesUseCase generateChapterOutlinesUseCase;

    @Mock
    GenerateChapterBodyUseCase generateChapterBodyUseCase;

    @Mock
    AdvanceGenerationStageUseCase advanceGenerationStageUseCase;

    @Test
    @DisplayName("그래프 완료를 보고한다 — 보고하지 않으면 ②가 시작되지 않는다")
    void graphListenerReportsCompletion() throws Exception {
        when(generateSkeletonGraphUseCase.generate(5L)).thenReturn(8);

        new SkeletonGraphListener(generateSkeletonGraphUseCase, advanceGenerationStageUseCase)
                .onGraphRequested("{\"skeletonId\":5,\"topicId\":42}");

        verify(generateSkeletonGraphUseCase).generate(5L);
        verify(advanceGenerationStageUseCase).graphCompleted(5L);
    }

    @Test
    @DisplayName("개요 완료를 보고한다 — 보고가 챕터 수만큼 ③을 발행한다")
    void outlineListenerReportsCompletion() throws Exception {
        when(generateChapterOutlinesUseCase.generate(5L)).thenReturn(8);

        new ChapterOutlineListener(generateChapterOutlinesUseCase, advanceGenerationStageUseCase)
                .onOutlinesRequested("{\"skeletonId\":5}");

        verify(advanceGenerationStageUseCase).outlinesCompleted(5L);
    }

    @Test
    @DisplayName("본문 완료를 보고한다")
    void bodyListenerReportsCompletion() throws Exception {
        when(generateChapterBodyUseCase.generate(5L, 101L)).thenReturn(true);

        new ChapterBodyListener(generateChapterBodyUseCase, advanceGenerationStageUseCase)
                .onBodyRequested("{\"skeletonId\":5,\"chapterId\":101,\"chapterKey\":\"gradient-descent\"}");

        verify(advanceGenerationStageUseCase).chapterBodyCompleted(5L);
    }

    /**
     * 건너뛴 경우에도 보고해야 한다. 보고하지 않으면 마지막 챕터가 중복 메시지였을 때 완료 판정이 영원히
     * 일어나지 않고, 뼈대가 본문 생성 단계에 갇힌다.
     */
    @Test
    @DisplayName("본문을 건너뛰어도 완료를 보고한다")
    void bodyListenerReportsEvenWhenSkipped() throws Exception {
        when(generateChapterBodyUseCase.generate(5L, 101L)).thenReturn(false);

        new ChapterBodyListener(generateChapterBodyUseCase, advanceGenerationStageUseCase)
                .onBodyRequested("{\"skeletonId\":5,\"chapterId\":101,\"chapterKey\":\"gradient-descent\"}");

        verify(advanceGenerationStageUseCase).chapterBodyCompleted(5L);
    }

    /**
     * 생성 실패를 삼키지 않는다. 즉시 실패로 기록하면 일시적 오류(벤더 5xx, 타임아웃)에도 뼈대가 죽고,
     * 완료로 보고하면 본문 없는 챕터가 다음 단계로 넘어간다. 예외를 올려 재시도에 맡기는 것이 옳다.
     */
    @Test
    @DisplayName("생성 실패는 예외로 올라가고 완료를 보고하지 않는다")
    void failurePropagatesWithoutReporting() {
        when(generateChapterBodyUseCase.generate(5L, 101L)).thenThrow(new ExternalApiException("벤더 오류"));
        ChapterBodyListener listener =
                new ChapterBodyListener(generateChapterBodyUseCase, advanceGenerationStageUseCase);

        assertThatThrownBy(() -> listener.onBodyRequested(
                        "{\"skeletonId\":5,\"chapterId\":101,\"chapterKey\":\"gradient-descent\"}"))
                .isInstanceOf(ExternalApiException.class);

        verify(advanceGenerationStageUseCase, org.mockito.Mockito.never()).chapterBodyCompleted(5L);
    }

    /**
     * 예산 초과만 예외적으로 삼킨다. 상한은 운영자가 올려 주기 전까지 그대로이므로 재시도가 같은 결과를
     * 반복하고, 그동안 파티션이 막혀 다른 뼈대의 생성까지 밀린다.
     *
     * <p>삼키는 대가로 실패를 반드시 기록한다 — 기록 없이 삼키면 뼈대가 생성 중 상태에 영원히 남는다.
     */
    @Test
    @DisplayName("예산 초과는 재시도하지 않고 실패로 확정한다")
    void budgetExceededIsNotRetried() throws Exception {
        when(generateChapterBodyUseCase.generate(5L, 101L)).thenThrow(new BudgetExceededException("일일 토큰 상한"));

        new ChapterBodyListener(generateChapterBodyUseCase, advanceGenerationStageUseCase)
                .onBodyRequested("{\"skeletonId\":5,\"chapterId\":101,\"chapterKey\":\"gradient-descent\"}");

        verify(advanceGenerationStageUseCase).failed(eq(5L), contains("예산 상한 초과"));
        verify(advanceGenerationStageUseCase, org.mockito.Mockito.never()).chapterBodyCompleted(5L);
    }

    @Test
    @DisplayName("그래프 단계의 예산 초과도 같은 경로를 지난다")
    void budgetExceededOnGraphStage() throws Exception {
        when(generateSkeletonGraphUseCase.generate(5L)).thenThrow(new BudgetExceededException("뼈대당 토큰 상한"));

        new SkeletonGraphListener(generateSkeletonGraphUseCase, advanceGenerationStageUseCase)
                .onGraphRequested("{\"skeletonId\":5,\"topicId\":42}");

        verify(advanceGenerationStageUseCase).failed(eq(5L), contains("예산 상한 초과"));
        verify(advanceGenerationStageUseCase, org.mockito.Mockito.never()).graphCompleted(5L);
    }

    @Test
    @DisplayName("개요 단계의 예산 초과도 같은 경로를 지난다")
    void budgetExceededOnOutlineStage() throws Exception {
        when(generateChapterOutlinesUseCase.generate(5L)).thenThrow(new BudgetExceededException("일일 토큰 상한"));

        new ChapterOutlineListener(generateChapterOutlinesUseCase, advanceGenerationStageUseCase)
                .onOutlinesRequested("{\"skeletonId\":5}");

        verify(advanceGenerationStageUseCase).failed(eq(5L), contains("예산 상한 초과"));
        verify(advanceGenerationStageUseCase, org.mockito.Mockito.never()).outlinesCompleted(5L);
    }
}
