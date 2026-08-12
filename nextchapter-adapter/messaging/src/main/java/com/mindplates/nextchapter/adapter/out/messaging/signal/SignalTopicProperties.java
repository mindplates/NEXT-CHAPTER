package com.mindplates.nextchapter.adapter.out.messaging.signal;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 신호 토픽.
 *
 * <p>파티션 키가 <b>챕터 ID</b> 다. 집계 축이 "같은 챕터·같은 블록에 몇 건 모였는가"이므로 챕터 단위로
 * 순서가 지켜지면 충분하고, 챕터가 많아질수록 병렬도가 함께 오른다. 뼈대 ID 를 키로 쓰면 인기 있는 뼈대
 * 하나가 파티션 하나에 몰려 그 뼈대의 집계만 밀린다.
 */
@ConfigurationProperties(prefix = "nextchapter.kafka.signal")
public record SignalTopicProperties(String prefix, int partitions, short replicationFactor, int pollLimit) {

    public static final String RECORDED = "signal.consumption.recorded";

    public SignalTopicProperties {
        prefix = prefix == null ? "" : prefix.trim();
        partitions = partitions < 1 ? 6 : partitions;
        replicationFactor = replicationFactor < 1 ? (short) 1 : replicationFactor;
        pollLimit = pollLimit < 1 ? 200 : pollLimit;
    }

    public String recorded() {
        return prefix + RECORDED;
    }
}
