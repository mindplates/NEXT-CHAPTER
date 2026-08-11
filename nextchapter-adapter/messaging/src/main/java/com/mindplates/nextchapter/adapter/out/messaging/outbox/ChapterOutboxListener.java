package com.mindplates.nextchapter.adapter.out.messaging.outbox;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mindplates.nextchapter.application.chapter.port.out.GraphSyncPort;
import com.mindplates.nextchapter.domain.chapter.model.ChapterRelationType;
import com.mindplates.nextchapter.domain.chapter.model.OutboxEventType;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

/**
 * outbox 컨슈머 — Postgres 에 커밋된 것을 Neo4j 에 반영한다.
 *
 * <p><b>멱등이다.</b> 노드는 버전이 더 클 때만 덮어쓰고, 관계는 전량 교체다. 그래서 같은 이벤트를 여러 번
 * 받아도, 임의 오프셋에서 재생해도 결과가 같다 — Kafka 가 at-least-once 이고 파생 저장소는 통째 재구축이
 * 가능해야 하므로 그 두 상황이 정상 경로에 있다.
 *
 * <p>알 수 없는 이벤트 종류는 <b>건너뛴다.</b> 예외를 올리면 그 파티션이 막혀 뒤의 정상 이벤트까지 멈추고,
 * 파티션 키가 뼈대 ID 라 그 뼈대의 반영 전체가 밀린다. 모르는 종류는 이 버전이 처리할 수 없다는 뜻이고,
 * 새 버전이 배포되면 오프셋을 되돌려 다시 읽을 수 있다.
 */
@Component
public class ChapterOutboxListener {

    private static final Logger log = LoggerFactory.getLogger(ChapterOutboxListener.class);

    private final GraphSyncPort graphSyncPort;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public ChapterOutboxListener(GraphSyncPort graphSyncPort) {
        this.graphSyncPort = graphSyncPort;
    }

    @KafkaListener(topics = "${nextchapter.kafka.outbox.prefix:}" + OutboxTopicProperties.CHAPTER_PERSISTED)
    public void onOutboxEvent(
            @Payload String payload,
            @Header(name = OutboxKafkaPublisher.EVENT_TYPE_HEADER, required = false) String eventType,
            @Header(name = OutboxKafkaPublisher.IDEMPOTENCY_KEY_HEADER, required = false) String idempotencyKey)
            throws Exception {
        OutboxEventType type = parseType(eventType);
        if (type == null) {
            log.warn("[outbox] 알 수 없는 이벤트 종류를 건너뛴다: {}", eventType);
            return;
        }
        JsonNode node = objectMapper.readTree(payload);

        switch (type) {
            case CHAPTER_PERSISTED -> graphSyncPort.upsertChapterNode(
                    node.path("skeletonId").asLong(),
                    node.path("chapterId").asLong(),
                    node.path("chapterKey").asText(),
                    node.path("title").asText(),
                    node.path("version").asInt());
            case CHAPTER_GRAPH_PERSISTED -> graphSyncPort.replaceRelations(
                    node.path("skeletonId").asLong(), edges(node.path("edges")));
        }
        log.debug("[outbox] 반영 완료 type={} key={}", type, idempotencyKey);
    }

    private static List<GraphSyncPort.Edge> edges(JsonNode node) {
        List<GraphSyncPort.Edge> edges = new ArrayList<>();
        for (JsonNode edge : node) {
            ChapterRelationType relationType = relationType(edge.path("type").asText(null));
            if (relationType == null) {
                log.warn("[outbox] 알 수 없는 관계 타입을 건너뛴다: {}", edge.path("type").asText(null));
                continue;
            }
            edges.add(new GraphSyncPort.Edge(
                    edge.path("from").asLong(), edge.path("to").asLong(), relationType));
        }
        return edges;
    }

    private static ChapterRelationType relationType(String raw) {
        if (raw == null) {
            return null;
        }
        try {
            return ChapterRelationType.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static OutboxEventType parseType(String raw) {
        if (raw == null) {
            return null;
        }
        try {
            return OutboxEventType.valueOf(raw.trim());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
