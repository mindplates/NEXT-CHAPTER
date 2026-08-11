package com.mindplates.nextchapter.adapter.out.messaging.outbox;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * outbox 토픽. 생성 파이프라인 토픽과 <b>파티션 키 전략이 반대라</b> 설정도 따로 둔다.
 *
 * <p>본문 생성은 병렬도를 위해 챕터 ID 를 키로 썼다. 여기는 <b>뼈대 ID</b>다 — Neo4j 반영은 값싸고,
 * 대신 간선이 노드보다 먼저 도착하면 속성 없는 유령 노드가 생긴다. 순서가 병렬도보다 중요한 구간이다.
 */
@ConfigurationProperties(prefix = "nextchapter.kafka.outbox")
public record OutboxTopicProperties(String prefix, int partitions, short replicationFactor, int pollLimit) {

    public static final String CHAPTER_PERSISTED = "chapter.outbox.persisted";

    public OutboxTopicProperties {
        prefix = prefix == null ? "" : prefix.trim();
        partitions = partitions < 1 ? 3 : partitions;
        replicationFactor = replicationFactor < 1 ? (short) 1 : replicationFactor;
        pollLimit = pollLimit < 1 ? 100 : pollLimit;
    }

    public String chapterPersisted() {
        return prefix + CHAPTER_PERSISTED;
    }
}
