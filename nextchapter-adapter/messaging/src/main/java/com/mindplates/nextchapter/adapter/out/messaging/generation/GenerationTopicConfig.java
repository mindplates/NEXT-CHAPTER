package com.mindplates.nextchapter.adapter.out.messaging.generation;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

/**
 * 토픽을 코드로 선언한다. {@code KafkaAdmin} 이 기동 시 없는 토픽을 만든다.
 *
 * <p>브로커의 자동 생성에 맡기지 않는 이유는 <b>파티션 수</b>다. 자동 생성된 토픽은 브로커 기본값
 * (보통 1)을 쓰고, 그러면 본문 생성이 순차로 돌면서도 아무 에러가 나지 않는다. 나중에 파티션을 늘려도
 * 이미 쌓인 메시지의 분포는 바뀌지 않는다.
 */
@Configuration
@EnableConfigurationProperties(GenerationTopicProperties.class)
public class GenerationTopicConfig {

    @Bean
    public NewTopic skeletonGraphRequestedTopic(GenerationTopicProperties properties) {
        return TopicBuilder.name(properties.graphRequested())
                .partitions(properties.graphPartitions())
                .replicas(properties.replicationFactor())
                .build();
    }

    @Bean
    public NewTopic skeletonOutlineRequestedTopic(GenerationTopicProperties properties) {
        return TopicBuilder.name(properties.outlineRequested())
                .partitions(properties.outlinePartitions())
                .replicas(properties.replicationFactor())
                .build();
    }

    @Bean
    public NewTopic skeletonBodyRequestedTopic(GenerationTopicProperties properties) {
        return TopicBuilder.name(properties.bodyRequested())
                .partitions(properties.bodyPartitions())
                .replicas(properties.replicationFactor())
                .build();
    }
}
