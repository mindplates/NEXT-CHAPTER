package com.mindplates.nextchapter.adapter.out.messaging.generation;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mindplates.nextchapter.application.generation.port.out.GenerationEventPublisherPort;
import com.mindplates.nextchapter.common.exception.InfrastructureException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

/**
 * 단계 전환 이벤트를 Kafka 로 발행한다.
 *
 * <p>키 선택이 이 클래스의 전부라고 봐도 된다 — 그래프·개요는 뼈대 ID, <b>본문은 챕터 ID</b>. 본문에
 * 뼈대 ID 를 쓰면 한 뼈대의 챕터가 같은 파티션에 몰려 병렬도가 1이 되고, 에러 없이 그냥 느려진다.
 */
@Component
public class GenerationEventKafkaPublisher implements GenerationEventPublisherPort {

    private static final Logger log = LoggerFactory.getLogger(GenerationEventKafkaPublisher.class);

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final GenerationTopicProperties topics;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public GenerationEventKafkaPublisher(
            KafkaTemplate<String, String> kafkaTemplate, GenerationTopicProperties topics) {
        this.kafkaTemplate = kafkaTemplate;
        this.topics = topics;
    }

    @Override
    public void requestGraph(Long skeletonId, Long topicId) {
        send(topics.graphRequested(), key(skeletonId), new GenerationEvents.GraphRequested(skeletonId, topicId));
    }

    @Override
    public void requestOutlines(Long skeletonId) {
        send(topics.outlineRequested(), key(skeletonId), new GenerationEvents.OutlinesRequested(skeletonId));
    }

    /** 키가 챕터 ID 다 — 여기가 실제 병렬 구간이고, 동시에 챕터별 재시도 순서가 보장된다. */
    @Override
    public void requestBody(Long skeletonId, Long chapterId, String chapterKey) {
        send(
                topics.bodyRequested(),
                key(chapterId),
                new GenerationEvents.BodyRequested(skeletonId, chapterId, chapterKey));
    }

    private void send(String topic, String key, Object payload) {
        try {
            kafkaTemplate.send(topic, key, objectMapper.writeValueAsString(payload));
            log.debug("[생성 이벤트] topic={} key={}", topic, key);
        } catch (Exception e) {
            throw new GenerationEventPublishFailed(topic, e);
        }
    }

    private static String key(Long id) {
        return String.valueOf(id);
    }

    /**
     * 발행 실패를 삼키지 않는다. 이벤트가 나가지 않으면 파이프라인이 그 지점에서 멈추고, 뼈대는
     * 생성 중 상태로 영원히 남는다 — 아무도 다음 단계를 시작하지 않는다.
     */
    static class GenerationEventPublishFailed extends InfrastructureException {

        GenerationEventPublishFailed(String topic, Throwable cause) {
            super("생성 이벤트 발행에 실패했습니다. topic=" + topic, cause);
        }
    }
}
