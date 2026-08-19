package com.mindplates.nextchapter.application.signal.service;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.mindplates.nextchapter.application.signal.port.out.SaveCollectiveSignalEventPort;
import com.mindplates.nextchapter.domain.chapter.model.DeliveryFormat;
import com.mindplates.nextchapter.domain.signal.model.CollectiveSignalEvent;
import com.mindplates.nextchapter.domain.signal.model.SignalType;
import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** 재수신을 건너뛴 것도 정상 종료여야 한다 — 예외를 올리면 그 뒤의 정상 메시지까지 막힌다. */
@ExtendWith(MockitoExtension.class)
@DisplayName("집단 신호 집계")
class CollectiveSignalAggregationServiceTest {

    @Mock
    SaveCollectiveSignalEventPort saveCollectiveSignalEventPort;

    CollectiveSignalAggregationService service;

    private static CollectiveSignalEvent event() {
        return new CollectiveSignalEvent(
                1L, 100L, 2, "b6", DeliveryFormat.WEB, SignalType.QUESTION, null, LocalDateTime.of(2026, 8, 12, 9, 0));
    }

    @Test
    @DisplayName("원장에 저장한다")
    void savesToLedger() {
        service = new CollectiveSignalAggregationService(saveCollectiveSignalEventPort);
        when(saveCollectiveSignalEventPort.saveIfAbsent(any())).thenReturn(true);

        service.record(event());

        verify(saveCollectiveSignalEventPort).saveIfAbsent(event());
    }

    @Test
    @DisplayName("이미 있는 신호는 건너뛰어도 예외가 아니다")
    void skippingDuplicateDoesNotThrow() {
        service = new CollectiveSignalAggregationService(saveCollectiveSignalEventPort);
        when(saveCollectiveSignalEventPort.saveIfAbsent(any())).thenReturn(false);

        assertThatCode(() -> service.record(event())).doesNotThrowAnyException();
    }
}
