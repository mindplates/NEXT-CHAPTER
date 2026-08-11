package com.mindplates.nextchapter.adapter.out.messaging.generation;

import com.mindplates.nextchapter.application.generation.port.out.GenerationRetryPublisherPort;
import com.mindplates.nextchapter.common.exception.InvalidOperationException;
import com.mindplates.nextchapter.domain.generation.model.GenerationStage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

/**
 * 운영자 재시도 — 저장된 페이로드를 <b>그대로</b> 다시 발행한다.
 *
 * <p>{@link GenerationEventKafkaPublisher} 와 나눈 이유는 이쪽이 페이로드를 만들지 않기 때문이다. 다시
 * 만들면 그 사이 바뀐 데이터가 섞여 <b>다른 요청</b>이 되고, 원래 실패의 재시도가 아니게 된다.
 *
 * <p>키는 원래 발행과 같은 규칙을 따른다 — 그래프·개요는 뼈대 ID, <b>본문은 챕터 ID</b>. 키가 달라지면
 * 같은 챕터의 재시도가 다른 파티션에 가서 원래 메시지와 순서가 어긋난다.
 */
@Component
public class GenerationRetryKafkaPublisher implements GenerationRetryPublisherPort {

    private static final Logger log = LoggerFactory.getLogger(GenerationRetryKafkaPublisher.class);

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final GenerationTopicProperties topics;

    public GenerationRetryKafkaPublisher(
            KafkaTemplate<String, String> kafkaTemplate, GenerationTopicProperties topics) {
        this.kafkaTemplate = kafkaTemplate;
        this.topics = topics;
    }

    @Override
    public void republish(GenerationStage stage, String payload, Long skeletonId, Long chapterId) {
        String topic = topicOf(stage);
        String key = String.valueOf(stage == GenerationStage.BODY ? chapterId : skeletonId);
        kafkaTemplate.send(topic, key, payload);
        log.info("[재시도] 재발행 topic={} key={} stage={}", topic, key, stage);
    }

    private String topicOf(GenerationStage stage) {
        return switch (stage) {
            case GRAPH -> topics.graphRequested();
            case OUTLINE -> topics.outlineRequested();
            case BODY -> topics.bodyRequested();
                // ④ 는 M6 에서 붙는다. 여기서 조용히 아무것도 하지 않으면 운영자는 재시도를 눌렀는데
                // 아무 일도 일어나지 않는 상태를 겪는다.
            case ASSET -> throw new InvalidOperationException("파생 자산 단계 재시도는 아직 지원하지 않습니다.");
        };
    }

    /** 토픽 이름에서 단계를 되짚는다. 실패 기록이 어느 단계였는지 알아야 재발행 대상이 정해진다. */
    static GenerationStage stageOf(String topic) {
        if (topic == null) {
            return null;
        }
        if (topic.endsWith(GenerationTopicProperties.GRAPH_REQUESTED)) {
            return GenerationStage.GRAPH;
        }
        if (topic.endsWith(GenerationTopicProperties.OUTLINE_REQUESTED)) {
            return GenerationStage.OUTLINE;
        }
        if (topic.endsWith(GenerationTopicProperties.BODY_REQUESTED)) {
            return GenerationStage.BODY;
        }
        return null;
    }
}
