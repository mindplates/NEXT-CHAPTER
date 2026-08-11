package com.mindplates.nextchapter.adapter.out.messaging.outbox;

import com.mindplates.nextchapter.application.chapter.port.out.OutboxPublisherPort;
import com.mindplates.nextchapter.common.exception.InfrastructureException;
import com.mindplates.nextchapter.domain.chapter.model.OutboxEvent;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Component;

/**
 * outbox 이벤트를 Kafka 로 내보낸다.
 *
 * <p>이벤트 종류와 멱등 키를 <b>헤더</b>로 싣는다. 컨슈머가 페이로드를 파싱하기 전에 종류를 알아야
 * 분기할 수 있고, 멱등 키는 로그에서 "이 반영이 몇 번째 시도인가"를 보는 데 쓴다. 페이로드에 넣으면
 * 파싱 실패 시 그 정보까지 함께 잃는다.
 */
@Component
public class OutboxKafkaPublisher implements OutboxPublisherPort {

    /** 컨슈머와 테스트가 같은 값을 써야 하므로 공개한다. 어긋나면 분기가 조용히 실패한다. */
    public static final String EVENT_TYPE_HEADER = "nc-event-type";

    public static final String IDEMPOTENCY_KEY_HEADER = "nc-idempotency-key";

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final OutboxTopicProperties topics;

    public OutboxKafkaPublisher(KafkaTemplate<String, String> kafkaTemplate, OutboxTopicProperties topics) {
        this.kafkaTemplate = kafkaTemplate;
        this.topics = topics;
    }

    @Override
    public void publish(OutboxEvent event) {
        try {
            kafkaTemplate.send(MessageBuilder.withPayload(event.payload())
                    .setHeader(KafkaHeaders.TOPIC, topics.chapterPersisted())
                    .setHeader(KafkaHeaders.KEY, event.partitionKey())
                    .setHeader(EVENT_TYPE_HEADER, event.eventType().name())
                    .setHeader(IDEMPOTENCY_KEY_HEADER, event.idempotencyKey())
                    .build());
        } catch (Exception e) {
            throw new OutboxPublishFailed(event.id(), e);
        }
    }

    /** 발행 실패를 삼키지 않는다 — 삼키면 발행 표시가 커밋되어 이벤트가 유실된다. */
    static class OutboxPublishFailed extends InfrastructureException {

        OutboxPublishFailed(Long eventId, Throwable cause) {
            super("outbox 이벤트 발행에 실패했습니다. id=" + eventId, cause);
        }
    }
}
