package com.mindplates.nextchapter.adapter.out.messaging.generation;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.kafka.ConfluentKafkaContainer;

/**
 * 발행 어댑터를 실제 Kafka 에 붙여 검증한다.
 *
 * <p>여기서만 확인되는 것이 셋이다.
 *
 * <ol>
 *   <li><b>토픽이 선언한 파티션 수로 만들어지는지</b> — 브로커 자동 생성에 맡기면 파티션이 1개가 되고,
 *       본문 생성이 순차로 돌면서도 아무 에러가 나지 않는다
 *   <li><b>본문 메시지의 키가 챕터 ID 인지</b> — 뼈대 ID 를 쓰면 한 뼈대의 챕터가 같은 파티션에 몰려
 *       병렬도가 1이 된다. 이것도 조용히 느려지기만 한다
 *   <li>페이로드가 타입 헤더 없이 읽히는지 — 헤더에 Java 클래스 이름이 박히면 클래스를 옮기는 것만으로
 *       토픽에 쌓인 옛 메시지가 죽는다
 * </ol>
 */
@SpringBootTest(classes = GenerationEventKafkaPublisherIT.TestApp.class)
@Testcontainers
@TestPropertySource(
        properties = {
            "nextchapter.kafka.prefix=it-",
            "nextchapter.kafka.body-partitions=6",
            "spring.kafka.producer.key-serializer=org.apache.kafka.common.serialization.StringSerializer",
            "spring.kafka.producer.value-serializer=org.apache.kafka.common.serialization.StringSerializer",
            "spring.kafka.consumer.group-id=it-generation",
            "spring.kafka.consumer.auto-offset-reset=earliest"
        })
@DisplayName("생성 이벤트 발행 (실제 Kafka)")
class GenerationEventKafkaPublisherIT {

    /**
     * {@code apache/kafka} 이미지는 Testcontainers 의 KRaft 부트스트랩 경로에서 이 환경에서 기동에
     * 실패했다(컨테이너가 코드 1로 종료). 같은 이미지를 직접 띄우면 정상이므로 이미지가 아니라 그
     * 경로의 문제다. Confluent 이미지는 Testcontainers 안에서 다른 경로를 타고 안정적으로 뜬다.
     */
    @Container
    @ServiceConnection
    @SuppressWarnings("resource")
    static final ConfluentKafkaContainer KAFKA = new ConfluentKafkaContainer("confluentinc/cp-kafka:7.8.0");

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Autowired
    GenerationEventKafkaPublisher publisher;

    @Autowired
    GenerationTopicProperties topics;

    /**
     * 리스너가 같은 모듈에 있어 컴포넌트 스캔에 걸린다. 유스케이스 구현은 application 모듈이라 이
     * 테스트 컨텍스트에 없으므로 목으로 채운다 — 이 테스트가 검증하는 것은 발행이고, 컨슈머 동작은
     * 각 단계의 테스트가 본다.
     */
    @MockitoBean
    com.mindplates.nextchapter.application.generation.port.in.GenerateSkeletonGraphUseCase generateSkeletonGraphUseCase;

    @MockitoBean
    com.mindplates.nextchapter.application.generation.port.in.AdvanceGenerationStageUseCase
            advanceGenerationStageUseCase;

    @Test
    @DisplayName("토픽이 선언한 파티션 수로 만들어진다")
    void topicsAreCreatedWithDeclaredPartitions() throws Exception {
        try (AdminClient admin =
                AdminClient.create(Map.of(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA.getBootstrapServers()))) {
            var described = admin.describeTopics(
                            List.of(topics.graphRequested(), topics.outlineRequested(), topics.bodyRequested()))
                    .allTopicNames()
                    .get();

            assertThat(described.get(topics.graphRequested()).partitions()).hasSize(3);
            assertThat(described.get(topics.bodyRequested()).partitions()).hasSize(6);
        }
    }

    @Test
    @DisplayName("토픽 이름에 접두사가 붙는다 — 한 클러스터를 공유해도 섞이지 않는다")
    void prefixApplies() {
        assertThat(topics.bodyRequested()).isEqualTo("it-skeleton.body.requested");
    }

    @Test
    @DisplayName("그래프 요청은 뼈대 ID 를 키로 발행된다")
    void graphKeyedBySkeleton() {
        publisher.requestGraph(5L, 42L);

        ConsumerRecord<String, String> record = readOne(topics.graphRequested());
        assertThat(record.key()).isEqualTo("5");
        assertThat(payload(record).path("topicId").asLong()).isEqualTo(42L);
    }

    /** 뼈대 ID 를 키로 쓰면 한 뼈대의 챕터가 같은 파티션에 몰려 병렬도가 1이 된다. */
    @Test
    @DisplayName("본문 요청은 챕터 ID 를 키로 발행된다 — 여기가 실제 병렬 구간이다")
    void bodyKeyedByChapter() {
        publisher.requestBody(5L, 77L, "gradient-descent");

        ConsumerRecord<String, String> record = readOne(topics.bodyRequested());
        assertThat(record.key()).isEqualTo("77");
        JsonNode body = payload(record);
        assertThat(body.path("skeletonId").asLong()).isEqualTo(5L);
        assertThat(body.path("chapterId").asLong()).isEqualTo(77L);
        assertThat(body.path("chapterKey").asText()).isEqualTo("gradient-descent");
    }

    @Test
    @DisplayName("같은 뼈대의 챕터들이 서로 다른 파티션으로 흩어진다")
    void chaptersSpreadAcrossPartitions() {
        for (long chapterId = 100; chapterId < 112; chapterId++) {
            publisher.requestBody(5L, chapterId, "chapter-" + chapterId);
        }

        List<Integer> partitions = readAll(topics.bodyRequested()).stream()
                .map(ConsumerRecord::partition)
                .distinct()
                .toList();

        assertThat(partitions).hasSizeGreaterThan(1);
    }

    @Test
    @DisplayName("개요 요청도 뼈대 ID 를 키로 발행된다")
    void outlineKeyedBySkeleton() {
        publisher.requestOutlines(5L);

        assertThat(readOne(topics.outlineRequested()).key()).isEqualTo("5");
    }

    private ConsumerRecord<String, String> readOne(String topic) {
        List<ConsumerRecord<String, String>> records = readAll(topic);
        assertThat(records).isNotEmpty();
        return records.get(records.size() - 1);
    }

    private List<ConsumerRecord<String, String>> readAll(String topic) {
        Map<String, Object> config = Map.of(
                ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG,
                KAFKA.getBootstrapServers(),
                ConsumerConfig.GROUP_ID_CONFIG,
                "it-" + topic + "-" + System.nanoTime(),
                ConsumerConfig.AUTO_OFFSET_RESET_CONFIG,
                "earliest");
        try (KafkaConsumer<String, String> consumer =
                new KafkaConsumer<>(config, new StringDeserializer(), new StringDeserializer())) {
            consumer.subscribe(List.of(topic));
            List<ConsumerRecord<String, String>> collected = new java.util.ArrayList<>();
            for (int attempt = 0; attempt < 10 && collected.isEmpty(); attempt++) {
                ConsumerRecords<String, String> polled = consumer.poll(Duration.ofSeconds(2));
                polled.forEach(collected::add);
            }
            return collected;
        }
    }

    private static JsonNode payload(ConsumerRecord<String, String> record) {
        try {
            return MAPPER.readTree(record.value());
        } catch (Exception e) {
            throw new IllegalStateException("페이로드를 읽지 못했다", e);
        }
    }

    @SpringBootApplication
    static class TestApp {}
}
