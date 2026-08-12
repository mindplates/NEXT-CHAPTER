package com.mindplates.nextchapter.application.signal.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.mindplates.nextchapter.application.chapter.port.out.ChapterDocumentCachePort;
import com.mindplates.nextchapter.application.chapter.port.out.ChapterDocumentCachePort.CachedChapterDocument;
import com.mindplates.nextchapter.application.chapter.port.out.LoadChapterPort;
import com.mindplates.nextchapter.application.chapter.port.out.LoadChapterVersionPort;
import com.mindplates.nextchapter.application.chapter.service.SharedChapterDocuments;
import com.mindplates.nextchapter.application.signal.port.in.command.RecordSignalCommand;
import com.mindplates.nextchapter.application.signal.port.out.LoadSignalPort;
import com.mindplates.nextchapter.application.signal.port.out.SaveSignalPort;
import com.mindplates.nextchapter.application.signal.port.out.SignalPublisherPort;
import com.mindplates.nextchapter.application.signal.view.SignalView;
import com.mindplates.nextchapter.application.skeleton.port.out.LoadSkeletonPort;
import com.mindplates.nextchapter.application.skeleton.service.PublishedSkeletonGuard;
import com.mindplates.nextchapter.common.exception.EntityNotFoundException;
import com.mindplates.nextchapter.common.exception.InfrastructureException;
import com.mindplates.nextchapter.common.exception.InvalidOperationException;
import com.mindplates.nextchapter.domain.chapter.model.Block;
import com.mindplates.nextchapter.domain.chapter.model.BlockDocument;
import com.mindplates.nextchapter.domain.chapter.model.BlockType;
import com.mindplates.nextchapter.domain.chapter.model.Chapter;
import com.mindplates.nextchapter.domain.chapter.model.ChapterVersion;
import com.mindplates.nextchapter.domain.chapter.model.ChapterVersionSource;
import com.mindplates.nextchapter.domain.chapter.model.DeliveryFormat;
import com.mindplates.nextchapter.domain.signal.model.Signal;
import com.mindplates.nextchapter.domain.signal.model.SignalType;
import com.mindplates.nextchapter.domain.skeleton.model.Skeleton;
import com.mindplates.nextchapter.domain.skeleton.model.SkeletonStatus;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * 신호 경로에서 조용히 틀릴 수 있는 것 두 가지를 고정한다.
 *
 * <ul>
 *   <li>없는 블록에 붙은 신호를 통과시키면 "질문은 많은데 고칠 지점을 찾을 수 없는" 상태가 된다
 *   <li>발행 실패에 발행 표시를 커밋하면 그 신호가 집계에 영원히 도달하지 않는다
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("신호")
class SignalServiceTest {

    @Mock
    LoadSkeletonPort loadSkeletonPort;

    @Mock
    LoadChapterPort loadChapterPort;

    @Mock
    LoadChapterVersionPort loadChapterVersionPort;

    @Mock
    ChapterDocumentCachePort chapterDocumentCachePort;

    @Mock
    SaveSignalPort saveSignalPort;

    @Mock
    LoadSignalPort loadSignalPort;

    @Mock
    SignalPublisherPort signalPublisherPort;

    @Nested
    @DisplayName("기록")
    class Recording {

        SignalRecordingService service;

        @BeforeEach
        void setUp() {
            service = new SignalRecordingService(
                    new PublishedSkeletonGuard(loadSkeletonPort),
                    loadChapterPort,
                    loadChapterVersionPort,
                    new SharedChapterDocuments(loadChapterVersionPort, chapterDocumentCachePort),
                    saveSignalPort);
        }

        private void stubPublishedChapter() {
            when(loadChapterPort.findById(100L))
                    .thenReturn(
                            Optional.of(new Chapter(100L, 5L, "gradient-descent", "경사하강법", "요약", 3, 6, 0, null, null)));
            when(loadSkeletonPort.findById(5L))
                    .thenReturn(Optional.of(new Skeleton(5L, 77L, SkeletonStatus.PUBLISHED, null, null)));
        }

        private void stubDocument(int version) {
            when(chapterDocumentCachePort.find(100L, version, DeliveryFormat.WEB))
                    .thenReturn(Optional.of(new CachedChapterDocument(
                            100L,
                            version,
                            DeliveryFormat.WEB,
                            List.of(
                                    Block.text("b1", BlockType.HEADING, "제목"),
                                    Block.quiz("b6", "질문", List.of("가", "나"), "가")),
                            false,
                            null,
                            null)));
        }

        /** 클라이언트는 <b>고른 답</b>만 보낸다. {@code correct} 는 서버가 채운다. */
        private static RecordSignalCommand quiz(int version, String blockId) {
            return new RecordSignalCommand(
                    100L, version, blockId, DeliveryFormat.WEB, SignalType.QUIZ_ANSWER, Map.of("choice", "가"));
        }

