package com.mindplates.nextchapter.adapter.out.messaging.signal;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mindplates.nextchapter.application.signal.port.out.SignalPublisherPort;
import com.mindplates.nextchapter.common.exception.InfrastructureException;
import com.mindplates.nextchapter.domain.signal.model.Signal;
import java.util.Map;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Component;

/**
 * 신호를 집계 경로로 내보낸다.
 *
 * <p><b>와이어 전용 레코드를 지난다.</b> 도메인 레코드를 직접 직렬화하면 필드 이름을 바꾸는 리팩터링이
 * 토픽에 쌓인 옛 메시지를 읽을 수 없게 만든다. 같은 이유로 타입 헤더를 쓰지 않는다 — 헤더에 Java 클래스
 * 이름이 박히면 클래스를 옮기는 것만으로 옛 메시지가 죽는다.
 *
 * <p>발행 실패를 삼키지 않는다. 삼키면 발행 표시가 커밋되어 그 신호가 집계에 영원히 도달하지 않고, 그
 * 유실은 "임계치가 트리거되지 않는다"로 조용히 나타난다.
 */
@Component
public class SignalKafkaPublisher implements SignalPublisherPort {

    /** 컨슈머(M5)가 페이로드를 파싱하기 전에 종류로 분기할 수 있게 헤더로도 싣는다. */
    public static final String SIGNAL_TYPE_HEADER = "nc-signal-type";

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final SignalTopicProperties topics;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public SignalKafkaPublisher(KafkaTemplate<String, String> kafkaTemplate, SignalTopicProperties topics) {
        this.kafkaTemplate = kafkaTemplate;
        this.topics = topics;
    }

    @Override
    public void publish(Signal signal) {
        try {
            kafkaTemplate.send(MessageBuilder.withPayload(objectMapper.writeValueAsString(toWire(signal)))
                    .setHeader(KafkaHeaders.TOPIC, topics.recorded())
                    // 집계 축이 챕터 단위이므로 챕터 ID 를 키로 쓴다.
                    .setHeader(KafkaHeaders.KEY, String.valueOf(signal.chapterId()))
                    .setHeader(SIGNAL_TYPE_HEADER, signal.type().name())
                    .build());
        } catch (Exception e) {
            throw new SignalPublishFailed(signal.id(), e);
        }
    }

    private static Map<String, Object> toWire(Signal signal) {
        // LinkedHashMap 이 아니라 HashMap 계열 순서에 의존하지 않는다 — 컨슈머는 키로 읽는다.
        Map<String, Object> wire = new java.util.LinkedHashMap<>();
        wire.put("signalId", signal.id());
        wire.put("userId", signal.userId());
        wire.put("chapterId", signal.chapterId());
        wire.put("chapterVersion", signal.chapterVersion());
        wire.put("blockId", signal.blockId());
        // 개인화 블록에 붙은 신호를 집계에서 구분할 수 있어야 한다 — 공용 본문에는 없는 블록이다.
        wire.put("supplementBlock", signal.isOnSupplementBlock());
        wire.put("format", signal.format().name());
        wire.put("type", signal.type().name());
        wire.put("payload", signal.payload());
        wire.put("occurredAt", signal.occurredAt().toString());
        return wire;
    }

    static class SignalPublishFailed extends InfrastructureException {

        SignalPublishFailed(Long signalId, Throwable cause) {
            super("신호 발행에 실패했습니다. id=" + signalId, cause);
        }
    }
}
