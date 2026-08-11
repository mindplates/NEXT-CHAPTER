package com.mindplates.nextchapter.adapter.out.graph.chapter;

import static org.assertj.core.api.Assertions.assertThat;

import com.mindplates.nextchapter.application.chapter.port.out.GraphSyncPort;
import com.mindplates.nextchapter.domain.chapter.model.ChapterRelationType;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.neo4j.driver.AuthTokens;
import org.neo4j.driver.Driver;
import org.neo4j.driver.GraphDatabase;
import org.testcontainers.containers.Neo4jContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * 그래프 어댑터를 실제 Neo4j 에 붙여 검증한다.
 *
 * <p>여기서만 확인되는 것이 <b>멱등성</b>이다. Kafka 는 at-least-once 이고 파생 저장소는 통째 재구축이
 * 가능해야 하므로, 같은 이벤트를 여러 번 적용하는 일과 임의 오프셋에서 재생하는 일이 둘 다 정상 경로에
 * 있다. 그 두 경우에 그래프가 어떻게 되는지는 Cypher 를 읽어서는 알 수 없다.
 *
 * <p>특히 <b>버전 되돌림</b>을 본다 — 옛 이벤트가 다시 왔을 때 무조건 덮어쓰면 그래프가 과거로 돌아가고,
 * 그 되돌림은 에러 없이 일어나 탐색 결과만 조용히 틀린다.
 */
@Testcontainers
@DisplayName("챕터 그래프 어댑터 (실제 Neo4j)")
class ChapterGraphNeo4jAdapterIT {

    @Container
    @SuppressWarnings("resource")
    static final Neo4jContainer<?> NEO4J = new Neo4jContainer<>("neo4j:5-community").withoutAuthentication();

    private Driver driver;
    private ChapterGraphNeo4jAdapter adapter;

    @BeforeEach
    void setUp() {
        driver = GraphDatabase.driver(NEO4J.getBoltUrl(), AuthTokens.none());
        adapter = new ChapterGraphNeo4jAdapter(driver);
        new ChapterGraphSchemaInitializer(driver).run(null);
        try (var session = driver.session()) {
            session.executeWrite(tx -> tx.run("MATCH (n) DETACH DELETE n").consume());
        }
    }

    @Test
    @DisplayName("챕터 노드를 만들고 갱신한다 — 얇은 그래프에 본문은 없다")
    void upsertsChapterNode() {
        adapter.upsertChapterNode(5L, 100L, "loss-function", "손실함수", 1);

        Map<String, Object> node = singleNode(100L);
        assertThat(node.get("chapterKey")).isEqualTo("loss-function");
        assertThat(node.get("title")).isEqualTo("손실함수");
        assertThat(node.get("version")).isEqualTo(1L);
        assertThat(node.get("skeletonId")).isEqualTo(5L);
        // 본문은 Postgres 가 원천이다 — 그래프에 본문 속성이 새어 들어오면 2홉 조회의 이유가 사라진다.
        assertThat(node).doesNotContainKey("body");
    }

    @Test
    @DisplayName("같은 이벤트를 두 번 적용해도 노드가 하나다")
    void upsertIsIdempotent() {
        adapter.upsertChapterNode(5L, 100L, "loss-function", "손실함수", 1);
        adapter.upsertChapterNode(5L, 100L, "loss-function", "손실함수", 1);

        assertThat(countNodes()).isEqualTo(1);
    }

    @Test
    @DisplayName("버전이 오르면 갱신한다")
    void newerVersionWins() {
        adapter.upsertChapterNode(5L, 100L, "loss-function", "손실함수", 1);

        adapter.upsertChapterNode(5L, 100L, "loss-function", "손실함수 (개선)", 2);

        assertThat(singleNode(100L).get("title")).isEqualTo("손실함수 (개선)");
        assertThat(singleNode(100L).get("version")).isEqualTo(2L);
    }

    /** 옛 오프셋에서 재생했을 때 그래프가 과거로 되돌아가면 안 된다. */
    @Test
    @DisplayName("옛 버전 이벤트가 다시 와도 되돌아가지 않는다")
    void olderVersionDoesNotRegress() {
        adapter.upsertChapterNode(5L, 100L, "loss-function", "손실함수 v3", 3);

        adapter.upsertChapterNode(5L, 100L, "loss-function", "손실함수 v1", 1);

        assertThat(singleNode(100L).get("version")).isEqualTo(3L);
        assertThat(singleNode(100L).get("title")).isEqualTo("손실함수 v3");
    }