        /** 채점은 원천(버전 스냅샷)을 읽는다 — 사용자에게 내려간 문서에는 정답이 없다. */
        private void stubSnapshot(String answer) {
            when(loadChapterVersionPort.find(100L, 2))
                    .thenReturn(Optional.of(new ChapterVersion(
                            1L,
                            100L,
                            2,
                            BlockDocument.of(
                                    Block.text("b1", BlockType.HEADING, "제목"),
                                    Block.quiz("b6", "질문", List.of("가", "나"), answer)),
                            null,
                            ChapterVersionSource.GENERATED,
                            "pipeline",
                            null)));
        }

        @Test
        @DisplayName("퀴즈 응답이 블록 ID·형태·소비한 버전을 달고 기록된다")
        void recordsQuizAnswer() {
            stubPublishedChapter();
            stubDocument(2);
            stubSnapshot("가");
            when(saveSignalPort.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

            SignalView view = service.record(42L, quiz(2, "b6"));

            ArgumentCaptor<Signal> saved = ArgumentCaptor.forClass(Signal.class);
            verify(saveSignalPort).save(saved.capture());
            assertThat(saved.getValue().userId()).isEqualTo(42L);
            assertThat(saved.getValue().blockId()).isEqualTo("b6");
            assertThat(saved.getValue().chapterVersion()).isEqualTo(2);
            assertThat(saved.getValue().format()).isEqualTo(DeliveryFormat.WEB);
            assertThat(view.type()).isEqualTo(SignalType.QUIZ_ANSWER);
        }

        /**
         * 사용자가 읽는 사이에 수정이 반영될 수 있다. 최신 버전으로 검증하면 그 사람의 정상 신호가
         * 거절되고, 최신 버전으로 <b>기록</b>하면 구버전에 대한 반응이 새 버전의 실패로 집계된다.
         */
        @Test
        @DisplayName("검증은 소비한 버전으로 한다 — 최신 버전이 아니다")
        void validatesAgainstConsumedVersion() {
            stubPublishedChapter();
            stubDocument(2);
            stubSnapshot("가");
            when(saveSignalPort.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

            service.record(42L, quiz(2, "b6"));

            verify(chapterDocumentCachePort).find(100L, 2, DeliveryFormat.WEB);
        }

        /**
         * <b>서버가 채점한다.</b> 클라이언트가 {@code correct} 를 보내면 그 값은 위조할 수 있고, 오답률이
         * 위조 가능해지는 순간 임계치 판정과 수정 전후 비교가 근거를 잃는다.
         */
        @Test
        @DisplayName("정답 대조는 서버가 한다 — 클라이언트가 보낸 correct 를 믿지 않는다")
        void gradesOnServer() {
            stubPublishedChapter();
            stubDocument(2);
            stubSnapshot("나");
            when(saveSignalPort.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

            service.record(
                    42L,
                    new RecordSignalCommand(
                            100L,
                            2,
                            "b6",
                            DeliveryFormat.WEB,
                            SignalType.QUIZ_ANSWER,
                            Map.of("choice", "가", "correct", true)));

            ArgumentCaptor<Signal> saved = ArgumentCaptor.forClass(Signal.class);
            verify(saveSignalPort).save(saved.capture());
            // 고른 답이 "가", 정답이 "나" 이므로 오답이다 — 클라이언트가 true 를 보냈어도 덮어쓴다.
            assertThat(saved.getValue().payload()).containsEntry("correct", false);
        }

        @Test
        @DisplayName("정답을 고르면 correct 가 참이다")
        void gradesCorrectAnswer() {
            stubPublishedChapter();
            stubDocument(2);
            stubSnapshot("가");
            when(saveSignalPort.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

            service.record(42L, quiz(2, "b6"));

            ArgumentCaptor<Signal> saved = ArgumentCaptor.forClass(Signal.class);
            verify(saveSignalPort).save(saved.capture());
            assertThat(saved.getValue().payload()).containsEntry("correct", true);
        }

        /** 고른 답이 없으면 채점할 수 없다. 저장하면 정답 여부 없는 퀴즈 신호가 남는다. */
        @Test
        @DisplayName("고른 답이 없으면 거절한다")
        void rejectsQuizWithoutChoice() {
            stubPublishedChapter();
            stubDocument(2);

            assertThatThrownBy(() -> service.record(
                            42L,
                            new RecordSignalCommand(
                                    100L, 2, "b6", DeliveryFormat.WEB, SignalType.QUIZ_ANSWER, Map.of())))
                    .isInstanceOf(InvalidOperationException.class);
            verify(saveSignalPort, never()).save(any());
        }

        /** 퀴즈가 아닌 블록에 퀴즈 응답을 붙이면 채점할 정답이 없다 — 그 신호는 오답률을 왜곡한다. */
        @Test
        @DisplayName("퀴즈가 아닌 블록에는 퀴즈 응답을 남길 수 없다")
        void rejectsQuizAnswerOnNonQuizBlock() {
            stubPublishedChapter();
            stubDocument(2);
            stubSnapshot("가");

            assertThatThrownBy(() -> service.record(42L, quiz(2, "b1"))).isInstanceOf(InvalidOperationException.class);
        }

        /** 없는 블록에 붙은 신호는 집계에 기여하지 않으면서 건수만 늘린다. */
        @Test
        @DisplayName("그 버전에 없는 블록은 거절한다")
        void rejectsUnknownBlock() {
            stubPublishedChapter();
            stubDocument(2);

            assertThatThrownBy(() -> service.record(42L, quiz(2, "b99"))).isInstanceOf(InvalidOperationException.class);
            verify(saveSignalPort, never()).save(any());
        }

        /** 보충 블록은 그 사용자의 레이어에만 있다. 거절하면 개인화 블록에 대한 질문을 받을 수 없다. */
        @Test
        @DisplayName("보충 블록은 공용 문서에 없어도 통과한다")
        void allowsSupplementBlock() {
            stubPublishedChapter();
            when(saveSignalPort.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

            service.record(
                    42L,
                    new RecordSignalCommand(
                            100L, 2, "s1", DeliveryFormat.WEB, SignalType.QUESTION, Map.of("text", "왜?")));

            verify(chapterDocumentCachePort, never()).find(anyLong(), anyInt(), any());
            verify(saveSignalPort).save(any());
        }

        @Test
        @DisplayName("소비 진행은 블록 확인을 하지 않는다")
        void progressSkipsBlockCheck() {
            stubPublishedChapter();
            when(saveSignalPort.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

            service.record(
                    42L,
                    new RecordSignalCommand(
                            100L, 2, null, DeliveryFormat.WEB, SignalType.PROGRESS, Map.of("percent", 100)));

            verify(chapterDocumentCachePort, never()).find(anyLong(), anyInt(), any());
        }

        @Test
        @DisplayName("형태를 주지 않으면 웹이다")
        void defaultsToWeb() {
            stubPublishedChapter();
            when(saveSignalPort.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

            SignalView view = service.record(
                    42L, new RecordSignalCommand(100L, 2, null, null, SignalType.PROGRESS, Map.of("percent", 10)));

            assertThat(view.format()).isEqualTo(DeliveryFormat.WEB);
        }

        @Test
        @DisplayName("공개되지 않은 뼈대의 챕터에는 신호를 남길 수 없다")
        void rejectsUnpublished() {
            when(loadChapterPort.findById(100L))
                    .thenReturn(Optional.of(new Chapter(100L, 5L, "k", "제목", null, 3, 6, 0, null, null)));
            when(loadSkeletonPort.findById(5L))
                    .thenReturn(Optional.of(new Skeleton(5L, 77L, SkeletonStatus.GENERATING_BODIES, null, null)));

            assertThatThrownBy(() -> service.record(42L, quiz(2, "b6"))).isInstanceOf(EntityNotFoundException.class);
            verify(saveSignalPort, never()).save(any());
        }

        @Test
        @DisplayName("없는 챕터는 404 다")
        void rejectsMissingChapter() {
            when(loadChapterPort.findById(100L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.record(42L, quiz(2, "b6"))).isInstanceOf(EntityNotFoundException.class);
        }
    }

    @Nested
    @DisplayName("발행")
    class Publishing {

        SignalPublishService service;

        @BeforeEach
        void setUp() {
            service = new SignalPublishService(loadSignalPort, saveSignalPort, signalPublisherPort);
        }

        /** {@code occurredAt} 을 고정한다 — null 로 두면 호출마다 now() 가 들어가 같은 신호가 다른 값이 된다. */
        private static Signal signal(Long id) {
            return new Signal(
                    id,
                    42L,
                    100L,
                    2,
                    "b6",
                    DeliveryFormat.WEB,
                    SignalType.QUIZ_ANSWER,
                    Map.of("correct", false),
                    LocalDateTime.of(2026, 8, 12, 9, 0),
                    null);
        }

        @Test
        @DisplayName("발행에 성공한 신호만 표시한다")
        void marksPublishedAfterSend() {
            when(loadSignalPort.claimUnpublished(10)).thenReturn(List.of(signal(1L), signal(2L)));

            assertThat(service.publishPending(10)).isEqualTo(2);

            verify(saveSignalPort).markPublished(1L);
            verify(saveSignalPort).markPublished(2L);
        }

        /**
         * 표시를 먼저 커밋하면 발행 실패가 곧 유실이 되고, 그 유실은 "임계치가 트리거되지 않는다"로
         * 조용히 나타난다. 실패한 신호는 표시하지 않아 다음 폴링이 다시 시도한다.
         */
        @Test
        @DisplayName("발행이 실패하면 표시하지 않는다 — 다음 폴링이 다시 시도한다")
        void failureLeavesUnpublished() {
            when(loadSignalPort.claimUnpublished(10)).thenReturn(List.of(signal(1L), signal(2L)));
            doThrow(new InfrastructureException("boom") {})
                    .when(signalPublisherPort)
                    .publish(signal(1L));

            assertThat(service.publishPending(10)).isEqualTo(1);

            verify(saveSignalPort, never()).markPublished(1L);
            verify(saveSignalPort).markPublished(2L);
        }

        @Test
        @DisplayName("보낼 것이 없으면 0 이다")
        void nothingToPublish() {
            when(loadSignalPort.claimUnpublished(10)).thenReturn(List.of());

            assertThat(service.publishPending(10)).isZero();
        }
    }
}
