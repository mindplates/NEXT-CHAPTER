package com.mindplates.nextchapter.adapter.out.graph.chapter;

import com.mindplates.nextchapter.application.chapter.port.out.LoadChapterGraphPort;
import com.mindplates.nextchapter.domain.chapter.model.ChapterRelationType;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.neo4j.driver.Driver;
import org.neo4j.driver.Record;
import org.neo4j.driver.Value;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 그래프 탐색. 사용자가 임의 지점에서 진입해 이웃을 따라가는 경로가 여기로 온다.
 *
 * <p>홉 수를 <b>쿼리 문자열에 넣는다.</b> Cypher 는 가변 길이 패턴의 상한을 파라미터로 받지 않는다.
 * 그래서 값을 먼저 좁은 범위로 접고(문자열 조립에 들어가는 값은 정수여야 한다) 그다음에 붙인다 —
 * 밖에서 온 문자열이 이 자리에 닿는 경로를 만들지 않는 것이 요점이다.
 *
 * <p>간선을 두 번째 쿼리로 따로 읽는다. 한 쿼리에서 경로째 돌려받으면 같은 간선이 경로마다 중복돼
 * 오고, 그 중복을 애플리케이션에서 다시 접어야 한다 — 노드 집합이 확정된 뒤에 그 안의 간선만 묻는
 * 편이 결과가 결정적이다.
 */
@Component
@RequiredArgsConstructor
public class ChapterGraphQueryNeo4jAdapter implements LoadChapterGraphPort {

    private static final Logger log = LoggerFactory.getLogger(ChapterGraphQueryNeo4jAdapter.class);

    /** 홉 상한. 넓히면 뼈대 전체가 한 응답에 들어와 "이웃"이라는 말이 의미를 잃는다. */
    private static final int MAX_DEPTH = 3;

    private final Driver driver;

    @Override
    public Optional<Neighborhood> neighborhood(Long chapterId, int depth) {
        int hops = Math.clamp(depth, 1, MAX_DEPTH);
        try (var session = driver.session()) {
            return session.executeRead(tx -> {
                Record centerRow = tx
                        .run("MATCH (c:Chapter {chapterId: $chapterId}) RETURN c", Map.of("chapterId", chapterId))
                        .list()
                        .stream()
                        .findFirst()
                        .orElse(null);
                if (centerRow == null) {
                    return Optional.empty();
                }
                GraphChapter center = toChapter(centerRow.get("c"));

                // 방향을 지정하지 않는다. 선행 챕터도 이웃이고, 사용자는 어느 방향으로든 움직인다.
                List<GraphChapter> neighbors = tx
                        .run(
                                "MATCH (c:Chapter {chapterId: $chapterId})-[:RELATES*1.." + hops
                                        + "]-(n:Chapter) RETURN DISTINCT n ORDER BY n.chapterKey",
                                Map.of("chapterId", chapterId))
                        .list()
                        .stream()
                        .map(row -> toChapter(row.get("n")))
                        .filter(chapter -> !chapter.chapterId().equals(chapterId))
                        .toList();

                Set<Long> ids = new LinkedHashSet<>();
                ids.add(chapterId);
                neighbors.forEach(chapter -> ids.add(chapter.chapterId()));

                return Optional.of(new Neighborhood(center, hops, neighbors, relationsAmong(tx, List.copyOf(ids))));
            });
        }
    }

    @Override
    public List<GraphChapter> entryPoints(Long skeletonId) {
        try (var session = driver.session()) {
            return session.executeRead(tx -> tx
                    .run(
                            """
                            MATCH (c:Chapter {skeletonId: $skeletonId})
                            WHERE NOT (:Chapter)-[:RELATES {type: 'PREREQUISITE'}]->(c)
                            RETURN c ORDER BY c.chapterKey
                            """,
                            Map.of("skeletonId", skeletonId))
                    .list()
                    .stream()
                    .map(row -> toChapter(row.get("c")))
                    .toList());
        }
    }

    private static List<GraphEdge> relationsAmong(org.neo4j.driver.TransactionContext tx, List<Long> ids) {
        List<GraphEdge> edges = new ArrayList<>();
        tx.run(
                        """
                        MATCH (a:Chapter)-[r:RELATES]->(b:Chapter)
                        WHERE a.chapterId IN $ids AND b.chapterId IN $ids
                        RETURN a.chapterId AS fromId, b.chapterId AS toId, r.type AS type
                        """,
                        Map.of("ids", ids))
                .list()
                .forEach(row -> relationType(row.get("type").asString())
                        .ifPresent(type -> edges.add(new GraphEdge(
                                row.get("fromId").asLong(), row.get("toId").asLong(), type))));
        return List.copyOf(edges);
    }

    /**
     * 모르는 관계 타입은 버리고 경고한다.
     *
     * <p>파생 저장소가 코드보다 앞서 있는 상태(롤백 직후)에서만 일어난다. 통째로 실패시키면 탐색이 아예
     * 안 되고, 조용히 넘기면 원인을 알 수 없으므로 경고를 남긴다 — 그래프는 재구축이 가능하므로 이 상태는
     * 복구할 수 있다.
     */
    private static Optional<ChapterRelationType> relationType(String raw) {
        try {
            return Optional.of(ChapterRelationType.valueOf(raw));
        } catch (IllegalArgumentException e) {
            log.warn("[graph] 알 수 없는 관계 타입을 건너뛴다: {}", raw);
            return Optional.empty();
        }
    }

    private static GraphChapter toChapter(Value node) {
        return new GraphChapter(
                node.get("chapterId").asLong(),
                node.get("skeletonId").asLong(0L),
                node.get("chapterKey").asString(null),
                node.get("title").asString(null),
                node.get("version").asInt(0));
    }
}
