package com.mindplates.nextchapter.application.generation.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.mindplates.nextchapter.application.chapter.port.out.LoadChapterPort;
import com.mindplates.nextchapter.application.generation.port.in.AdvanceGenerationStageUseCase;
import com.mindplates.nextchapter.application.generation.port.in.ComposeChapterBodyUseCase;
import com.mindplates.nextchapter.application.generation.port.in.ComposeChapterBodyUseCase.ChapterBodyPrompt;
import com.mindplates.nextchapter.application.generation.port.out.GenerationEventPublisherPort;
import com.mindplates.nextchapter.application.generation.port.out.LlmBatchItem;
import com.mindplates.nextchapter.application.generation.port.out.LlmBatchItemResult;
import com.mindplates.nextchapter.application.generation.port.out.LlmBatchPollResult;
import com.mindplates.nextchapter.application.generation.port.out.LoadAiBatchJobPort;
import com.mindplates.nextchapter.application.generation.port.out.SaveAiBatchJobPort;
import com.mindplates.nextchapter.common.exception.BudgetExceededException;
import com.mindplates.nextchapter.common.exception.ExternalApiException;
import com.mindplates.nextchapter.domain.admin.model.AiStage;
import com.mindplates.nextchapter.domain.admin.model.AiVendor;
import com.mindplates.nextchapter.domain.chapter.model.Chapter;
import com.mindplates.nextchapter.domain.generation.model.AiBatchJob;
import com.mindplates.nextchapter.domain.generation.model.AiBatchStatus;
import com.mindplates.nextchapter.domain.generation.model.GenerationStage;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

