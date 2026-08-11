package com.mindplates.nextchapter.adapter.out.messaging.outbox;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
@EnableConfigurationProperties(OutboxTopicProperties.class)
public class OutboxTopicConfig {

    @Bean
    public NewTopic chapterOutboxTopic(OutboxTopicProperties properties) {
        return TopicBuilder.name(properties.chapterPersisted())
                .partitions(properties.partitions())
                .replicas(properties.replicationFactor())
                .build();
    }
}
