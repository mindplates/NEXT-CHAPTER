package com.mindplates.nextchapter.application.signal.service;

import com.mindplates.nextchapter.application.signal.port.in.RecordCollectiveSignalUseCase;
import com.mindplates.nextchapter.application.signal.port.out.SaveCollectiveSignalEventPort;
import com.mindplates.nextchapter.domain.signal.model.CollectiveSignalEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 집단 루프 집계 원장 기록.
 *
 * <p>중복 저장을 건너뛴 것도 정상이다. Kafka 가 at-least-once 라 같은 신호가 다시 올 수 있고, 그때
 * {@code signalId} 로 걸리는 저장 건너뜀이 곧 멱등성의 증거다 — 예외가 아니다.
 */
@Service
@Transactional
public class CollectiveSignalAggregationService implements RecordCollectiveSignalUseCase {

    private static final Logger log = LoggerFactory.getLogger(CollectiveSignalAggregationService.class);

    private final SaveCollectiveSignalEventPort saveCollectiveSignalEventPort;

    public CollectiveSignalAggregationService(SaveCollectiveSignalEventPort saveCollectiveSignalEventPort) {
        this.saveCollectiveSignalEventPort = saveCollectiveSignalEventPort;
    }

    @Override
    public void record(CollectiveSignalEvent event) {
        boolean saved = saveCollectiveSignalEventPort.saveIfAbsent(event);
        if (!saved) {
            log.debug("[집단 신호] 이미 집계된 신호를 건너뛴다 signalId={}", event.signalId());
        }
    }
}