/**
 * 배치가 지켜야 하는 것은 셋이다.
 *
 * <ol>
 *   <li>지원하지 않으면 <b>개별 경로로 넘긴다</b> — 조용히 아무것도 하지 않으면 뼈대가 본문 단계에 갇힌다
 *   <li>제출 당시의 벤더·모델을 저장한다 — 조회를 다른 벤더에게 보내면 배치가 유실된다
 *   <li>항목 실패는 그 챕터만 개별 경로로 넘긴다 — 두 번째 실패 처리 체계를 만들지 않는다
 * </ol>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("본문 생성 배치")
class ChapterBodyBatchServiceTest {

    private static final Long SKELETON_ID = 5L;

    @Mock
    LoadChapterPort loadChapterPort;

    @Mock
    ComposeChapterBodyUseCase composeChapterBodyUseCase;

    @Mock
    AiGateway aiGateway;

    @Mock
    SaveAiBatchJobPort saveAiBatchJobPort;

    @Mock
    LoadAiBatchJobPort loadAiBatchJobPort;

    @Mock
    GenerationEventPublisherPort eventPublisher;

    @Mock
    AdvanceGenerationStageUseCase advanceGenerationStageUseCase;

    private ChapterBodyBatchService service(boolean batchEnabled) {
        return new ChapterBodyBatchService(
                loadChapterPort,
                composeChapterBodyUseCase,
                aiGateway,
                saveAiBatchJobPort,
                loadAiBatchJobPort,
                eventPublisher,
                advanceGenerationStageUseCase,
                batchEnabled);
    }

    private static Chapter chapter(Long id, String key, int version) {
        return new Chapter(id, SKELETON_ID, key, "제목-" + key, null, version, version, 0, null, null);
    }

    private void stubChapters(Chapter... chapters) {
        when(loadChapterPort.findBySkeletonId(SKELETON_ID)).thenReturn(List.of(chapters));
    }

    private void stubPrompts(Long... chapterIds) {
        for (Long chapterId : chapterIds) {
            when(composeChapterBodyUseCase.promptFor(SKELETON_ID, chapterId))
                    .thenReturn(Optional.of(new ChapterBodyPrompt("시스템", "사용자-" + chapterId, 16384)));
        }
    }

    private static AiBatchJob job() {
        return new AiBatchJob(
                9L,
                SKELETON_ID,
                GenerationStage.BODY,
                AiVendor.ANTHROPIC,
                "claude-opus-5",
                "msgbatch_abc",
                AiBatchStatus.SUBMITTED,
                2,
                null,
                null,
                null,
                null);
    }

    @Nested
    @DisplayName("제출")
    class Submit {

        /** 조용히 아무것도 하지 않으면 그 뼈대의 본문이 영원히 생성되지 않는다. */
        @Test
        @DisplayName("배치가 꺼져 있으면 false — 호출부가 개별 경로로 간다")
        void disabledFallsBack() {
            assertThat(service(false).submitBodies(SKELETON_ID)).isFalse();
            verifyNoInteractions(aiGateway);
        }

        /** 능력 조회다. 배치를 켜 뒀는데 개별 호출로 돌고 있으면 "할인이 적용된다"고 착각한 채 정가를 낸다. */
        @Test
        @DisplayName("벤더가 배치를 지원하지 않으면 false")
        void unsupportedVendorFallsBack() {
            when(aiGateway.supportsBatch(AiStage.SKELETON_BODY)).thenReturn(false);

            assertThat(service(true).submitBodies(SKELETON_ID)).isFalse();
            verify(aiGateway, never()).submitBatch(any(), anyLong(), any());
        }

        @Test
        @DisplayName("본문 없는 챕터만 배치에 담고 벤더·모델을 저장한다")
        void submitsPendingChaptersOnly() {
            when(aiGateway.supportsBatch(AiStage.SKELETON_BODY)).thenReturn(true);
            stubChapters(chapter(101L, "a", 0), chapter(102L, "b", 3), chapter(103L, "c", 0));
            stubPrompts(101L, 103L);
            when(composeChapterBodyUseCase.promptFor(SKELETON_ID, 102L)).thenReturn(Optional.empty());
            when(aiGateway.submitBatch(eq(AiStage.SKELETON_BODY), eq(SKELETON_ID), any()))
                    .thenReturn(new AiGateway.BatchSubmission("msgbatch_abc", AiVendor.ANTHROPIC, "claude-opus-5"));

            assertThat(service(true).submitBodies(SKELETON_ID)).isTrue();

            ArgumentCaptor<List<LlmBatchItem>> items = ArgumentCaptor.captor();
            verify(aiGateway).submitBatch(eq(AiStage.SKELETON_BODY), eq(SKELETON_ID), items.capture());
            assertThat(items.getValue()).extracting(LlmBatchItem::customId).containsExactly("101", "103");

            ArgumentCaptor<AiBatchJob> saved = ArgumentCaptor.forClass(AiBatchJob.class);
            verify(saveAiBatchJobPort).save(saved.capture());
            assertThat(saved.getValue().vendor()).isEqualTo(AiVendor.ANTHROPIC);
            assertThat(saved.getValue().model()).isEqualTo("claude-opus-5");
            assertThat(saved.getValue().itemCount()).isEqualTo(2);
            // 배치로 갔으므로 개별 메시지는 나가지 않는다 — 나가면 같은 본문을 두 번 생성한다.
            verifyNoInteractions(eventPublisher);
        }

        @Test
        @DisplayName("제출할 챕터가 없으면 false")
        void noPendingChapters() {
            when(aiGateway.supportsBatch(AiStage.SKELETON_BODY)).thenReturn(true);
            stubChapters(chapter(101L, "a", 3));
            when(composeChapterBodyUseCase.promptFor(SKELETON_ID, 101L)).thenReturn(Optional.empty());

            assertThat(service(true).submitBodies(SKELETON_ID)).isFalse();
            verify(aiGateway, never()).submitBatch(any(), anyLong(), any());
        }

        /**
         * 개별 경로로 내려가도 같은 상한에 막히므로 챕터 수만큼 같은 실패를 반복한다. 그 자리에서 실패로
         * 확정하는 것이 맞다.
         */
        @Test
        @DisplayName("예산 초과는 개별 경로로 넘기지 않고 실패로 확정한다")
        void budgetExceededFailsImmediately() {
            when(aiGateway.supportsBatch(AiStage.SKELETON_BODY)).thenReturn(true);
            stubChapters(chapter(101L, "a", 0));
            stubPrompts(101L);
            when(aiGateway.submitBatch(any(), anyLong(), any())).thenThrow(new BudgetExceededException("일일 토큰 상한"));

            assertThat(service(true).submitBodies(SKELETON_ID)).isTrue();

            verify(advanceGenerationStageUseCase).failed(eq(SKELETON_ID), contains("예산 상한 초과"));
            verifyNoInteractions(eventPublisher);
        }
    }

    @Nested
    @DisplayName("반영")
    class Poll {

        @Test
        @DisplayName("아직 처리 중이면 아무것도 하지 않는다")
        void inProgressDoesNothing() {
            when(loadAiBatchJobPort.findSubmitted()).thenReturn(List.of(job()));
            when(aiGateway.pollBatch(any(), anyLong(), any(), anyString(), anyString()))
                    .thenReturn(LlmBatchPollResult.inProgress());

            assertThat(service(true).pollSubmitted()).isZero();
            verify(saveAiBatchJobPort, never()).save(any());
            verifyNoInteractions(composeChapterBodyUseCase);
        }

        /** 조회를 <b>저장된</b> 벤더·모델로 보낸다. 현재 설정을 다시 읽으면 배치가 유실된다. */
        @Test
        @DisplayName("조회는 제출 당시의 벤더·모델로 보낸다")
        void pollsWithStoredVendor() {
            when(loadAiBatchJobPort.findSubmitted()).thenReturn(List.of(job()));
            when(aiGateway.pollBatch(any(), anyLong(), any(), anyString(), anyString()))
                    .thenReturn(LlmBatchPollResult.inProgress());

            service(true).pollSubmitted();

            verify(aiGateway)
                    .pollBatch(AiStage.SKELETON_BODY, SKELETON_ID, AiVendor.ANTHROPIC, "claude-opus-5", "msgbatch_abc");
        }

        @Test
        @DisplayName("성공 항목은 본문으로 기록하고 완료를 보고한다")
        void appliesSucceededItems() {
            when(loadAiBatchJobPort.findSubmitted()).thenReturn(List.of(job()));
            stubChapters(chapter(101L, "a", 1), chapter(102L, "b", 1));
            when(aiGateway.pollBatch(any(), anyLong(), any(), anyString(), anyString()))
                    .thenReturn(LlmBatchPollResult.ended(List.of(
                            LlmBatchItemResult.succeeded("101", "본문-101", 100, 200),
                            LlmBatchItemResult.succeeded("102", "본문-102", 100, 200))));

            assertThat(service(true).pollSubmitted()).isEqualTo(1);

            verify(composeChapterBodyUseCase).apply(101L, "본문-101");
            verify(composeChapterBodyUseCase).apply(102L, "본문-102");
            verify(advanceGenerationStageUseCase).chapterBodyCompleted(SKELETON_ID);
            verifyNoInteractions(eventPublisher);
        }

        /**
         * 배치용 재시도 정책을 따로 만들지 않는다. 그게 두 번째 실패 처리 체계가 되고, P2.8 의 재시도·운영자
         * 큐와 어긋나면 어떤 실패는 아무도 재시도하지 않는다.
         */
        @Test
        @DisplayName("실패 항목은 그 챕터만 개별 경로로 넘긴다")
        void failedItemFallsBackToIndividual() {
            when(loadAiBatchJobPort.findSubmitted()).thenReturn(List.of(job()));
            stubChapters(chapter(101L, "a", 1), chapter(102L, "b", 0));
            when(aiGateway.pollBatch(any(), anyLong(), any(), anyString(), anyString()))
                    .thenReturn(LlmBatchPollResult.ended(List.of(
                            LlmBatchItemResult.succeeded("101", "본문-101", 100, 200),
                            LlmBatchItemResult.failed("102", "expired"))));

            service(true).pollSubmitted();

            verify(composeChapterBodyUseCase).apply(101L, "본문-101");
            verify(composeChapterBodyUseCase, never()).apply(eq(102L), anyString());
            verify(eventPublisher).requestBody(SKELETON_ID, 102L, "b");
        }

        /** 파싱 실패·출처 없음. 배치 결과를 재시도할 수는 없으므로 개별 호출로 다시 만든다. */
        @Test
        @DisplayName("반영이 실패한 항목도 개별 경로로 넘긴다")
        void unparseableItemFallsBack() {
            when(loadAiBatchJobPort.findSubmitted()).thenReturn(List.of(job()));
            stubChapters(chapter(101L, "a", 0));
            when(aiGateway.pollBatch(any(), anyLong(), any(), anyString(), anyString()))
                    .thenReturn(
                            LlmBatchPollResult.ended(List.of(LlmBatchItemResult.succeeded("101", "깨진 응답", 100, 200))));
            doThrow(new ExternalApiException("출처가 없습니다"))
                    .when(composeChapterBodyUseCase)
                    .apply(101L, "깨진 응답");

            assertThat(service(true).pollSubmitted()).isEqualTo(1);

            verify(eventPublisher).requestBody(SKELETON_ID, 101L, "a");
            verify(advanceGenerationStageUseCase, never()).chapterBodyCompleted(anyLong());
        }

        /** 벤더가 항목을 누락하면 그 챕터는 성공도 실패도 아닌 상태가 되고, 뼈대가 본문 단계에 갇힌다. */
        @Test
        @DisplayName("결과에 오지 않은 챕터도 개별 경로로 넘긴다")
        void missingItemFallsBack() {
            when(loadAiBatchJobPort.findSubmitted()).thenReturn(List.of(job()));
            stubChapters(chapter(101L, "a", 1), chapter(102L, "b", 0));
            when(aiGateway.pollBatch(any(), anyLong(), any(), anyString(), anyString()))
                    .thenReturn(
                            LlmBatchPollResult.ended(List.of(LlmBatchItemResult.succeeded("101", "본문-101", 100, 200))));

            service(true).pollSubmitted();

            verify(eventPublisher).requestBody(SKELETON_ID, 102L, "b");
        }

        @Test
        @DisplayName("모르는 customId 는 기록만 하고 넘어간다")
        void unknownCustomIdIsSkipped() {
            when(loadAiBatchJobPort.findSubmitted()).thenReturn(List.of(job()));
            stubChapters(chapter(101L, "a", 1));
            when(aiGateway.pollBatch(any(), anyLong(), any(), anyString(), anyString()))
                    .thenReturn(LlmBatchPollResult.ended(List.of(
                            LlmBatchItemResult.succeeded("999", "본문-999", 1, 1),
                            LlmBatchItemResult.succeeded("not-a-number", "본문-x", 1, 1))));

            assertThat(service(true).pollSubmitted()).isEqualTo(1);
            verifyNoInteractions(composeChapterBodyUseCase);
        }

        /** 한 배치의 조회 실패로 전체 스윕이 멈추면 다른 뼈대의 본문이 함께 밀린다. */
        @Test
        @DisplayName("배치 하나가 실패해도 나머지를 계속 처리한다")
        void oneFailureDoesNotStopSweep() {
            AiBatchJob broken = new AiBatchJob(
                    8L,
                    99L,
                    GenerationStage.BODY,
                    AiVendor.ANTHROPIC,
                    "claude-opus-5",
                    "msgbatch_broken",
                    AiBatchStatus.SUBMITTED,
                    1,
                    null,
                    null,
                    null,
                    null);
            when(loadAiBatchJobPort.findSubmitted()).thenReturn(List.of(broken, job()));
            when(aiGateway.pollBatch(any(), eq(99L), any(), anyString(), anyString()))
                    .thenThrow(new ExternalApiException("조회 실패"));
            stubChapters(chapter(101L, "a", 1));
            when(aiGateway.pollBatch(any(), eq(SKELETON_ID), any(), anyString(), anyString()))
                    .thenReturn(
                            LlmBatchPollResult.ended(List.of(LlmBatchItemResult.succeeded("101", "본문-101", 100, 200))));

            assertThat(service(true).pollSubmitted()).isEqualTo(1);
            verify(composeChapterBodyUseCase).apply(101L, "본문-101");
        }

        @Test
        @DisplayName("반영을 끝낸 배치는 COMPLETED 로 닫는다")
        void closesJobAfterApplying() {
            when(loadAiBatchJobPort.findSubmitted()).thenReturn(List.of(job()));
            stubChapters(chapter(101L, "a", 1));
            when(aiGateway.pollBatch(any(), anyLong(), any(), anyString(), anyString()))
                    .thenReturn(
                            LlmBatchPollResult.ended(List.of(LlmBatchItemResult.succeeded("101", "본문-101", 100, 200))));

            service(true).pollSubmitted();

            ArgumentCaptor<AiBatchJob> saved = ArgumentCaptor.forClass(AiBatchJob.class);
            verify(saveAiBatchJobPort).save(saved.capture());
            assertThat(saved.getValue().status()).isEqualTo(AiBatchStatus.COMPLETED);
            assertThat(saved.getValue().completedAt()).isNotNull();
        }
    }
}