    @Test
    @DisplayName("관계를 통째로 교체한다 — 전량 교체이므로 그 자체로 멱등이다")
    void replacesRelations() {
        adapter.upsertChapterNode(5L, 100L, "loss-function", "손실함수", 1);
        adapter.upsertChapterNode(5L, 101L, "gradient-descent", "경사하강법", 1);
        adapter.upsertChapterNode(5L, 102L, "learning-rate", "학습률", 1);

        adapter.replaceRelations(
                5L,
                List.of(
                        new GraphSyncPort.Edge(100L, 101L, ChapterRelationType.PREREQUISITE),
                        new GraphSyncPort.Edge(101L, 102L, ChapterRelationType.PREREQUISITE)));
        assertThat(countRelations()).isEqualTo(2);

        // 재생성으로 그래프가 바뀌었다 — 옛 간선이 남으면 순환이 생길 수 있다.
        adapter.replaceRelations(5L, List.of(new GraphSyncPort.Edge(100L, 102L, ChapterRelationType.RELATED)));

        assertThat(countRelations()).isEqualTo(1);
        assertThat(relationTypes()).containsExactly("RELATED");
    }

    @Test
    @DisplayName("같은 관계 집합을 두 번 적용해도 간선이 늘지 않는다")
    void replaceRelationsIsIdempotent() {
        List<GraphSyncPort.Edge> edges = List.of(new GraphSyncPort.Edge(100L, 101L, ChapterRelationType.PREREQUISITE));

        adapter.replaceRelations(5L, edges);
        adapter.replaceRelations(5L, edges);

        assertThat(countRelations()).isEqualTo(1);
    }

    /**
     * 파티션 키가 뼈대 ID 라 정상 경로에서는 노드가 먼저 온다. 하지만 임의 오프셋 재생에서는 순서가
     * 어긋날 수 있고, 그때 유령 간선이 남는 것보다 속성 없는 노드가 생기는 편이 낫다 — 뒤이어 오는 노드
     * 이벤트가 속성을 채운다.
     */
    @Test
    @DisplayName("노드가 아직 없어도 간선을 만들고, 뒤에 온 노드 이벤트가 속성을 채운다")
    void createsMissingEndpoints() {
        adapter.replaceRelations(5L, List.of(new GraphSyncPort.Edge(100L, 101L, ChapterRelationType.PREREQUISITE)));

        assertThat(countNodes()).isEqualTo(2);
        assertThat(singleNode(100L)).doesNotContainKey("chapterKey");

        adapter.upsertChapterNode(5L, 100L, "loss-function", "손실함수", 1);

        assertThat(singleNode(100L).get("chapterKey")).isEqualTo("loss-function");
        assertThat(countRelations()).isEqualTo(1);
    }

    @Test
    @DisplayName("관계를 비우면 전부 사라진다")
    void emptyEdgesClearsRelations() {
        adapter.replaceRelations(5L, List.of(new GraphSyncPort.Edge(100L, 101L, ChapterRelationType.PREREQUISITE)));

        adapter.replaceRelations(5L, List.of());

        assertThat(countRelations()).isZero();
        assertThat(countNodes()).isEqualTo(2);
    }

    private Map<String, Object> singleNode(Long chapterId) {
        try (var session = driver.session()) {
            return session.executeRead(
                    tx -> tx.run("MATCH (c:Chapter {chapterId: $id}) RETURN c", Map.of("id", chapterId))
                            .single()
                            .get("c")
                            .asMap());
        }
    }

    private long countNodes() {
        try (var session = driver.session()) {
            return session.executeRead(tx -> tx.run("MATCH (c:Chapter) RETURN count(c) AS c")
                    .single()
                    .get("c")
                    .asLong());
        }
    }

    private long countRelations() {
        try (var session = driver.session()) {
            return session.executeRead(tx -> tx.run("MATCH ()-[r:RELATES]->() RETURN count(r) AS c")
                    .single()
                    .get("c")
                    .asLong());
        }
    }

    private List<String> relationTypes() {
        try (var session = driver.session()) {
            return session.executeRead(tx -> tx.run("MATCH ()-[r:RELATES]->() RETURN r.type AS t")
                    .list(record -> record.get("t").asString()));
        }
    }
}
