package com.mindplates.nextchapter.adapter.out.graph.chapter;

import com.mindplates.nextchapter.application.chapter.port.out.GraphSyncPort;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.neo4j.driver.Driver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Neo4j 반영. <b>얇은 그래프</b>다 — 챕터 ID · 키 · 제목 · 버전 · 관계만 둔다.
 *
 * <p>본문을 여기 두지 않는 이유는 두 가지다. 수정 이력과 승인 기록이 본문과 같은 트랜잭션에 묶여야
 * 하고(Postgres 가 원천), 그래프가 가벼울수록 탐색이 빠르다. 대가는 탐색 후 본문 조회가 2홉이라는 점이다.
 *
 * <p>관계를 네이티브 관계 타입이 아니라 {@code RELATES} + {@code type} 속성으로 둔다. 타입이 늘어날 때
 * (M4 개인화가 더 필요로 할 수 있다) Cypher 를 고치지 않아도 되고, 나중에 탐색 성능이 네이티브 타입을
 * 요구하면 <b>파생 저장소이므로 통째 재구축으로 옮길 수 있다.</b> 원천이라면 못 할 선택이다.
 */
@Component
@RequiredArgsConstructor
public class ChapterGraphNeo4jAdapter implements GraphSyncPort {

    private static final Logger log = LoggerFactory.getLogger(ChapterGraphNeo4jAdapter.class);

    private final Driver driver;

    /**
     * 버전이 더 클 때만 덮어쓴다.
     *
     * <p>옛 오프셋에서 재생하면 지난 버전 이벤트가 다시 오는데, 무조건 덮어쓰면 그래프가 과거로
     * 되돌아간다. 그 되돌림은 에러 없이 일어나고, 탐색 결과가 조용히 틀린다.
     */
    @Override
    public void upsertChapterNode(Long skeletonId, Long chapterId, String chapterKey, String title, int version) {
        try (var session = driver.session()) {
            session.executeWrite(tx -> tx.run(
                            """
                            MERGE (c:Chapter {chapterId: $chapterId})
                            ON CREATE SET c.version = 0
                            SET c.skeletonId = $skeletonId,
                                c.chapterKey = $chapterKey,
                                c.title   = CASE WHEN coalesce(c.version, 0) <= $version THEN $title ELSE c.title END,
                                c.version = CASE WHEN coalesce(c.version, 0) < $version THEN $version ELSE c.version END
                            """,
                            Map.of(
                                    "chapterId", chapterId,
                                    "skeletonId", skeletonId,
                                    "chapterKey", chapterKey,
                                    "title", title,
                                    "version", version))
                    .consume());
        }
        log.debug("[graph] 챕터 노드 반영 chapterId={} version={}", chapterId, version);
    }

    /**
     * 뼈대의 관계를 통째로 교체한다 — 전량 교체이므로 그 자체로 멱등이다.
     *
     * <p>양 끝 노드를 {@code MERGE} 로 만든다. 파티션 키가 뼈대 ID 라 정상 경로에서는 노드 이벤트가 먼저
     * 오지만, 임의 오프셋에서 재생하면 순서가 어긋날 수 있다. 그때 노드를 만들어 두면 속성만 비어 있고
     * 간선은 살아 있으며, 뒤이어 오는 노드 이벤트가 속성을 채운다 — 유령 간선이 남는 것보다 낫다.
     */
    @Override
    public void replaceRelations(Long skeletonId, List<Edge> edges) {
        List<Map<String, Object>> rows = edges.stream()
                .map(edge -> Map.<String, Object>of(
                        "from", edge.fromChapterId(),
                        "to", edge.toChapterId(),
                        "type", edge.relationType().name()))
                .toList();

        try (var session = driver.session()) {
            session.executeWrite(tx -> {
                tx.run(
                                "MATCH (:Chapter {skeletonId: $skeletonId})-[r:RELATES]->(:Chapter) DELETE r",
                                Map.of("skeletonId", skeletonId))
                        .consume();
                if (!rows.isEmpty()) {
                    tx.run(
                                    """
                                    UNWIND $edges AS edge
                                    MERGE (from:Chapter {chapterId: edge.from})
                                      ON CREATE SET from.skeletonId = $skeletonId, from.version = 0
                                    MERGE (to:Chapter {chapterId: edge.to})
                                      ON CREATE SET to.skeletonId = $skeletonId, to.version = 0
                                    MERGE (from)-[:RELATES {type: edge.type}]->(to)
                                    """,
                                    Map.of("skeletonId", skeletonId, "edges", rows))
                            .consume();
                }
                return null;
            });
        }
        log.debug("[graph] 관계 교체 skeletonId={} edges={}", skeletonId, rows.size());
    }
}
