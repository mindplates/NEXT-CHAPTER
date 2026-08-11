package com.mindplates.nextchapter.adapter.out.messaging.health;

import java.time.Duration;
import java.util.concurrent.TimeUnit;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.DescribeClusterOptions;
import org.apache.kafka.clients.admin.DescribeClusterResult;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.kafka.core.KafkaAdmin;
import org.springframework.stereotype.Component;

/**
 * Kafka 연결 상태. Spring Boot 가 기본 제공하지 않아 직접 둔다.
 *
 * <p>생성 잡 체인과 outbox 발행이 전부 Kafka 를 지난다. Kafka 가 죽으면 뼈대 생성이 멈추는
 * 것에 더해 Postgres 에 커밋된 챕터가 Neo4j 로 넘어가지 못한 채 쌓인다. 저장소 중에서 장애가
 * 가장 조용히 번지는 쪽이라 헬스 응답에 반드시 포함시킨다.
 */
@Component
public class KafkaHealthIndicator implements HealthIndicator {

    private static final Duration TIMEOUT = Duration.ofSeconds(3);

    private final KafkaAdmin kafkaAdmin;

    public KafkaHealthIndicator(KafkaAdmin kafkaAdmin) {
        this.kafkaAdmin = kafkaAdmin;
    }

    @Override
    public Health health() {
        try (AdminClient client = AdminClient.create(kafkaAdmin.getConfigurationProperties())) {
            DescribeClusterOptions options = new DescribeClusterOptions().timeoutMs((int) TIMEOUT.toMillis());
            DescribeClusterResult cluster = client.describeCluster(options);
            String clusterId = cluster.clusterId().get(TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
            int nodeCount = cluster.nodes()
                    .get(TIMEOUT.toMillis(), TimeUnit.MILLISECONDS)
                    .size();
            return Health.up()
                    .withDetail("clusterId", clusterId)
                    .withDetail("nodes", nodeCount)
                    .build();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return Health.down().withDetail("error", describe(e)).build();
        } catch (Exception e) {
            return Health.down().withDetail("error", describe(e)).build();
        }
    }

    /** 스택 트레이스 대신 타입과 메시지만 노출한다 — 설정값이 응답에 흘러나가지 않게. */
    private String describe(Exception e) {
        return e.getClass().getSimpleName() + ": " + e.getMessage();
    }
}
