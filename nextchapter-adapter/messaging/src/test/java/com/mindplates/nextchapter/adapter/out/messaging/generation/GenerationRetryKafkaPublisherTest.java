package com.mindplates.nextchapter.adapter.out.messaging.generation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;

import com.mindplates.nextchapter.common.exception.InvalidOperationException;
import com.mindplates.nextchapter.domain.generation.model.GenerationStage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;

/**
 * 재발행이 지켜야 하는 것은 <b>키 규칙</b>이다. 원래 발행과 키가 달라지면 같은 챕터의 재시도가 다른
 * 파티션에 가서 원래 메시지와 순서가 어긋난다.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("운영자 재시도 재발행")
class GenerationRetryKafkaPublisherTest {

    @Mock
    KafkaTemplate<String, String> kafkaTemplate;

    private GenerationRetryKafkaPublisher publisher() {
        return new GenerationRetryKafkaPublisher(
                kafkaTemplate, new GenerationTopicProperties("staging.", 3, 3, 12, (short) 1));
    }

    @Test
    @DisplayName("본문은 챕터 ID 를 키로 쓴다 — 원래 발행과 같은 규칙이다")
    void bodyUsesChapterKey() {
        publisher().republish(GenerationStage.BODY, "{\"chapterId\":101}", 5L, 101L);

        verify(kafkaTemplate).send("staging." + GenerationTopicProperties.BODY_REQUESTED, "101", "{\"chapterId\":101}");
    }

    @Test
    @DisplayName("그래프·개요는 뼈대 ID 를 키로 쓴다")
    void skeletonStagesUseSkeletonKey() {
        publisher().republish(GenerationStage.GRAPH, "{\"skeletonId\":5}", 5L, null);
        publisher().republish(GenerationStage.OUTLINE, "{\"skeletonId\":5}", 5L, null);

        verify(kafkaTemplate).send("staging." + GenerationTopicProperties.GRAPH_REQUESTED, "5", "{\"skeletonId\":5}");
        verify(kafkaTemplate).send("staging." + GenerationTopicProperties.OUTLINE_REQUESTED, "5", "{\"skeletonId\":5}");
    }

    /** 조용히 아무것도 하지 않으면 운영자는 재시도를 눌렀는데 아무 일도 일어나지 않는 상태를 겪는다. */
    @Test
    @DisplayName("아직 없는 자산 단계 재시도는 거절한다")
    void rejectsAssetStage() {
        GenerationRetryKafkaPublisher publisher = publisher();

        assertThatThrownBy(() -> publisher.republish(GenerationStage.ASSET, "{}", 5L, 101L))
                .isInstanceOf(InvalidOperationException.class);
    }

    /** 접두어가 붙어 있어도 단계를 알아내야 한다. 그러지 못하면 실패가 큐에 오르지 않는다. */
    @Test
    @DisplayName("토픽 이름에서 단계를 되짚는다")
    void resolvesStageFromTopic() {
        assertThat(GenerationRetryKafkaPublisher.stageOf("staging." + GenerationTopicProperties.BODY_REQUESTED))
                .isEqualTo(GenerationStage.BODY);
        assertThat(GenerationRetryKafkaPublisher.stageOf(GenerationTopicProperties.GRAPH_REQUESTED))
                .isEqualTo(GenerationStage.GRAPH);
        assertThat(GenerationRetryKafkaPublisher.stageOf(GenerationTopicProperties.OUTLINE_REQUESTED))
                .isEqualTo(GenerationStage.OUTLINE);
        assertThat(GenerationRetryKafkaPublisher.stageOf("chapter.outbox.events"))
                .isNull();
        assertThat(GenerationRetryKafkaPublisher.stageOf(null)).isNull();
    }
}
