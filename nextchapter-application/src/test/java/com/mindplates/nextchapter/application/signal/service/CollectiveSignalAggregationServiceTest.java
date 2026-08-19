package com.mindplates.nextchapter.application.signal.service;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.mindplates.nextchapter.application.admin.port.out.LoadCollectiveThresholdSettingsPort;
import com.mindplates.nextchapter.application.signal.port.out.LoadCollectiveSignalAggregatePort;
import com.mindplates.nextchapter.application.signal.port.out.SaveCollectiveSignalEventPort;
import com.mindplates.nextchapter.application.signal.port.out.SaveRevisionTriggerPort;
import com.mindplates.nextchapter.domain.admin.model.CollectiveThresholdSettings;
import com.mindplates.nextchapter.domain.chapter.model.DeliveryFormat;
import com.mindplates.nextchapter.domain.signal.model.BlockSignalAggregate;
import com.mindplates.nextchapter.domain.signal.model.CollectiveSignalEvent;
import com.mindplates.nextchapter.domain.signal.model.SignalType;
import java.time.LocalDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * 재수신을 건너뛴 것도 정상 종료여야 한다 — 예외를 올리면 그 뒤의 정상 메시지까지 막힌다. 임계치 검사는
 * <b>새로 저장됐을 때만</b> 돈다 — 재수신은 집계를 바꾸지 않는다.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("집단 신호 집계")
class CollectiveSignalAggregationServiceTest {

    @Mock
    SaveCollectiveSignalEventPort saveCollectiveSignalEventPort;

    @Mock
    LoadCollectiveSignalAggregatePort loadCollectiveSignalAggregatePort;

    @Mock
    LoadCollectiveThresholdSettingsPort loadCollectiveThresholdSettingsPort;

    @Mock
    SaveRevisionTriggerPort saveRevisionTriggerPort;

    CollectiveSignalAggregationService service;

    @BeforeEach
    void setUp() {
        service = new CollectiveSignalAggregationService(
                saveCollectiveSignalEventPort,
                loadCollectiveSignalAggregatePort,
                loadCollectiveThresholdSettingsPort,
                saveRevisionTriggerPort);
    }

    private static CollectiveSignalEvent event() {
        return new CollectiveSignalEvent(
                1L, 100L, 2, "b6", DeliveryFormat.WEB, SignalType.QUESTION, null, LocalDateTime.of(2026, 8, 12, 9, 0));
    }

    private static CollectiveThresholdSettings settings(int questionThreshold) {
        return new CollectiveThresholdSettings(questionThreshold, 20, 40, null, null, null);
    }

    @Test
    @DisplayName("원장에 저장한다")
    void savesToLedger() {
        when(saveCollectiveSignalEventPort.saveIfAbsent(any())).thenReturn(true);
        when(loadCollectiveSignalAggregatePort.aggregate(100L, 2, "b6"))
                .thenReturn(new BlockSignalAggregate(100L, 2, "b6", 1, 0, 0));
        when(loadCollectiveThresholdSettingsPort.load()).thenReturn(settings(5));

        service.record(event());

        verify(saveCollectiveSignalEventPort).saveIfAbsent(event());
    }

    @Test
    @DisplayName("이미 있는 신호는 건너뛰어도 예외가 아니다 — 임계치도 다시 검사하지 않는다")
    void skippingDuplicateDoesNotThrowOrRecheck() {
        when(saveCollectiveSignalEventPort.saveIfAbsent(any())).thenReturn(false);

        assertThatCode(() -> service.record(event())).doesNotThrowAnyException();

        verify(loadCollectiveSignalAggregatePort, never()).aggregate(any(), anyInt(), any());
    }

    @Test
    @DisplayName("임계치를 넘으면 수정 트리거를 남긴다")
    void triggersRevisionWhenThresholdExceeded() {
        when(saveCollectiveSignalEventPort.saveIfAbsent(any())).thenReturn(true);
        when(loadCollectiveSignalAggregatePort.aggregate(100L, 2, "b6"))
                .thenReturn(new BlockSignalAggregate(100L, 2, "b6", 5, 0, 0));
        when(loadCollectiveThresholdSettingsPort.load()).thenReturn(settings(5));
        when(saveRevisionTriggerPort.saveIfAbsent(any())).thenReturn(true);

        service.record(event());

        verify(saveRevisionTriggerPort).saveIfAbsent(any());
    }

    @Test
    @DisplayName("임계치 미만이면 트리거를 남기지 않는다")
    void doesNotTriggerBelowThreshold() {
        when(saveCollectiveSignalEventPort.saveIfAbsent(any())).thenReturn(true);
        when(loadCollectiveSignalAggregatePort.aggregate(100L, 2, "b6"))
                .thenReturn(new BlockSignalAggregate(100L, 2, "b6", 3, 0, 0));
        when(loadCollectiveThresholdSettingsPort.load()).thenReturn(settings(5));

        service.record(event());

        verify(saveRevisionTriggerPort, never()).saveIfAbsent(any());
    }
}
