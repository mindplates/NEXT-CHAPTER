package com.mindplates.nextchapter.adapter.out.messaging.generation;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mindplates.nextchapter.application.generation.port.in.RecordGenerationFailureUseCase;
import com.mindplates.nextchapter.domain.generation.model.GenerationStage;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.listener.ConsumerRecordRecoverer;
import org.springframework.stereotype.Component;

/**
 * 재시도를 소진한 메시지를 운영자 큐로 넘긴다.
 *
 * <p>DLT 토픽을 쓰지 않고 DB 테이블에 넣는 이유는 <b>운영자가 볼 수 있어야</b> 하기 때문이다. DLT 는
 * 메시지를 보관하지만 관리 화면에서 목록·상태·처리 이력을 다루려면 결국 저장소가 필요하고, 그러면
 * 토픽과 테이블 두 곳에 같은 사실이 생긴다.
 *
 * <p>여기서 예외를 던지면 안 된다. 던지면 Spring Kafka 가 오프셋을 커밋하지 않아 <b>같은 메시지를 영원히
 * 다시 처리</b>하고, 그 뼈대의 파티션이 막힌다. 그래서 기록 실패는 로그로만 남긴다 — 큐 한 줄을 잃는
 * 것보다 파티션이 막히는 쪽이 훨씬 나쁘다.
 */
@Component
public class GenerationFailureRecoverer implements ConsumerRecordRecoverer {

    private static final Logger log = LoggerFactory.getLogger(GenerationFailureRecoverer.class);

    private final RecordGenerationFailureUseCase recordGenerationFailureUseCase;
    private final GenerationRetryProperties retryProperties;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public GenerationFailureRecoverer(
            RecordGenerationFailureUseCase recordGenerationFailureUseCase, GenerationRetryProperties retryProperties) {
        this.recordGenerationFailureUseCase = recordGenerationFailureUseCase;
        this.retryProperties = retryProperties;
    }

    @Override
    public void accept(ConsumerRecord<?, ?> record, Exception exception) {
        GenerationStage stage = GenerationRetryKafkaPublisher.stageOf(record.topic());
        if (stage == null) {
            // 생성 파이프라인 토픽이 아니다. outbox 컨슈머 등 다른 리스너의 실패는 이 큐의 대상이 아니다.
            log.error("[생성실패] 생성 토픽이 아닌 메시지의 재시도 소진 topic={}", record.topic(), exception);
            return;
        }
        String payload = String.valueOf(record.value());
        try {
            recordGenerationFailureUseCase.record(new RecordGenerationFailureUseCase.GenerationFailureCommand(
                    longAt(payload, "skeletonId"),
                    longAt(payload, "chapterId"),
                    stage,
                    record.topic(),
                    record.partition(),
                    record.offset(),
                    payload,
                    rootCause(exception).getClass().getName(),
                    rootCause(exception).getMessage(),
                    retryProperties.maxAttempts()));
        } catch (Exception recordingFailure) {
            log.error(
                    "[생성실패] 큐 기록에 실패했다. topic={} partition={} offset={}",
                    record.topic(),
                    record.partition(),
                    record.offset(),
                    recordingFailure);
        }
    }

    /**
     * 페이로드를 읽지 못하면 null 이다. 이 경우에도 큐에는 올린다 — <b>읽을 수 없는 메시지가 조용히
     * 사라지는 것</b>이 이 큐를 만든 이유 그 자체다.
     */
    private Long longAt(String payload, String field) {
        try {
            JsonNode node = objectMapper.readTree(payload).get(field);
            return node == null || node.isNull() ? null : node.asLong();
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Spring Kafka 가 원인 예외를 {@code ListenerExecutionFailedException} 으로 감싼다. 감싼 것을 그대로
     * 기록하면 큐의 모든 줄이 같은 오류 종류로 보이고, 그러면 무엇이 실패했는지 구분할 수 없다.
     */
    private static Throwable rootCause(Throwable exception) {
        Throwable current = exception;
        while (current.getCause() != null && current.getCause() != current) {
            current = current.getCause();
        }
        return current;
    }
}
