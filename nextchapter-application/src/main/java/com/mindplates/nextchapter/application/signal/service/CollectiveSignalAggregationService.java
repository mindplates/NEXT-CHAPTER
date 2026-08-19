package com.mindplates.nextchapter.application.signal.service;

import com.mindplates.nextchapter.application.admin.port.out.LoadCollectiveThresholdSettingsPort;
import com.mindplates.nextchapter.application.signal.port.in.RecordCollectiveSignalUseCase;
import com.mindplates.nextchapter.application.signal.port.out.LoadCollectiveSignalAggregatePort;
import com.mindplates.nextchapter.application.signal.port.out.SaveCollectiveSignalEventPort;
import com.mindplates.nextchapter.application.signal.port.out.SaveRevisionTriggerPort;
import com.mindplates.nextchapter.domain.admin.model.CollectiveThresholdSettings;
import com.mindplates.nextchapter.domain.signal.model.BlockSignalAggregate;
import com.mindplates.nextchapter.domain.signal.model.CollectiveSignalEvent;
import com.mindplates.nextchapter.domain.signal.model.RevisionTrigger;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 집단 루프 집계 원장 기록과 임계치 검사.
 *
 * <p>중복 저장을 건너뛴 것도 정상이다. Kafka 가 at-least-once 라 같은 신호가 다시 올 수 있고, 그때
 * {@code signalId} 로 걸리는 저장 건너뜀이 곧 멱등성의 증거다 — 예외가 아니다. <b>새로 저장됐을 때만</b>
 * 임계치를 검사한다 — 재수신은 집계를 바꾸지 않으므로 다시 검사할 이유가 없다.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class CollectiveSignalAggregationService implements RecordCollectiveSignalUseCase {

    private static final Logger log = LoggerFactory.getLogger(CollectiveSignalAggregationService.class);

    private final SaveCollectiveSignalEventPort saveCollectiveSignalEventPort;
    private final LoadCollectiveSignalAggregatePort loadCollectiveSignalAggregatePort;
    private final LoadCollectiveThresholdSettingsPort loadCollectiveThresholdSettingsPort;
    private final SaveRevisionTriggerPort saveRevisionTriggerPort;

    @Override
    public void record(CollectiveSignalEvent event) {
        boolean saved = saveCollectiveSignalEventPort.saveIfAbsent(event);
        if (!saved) {
            log.debug("[집단 신호] 이미 집계된 신호를 건너뛴다 signalId={}", event.signalId());
            return;
        }
        checkThreshold(event);
    }

    private void checkThreshold(CollectiveSignalEvent event) {
        BlockSignalAggregate aggregate =
                loadCollectiveSignalAggregatePort.aggregate(event.chapterId(), event.chapterVersion(), event.blockId());
        CollectiveThresholdSettings settings = loadCollectiveThresholdSettingsPort.load();
        if (!settings.isTriggered(aggregate)) {
            return;
        }

        boolean triggered = saveRevisionTriggerPort.saveIfAbsent(RevisionTrigger.of(aggregate));
        if (triggered) {
            log.info(
                    "[집단 신호] 임계치 초과 — 수정 트리거 chapterId={} version={} blockId={} 질문={} 시도={} 오답={}",
                    aggregate.chapterId(),
                    aggregate.chapterVersion(),
                    aggregate.blockId(),
                    aggregate.questionCount(),
                    aggregate.attemptCount(),
                    aggregate.wrongCount());
        }
    }
}
