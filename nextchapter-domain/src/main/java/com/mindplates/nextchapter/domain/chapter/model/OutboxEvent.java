package com.mindplates.nextchapter.domain.chapter.model;

import com.mindplates.nextchapter.common.text.Strings;
import java.time.LocalDateTime;

/**
 * 파생 저장소로 흘려보낼 이벤트 하나. 본문·관계와 <b>같은 커밋</b>에 들어간다.
 *
 * @param partitionKey 뼈대 ID. 뼈대 단위 순서를 보장한다 — 간선 이벤트가 노드 이벤트보다 먼저 도착하면
 *     Neo4j 에 속성 없는 유령 노드가 생긴다. 본문 생성이 병렬도를 위해 챕터 ID 를 키로 쓴 것과 반대의
 *     선택이고, 이유도 반대다: 여기서 하는 일은 값싸고 순서가 중요하다
 * @param idempotencyKey 챕터 ID + 버전. 재시도·재발행이 안전한 근거다
 * @param publishedAt null 이면 아직 발행되지 않았다
 */
public record OutboxEvent(
        Long id,
        String aggregateType,
        String aggregateId,
        OutboxEventType eventType,
        String partitionKey,
        String idempotencyKey,
        String payload,
        LocalDateTime createdAt,
        LocalDateTime publishedAt) {

    private static final String CHAPTER = "CHAPTER";

    public OutboxEvent {
        aggregateType = Strings.requireText(aggregateType, "집합체 타입");
        aggregateId = Strings.requireText(aggregateId, "집합체 ID");
        if (eventType == null) {
            throw new IllegalArgumentException("이벤트 종류는 필수입니다.");
        }
        partitionKey = Strings.requireText(partitionKey, "파티션 키");
        idempotencyKey = Strings.requireText(idempotencyKey, "멱등 키");
        payload = Strings.requireText(payload, "페이로드");
    }

    /** 챕터 본문이 커밋됐다. 멱등 키에 버전이 들어가 재발행이 옛 상태로 되돌리지 않는다. */
    public static OutboxEvent chapterPersisted(Long skeletonId, Long chapterId, int version, String payload) {
        return new OutboxEvent(
                null,
                CHAPTER,
                String.valueOf(chapterId),
                OutboxEventType.CHAPTER_PERSISTED,
                String.valueOf(skeletonId),
                "chapter:" + chapterId + ":v" + version,
                payload,
                null,
                null);
    }

    /**
     * 뼈대의 관계가 바뀌었다.
     *
     * <p>멱등 키에 outbox 행 ID 를 쓸 수 없어(아직 없다) 관계 집합의 세대를 대신 쓴다. 전량 교체라
     * 같은 이벤트를 두 번 적용해도 결과가 같으므로 키의 유일성이 정확성에 필요하지는 않다 — 추적용이다.
     */
    public static OutboxEvent chapterGraphPersisted(Long skeletonId, int relationCount, String payload) {
        return new OutboxEvent(
                null,
                CHAPTER,
                String.valueOf(skeletonId),
                OutboxEventType.CHAPTER_GRAPH_PERSISTED,
                String.valueOf(skeletonId),
                "skeleton:" + skeletonId + ":graph:" + relationCount,
                payload,
                null,
                null);
    }

    public boolean isPublished() {
        return publishedAt != null;
    }
}
