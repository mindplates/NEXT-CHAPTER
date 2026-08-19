package com.mindplates.nextchapter.adapter.out.messaging.signal;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mindplates.nextchapter.application.signal.port.in.RecordCollectiveSignalUseCase;
import com.mindplates.nextchapter.domain.chapter.model.BlockIds;
import com.mindplates.nextchapter.domain.chapter.model.DeliveryFormat;
import com.mindplates.nextchapter.domain.signal.model.CollectiveSignalEvent;
import com.mindplates.nextchapter.domain.signal.model.SignalType;
import java.time.LocalDateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * 집단 루프 집계 컨슈머 — {@link SignalKafkaPublisher} 가 실은 신호를 원장으로 옮긴다.
 *
 * <p><b>보충 블록과 PROGRESS 를 여기서 거른다.</b> 보충 블록은 그 사용자에게만 있는 블록이라 여럿의
 * 반응을 겹쳐 볼 수 없고, PROGRESS 는 블록이 없어 애초에 블록 단위 집계 대상이 아니다. 걸러진 메시지도
 * 정상 처리로 본다 — 예외를 올리지 않는다. 그래야 파티션이 막히지 않는다.
 */
@Component
public class CollectiveSignalListener {

    private static final Logger log = LoggerFactory.getLogger(CollectiveSignalListener.class);

    private final RecordCollectiveSignalUseCase recordCollectiveSignalUseCase;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public CollectiveSignalListener(RecordCollectiveSignalUseCase recordCollectiveSignalUseCase) {
        this.recordCollectiveSignalUseCase = recordCollectiveSignalUseCase;
    }

    @KafkaListener(topics = "${nextchapter.kafka.signal.prefix:}" + SignalTopicProperties.RECORDED)
    public void onSignalRecorded(String payload) throws Exception {
        JsonNode node = objectMapper.readTree(payload);
        long signalId = node.path("signalId").asLong();

        if (node.path("supplementBlock").asBoolean(false)) {
            log.debug("[집단 신호] 보충 블록 신호를 건너뛴다 signalId={}", signalId);
            return;
        }
        String blockId = node.path("blockId").asText(null);
        if (!BlockIds.isBody(blockId)) {
            log.debug("[집단 신호] 블록 단위가 아닌 신호를 건너뛴다 signalId={}", signalId);
            return;
        }

        recordCollectiveSignalUseCase.record(new CollectiveSignalEvent(
                signalId,
                node.path("chapterId").asLong(),
                node.path("chapterVersion").asInt(),
                blockId,
                DeliveryFormat.valueOf(node.path("format").asText()),
                SignalType.valueOf(node.path("type").asText()),
                correctOf(node),
                LocalDateTime.parse(node.path("occurredAt").asText())));
    }

    private static Boolean correctOf(JsonNode node) {
        JsonNode correct = node.path("payload").path("correct");
        return correct.isMissingNode() || correct.isNull() ? null : correct.asBoolean();
    }
}
