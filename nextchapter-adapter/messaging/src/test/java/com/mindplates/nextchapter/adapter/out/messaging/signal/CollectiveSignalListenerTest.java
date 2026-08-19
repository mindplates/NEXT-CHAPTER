package com.mindplates.nextchapter.adapter.out.messaging.signal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.mindplates.nextchapter.application.signal.port.in.RecordCollectiveSignalUseCase;
import com.mindplates.nextchapter.domain.chapter.model.DeliveryFormat;
import com.mindplates.nextchapter.domain.signal.model.CollectiveSignalEvent;
import com.mindplates.nextchapter.domain.signal.model.SignalType;
import java.time.LocalDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** 보충 블록과 PROGRESS 를 걸러내는 것이 이 컨슈머의 핵심이다 — 원장에는 공용 본문 블록만 들어간다. */
@ExtendWith(MockitoExtension.class)
@DisplayName("집단 신호 컨슈머")
class CollectiveSignalListenerTest {

    @Mock
    RecordCollectiveSignalUseCase recordCollectiveSignalUseCase;

    CollectiveSignalListener listener;

    @BeforeEach
    void setUp() {
        listener = new CollectiveSignalListener(recordCollectiveSignalUseCase);
    }

    @Test
    @DisplayName("질문 신호를 원장 기록으로 옮긴다")
    void dispatchesQuestion() throws Exception {
        listener.onSignalRecorded(
                "{\"signalId\":1,\"userId\":42,\"chapterId\":100,\"chapterVersion\":2,\"blockId\":\"b6\","
                        + "\"supplementBlock\":false,\"format\":\"WEB\",\"type\":\"QUESTION\","
                        + "\"payload\":{\"text\":\"왜?\"},\"occurredAt\":\"2026-08-12T09:00:00\"}");

        ArgumentCaptor<CollectiveSignalEvent> captor = ArgumentCaptor.forClass(CollectiveSignalEvent.class);
        verify(recordCollectiveSignalUseCase).record(captor.capture());
        CollectiveSignalEvent event = captor.getValue();
        assertThat(event.signalId()).isEqualTo(1L);
        assertThat(event.chapterId()).isEqualTo(100L);
        assertThat(event.chapterVersion()).isEqualTo(2);
        assertThat(event.blockId()).isEqualTo("b6");
        assertThat(event.format()).isEqualTo(DeliveryFormat.WEB);
        assertThat(event.type()).isEqualTo(SignalType.QUESTION);
        assertThat(event.correct()).isNull();
        assertThat(event.occurredAt()).isEqualTo(LocalDateTime.of(2026, 8, 12, 9, 0));
    }

    @Test
    @DisplayName("퀴즈 응답의 정답 여부를 payload 에서 읽는다")
    void extractsCorrectFlag() throws Exception {
        listener.onSignalRecorded("{\"signalId\":2,\"chapterId\":100,\"chapterVersion\":2,\"blockId\":\"b6\","
                + "\"supplementBlock\":false,\"format\":\"WEB\",\"type\":\"QUIZ_ANSWER\","
                + "\"payload\":{\"correct\":false,\"choice\":\"가\"},"
                + "\"occurredAt\":\"2026-08-12T09:00:00\"}");

        ArgumentCaptor<CollectiveSignalEvent> captor = ArgumentCaptor.forClass(CollectiveSignalEvent.class);
        verify(recordCollectiveSignalUseCase).record(captor.capture());
        assertThat(captor.getValue().correct()).isFalse();
    }

    @Test
    @DisplayName("보충 블록 신호는 건너뛴다")
    void skipsSupplementBlock() throws Exception {
        listener.onSignalRecorded("{\"signalId\":3,\"chapterId\":100,\"chapterVersion\":2,\"blockId\":\"s1\","
                + "\"supplementBlock\":true,\"format\":\"WEB\",\"type\":\"QUESTION\","
                + "\"payload\":{\"text\":\"왜?\"},\"occurredAt\":\"2026-08-12T09:00:00\"}");

        verifyNoInteractions(recordCollectiveSignalUseCase);
    }

    @Test
    @DisplayName("블록이 없는 PROGRESS 신호는 건너뛴다")
    void skipsProgressWithoutBlock() throws Exception {
        listener.onSignalRecorded("{\"signalId\":4,\"chapterId\":100,\"chapterVersion\":2,\"blockId\":null,"
                + "\"supplementBlock\":false,\"format\":\"WEB\",\"type\":\"PROGRESS\","
                + "\"payload\":{\"percent\":100},\"occurredAt\":\"2026-08-12T09:00:00\"}");

        verifyNoInteractions(recordCollectiveSignalUseCase);
    }
}
