package com.mindplates.nextchapter.adapter.out.messaging.signal;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

/** 토픽을 코드로 선언한다 — 브로커 자동 생성에 맡기면 파티션이 기본값(보통 1)이 된다. */
@Configuration
@EnableConfigurationProperties(SignalTopicProperties.class)
public class SignalTopicConfig {

    @Bean
    public NewTopic signalRecordedTopic(SignalTopicProperties properties) {
        return TopicBuilder.name(properties.recorded())
                .partitions(properties.partitions())
                .replicas(properties.replicationFactor())
                .build();
    }
}
