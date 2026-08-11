package com.mindplates.nextchapter.application.chapter.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.mindplates.nextchapter.application.chapter.port.out.LoadOutboxEventPort;
import com.mindplates.nextchapter.application.chapter.port.out.OutboxPublisherPort;
import com.mindplates.nextchapter.application.chapter.port.out.SaveOutboxEventPort;
import com.mindplates.nextchapter.common.exception.ExternalApiException;
import com.mindplates.nextchapter.domain.chapter.model.OutboxEvent;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("outbox 퍼블리셔")
class OutboxPublishServiceTest {

    @Mock
    LoadOutboxEventPort loadOutboxEventPort;

    @Mock
    SaveOutboxEventPort saveOutboxEventPort;

    @Mock
    OutboxPublisherPort outboxPublisherPort;

    @InjectMocks
    OutboxPublishService service;

    @Test
    @DisplayName("집어 온 이벤트를 발행하고 표시한다")
    void publishesAndMarks() {
        when(loadOutboxEventPort.claimUnpublished(10)).thenReturn(List.of(event(1L), event(2L)));

        assertThat(service.publishPending(10)).isEqualTo(2);

        verify(outboxPublisherPort).publish(event(1L));
        verify(saveOutboxEventPort).markPublished(1L);
        verify(saveOutboxEventPort).markPublished(2L);
    }

    /**
     * 발행 실패 시 표시하지 않는 것이 요점이다. 표시하면 이벤트가 유실되고, Neo4j 반영이 멱등이므로
     * <b>중복 발행은 안전하지만 누락은 안전하지 않다.</b>
     */
    @Test
    @DisplayName("발행이 실패하면 표시하지 않는다 — 다음 폴링이 다시 집어 간다")
    void doesNotMarkOnFailure() {
        when(loadOutboxEventPort.claimUnpublished(10)).thenReturn(List.of(event(1L)));
        doThrow(new ExternalApiException("브로커 없음")).when(outboxPublisherPort).publish(any());

        assertThat(service.publishPending(10)).isZero();

        verify(saveOutboxEventPort, never()).markPublished(any());
    }

    @Test
    @DisplayName("한 건이 실패해도 뒤의 이벤트를 계속 발행한다")
    void isolatesFailurePerEvent() {
        when(loadOutboxEventPort.claimUnpublished(10)).thenReturn(List.of(event(1L), event(2L)));
        doThrow(new ExternalApiException("일시 실패")).when(outboxPublisherPort).publish(event(1L));

        assertThat(service.publishPending(10)).isEqualTo(1);

        verify(saveOutboxEventPort, never()).markPublished(1L);
        verify(saveOutboxEventPort).markPublished(2L);
    }

    @Test
    @DisplayName("밀린 것이 없으면 0 이다")
    void nothingPending() {
        when(loadOutboxEventPort.claimUnpublished(10)).thenReturn(List.of());

        assertThat(service.publishPending(10)).isZero();
    }

    private static OutboxEvent event(Long id) {
        return new OutboxEvent(
                id,
                "CHAPTER",
                "100",
                com.mindplates.nextchapter.domain.chapter.model.OutboxEventType.CHAPTER_PERSISTED,
                "5",
                "chapter:100:v" + id,
                "{}",
                LocalDateTime.of(2026, 8, 12, 3, 0),
                null);
    }
}
