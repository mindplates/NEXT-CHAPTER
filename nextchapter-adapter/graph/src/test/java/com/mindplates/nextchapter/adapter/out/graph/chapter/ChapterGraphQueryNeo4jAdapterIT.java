package com.mindplates.nextchapter.adapter.out.graph.chapter;

import static org.assertj.core.api.Assertions.assertThat;

import com.mindplates.nextchapter.application.chapter.port.out.GraphSyncPort;
import com.mindplates.nextchapter.application.chapter.port.out.LoadChapterGraphPort.GraphChapter;
import com.mindplates.nextchapter.application.chapter.port.out.LoadChapterGraphPort.Neighborhood;
import com.mindplates.nextchapter.domain.chapter.model.ChapterRelationType;
import java.util.List;
import java.util.Map;
import java.util.Optional;
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
 * 탐색을 실제 Neo4j 에 붙여 검증한다. 여기서만 확인되는 것은 <b>가변 길이 패턴이 실제로 몇 홉을
 * 도는가</b>다 — Cypher 를 읽어서는 방향 무시·중복 경로·상한 처리를 알 수 없고, 목으로 덮으면 그
 * 셋이 정확히 테스트에서 빠진다.
 *
 * <p>그래프 모양 (선행 사슬 + 곁가지):
 *
 * <pre>
 *   a ──PREREQUISITE──▶ b ──PREREQUISITE──▶ c ──PREREQUISITE──▶ d
 *                       │
 *                       └──RELATED──▶ e
 * </pre>
 */
@Testcontainers
@DisplayName("챕터 그래프 탐색 (실제 Neo4j)")
class ChapterGraphQueryNeo4jAdapterIT {

    @Container
    @SuppressWarnings("resource")
    static final Neo4jContainer<?> NEO4J = new Neo4jContainer<>("neo4j:5-community").withoutAuthentication();

    private static final long SKELETON = 5L;

    private Driver driver;
    private ChapterGraphQueryNeo4jAdapter queries;

    @BeforeEach
    void setUp() {
        driver = GraphDatabase.driver(NEO4J.getBoltUrl(), AuthTokens.none());
        queries = new ChapterGraphQueryNeo4jAdapter(driver);
        ChapterGraphNeo4jAdapter writer = new ChapterGraphNeo4jAdapter(driver);
        new ChapterGraphSchemaInitializer(driver).run(null);
        try (var session = driver.session()) {
            session.executeWrite(tx -> tx.run("MATCH (n) DETACH DELETE n").consume());
        }
        writer.upsertChapterNode(SKELETON, 100L, "a", "가", 1);
        writer.upsertChapterNode(SKELETON, 101L, "b", "나", 1);
        writer.upsertChapterNode(SKELETON, 102L, "c", "다", 1);
        writer.upsertChapterNode(SKELETON, 103L, "d", "라", 1);
        writer.upsertChapterNode(SKELETON, 104L, "e", "마", 1);
        writer.replaceRelations(
                SKELETON,
                List.of(
                        new GraphSyncPort.Edge(100L, 101L, ChapterRelationType.PREREQUISITE),
                        new GraphSyncPort.Edge(101L, 102L, ChapterRelationType.PREREQUISITE),
                        new GraphSyncPort.Edge(102L, 103L, ChapterRelationType.PREREQUISITE),
                        new GraphSyncPort.Edge(101L, 104L, ChapterRelationType.RELATED)));
    }

    /**
     * 선행 관계가 <b>들어오지 않는</b> 챕터가 진입점이다. {@code e} 는 {@code b} 에서 RELATED 로만 들어오므로
     * 선행 조건이 없고, 따라서 진입점이다 — 관계가 있다는 사실만으로 막으면 곁가지 주제로 바로 시작할 수 없다.
     */
    @Test
    @DisplayName("진입점은 선행이 들어오지 않는 챕터다 — RELATED 는 막지 않는다")
    void entryPointsHaveNoIncomingPrerequisite() {
        assertThat(queries.entryPoints(SKELETON))
                .extracting(GraphChapter::chapterKey)
                .containsExactly("a", "e");
    }

    /** 반대로 선행이 하나라도 들어오면 다른 관계가 몇 개든 진입점이 아니다. */
    @Test
    @DisplayName("선행이 들어오는 챕터는 RELATED 가 더 붙어도 진입점이 아니다")
    void prerequisiteAlwaysBlocksEntry() {
        try (var session = driver.session()) {
            session.executeWrite(tx -> tx.run(
                            """
                            MATCH (a:Chapter {chapterId: 100}), (c:Chapter {chapterId: 102})
                            MERGE (a)-[:RELATES {type: 'RELATED'}]->(c)
                            """)
                    .consume());
        }

        assertThat(queries.entryPoints(SKELETON))
                .extracting(GraphChapter::chapterKey)
                .doesNotContain("c");
    }

    /** 방향을 지정하지 않는 것이 요점이다 — 선행 챕터도 이웃이고, 사용자는 어느 방향으로든 움직인다. */
    @Test
    @DisplayName("1홉은 방향과 무관하게 인접한 챕터를 준다")
    void oneHopIgnoresDirection() {
        Neighborhood neighborhood = queries.neighborhood(101L, 1).orElseThrow();

        assertThat(neighborhood.center().chapterKey()).isEqualTo("b");
        assertThat(neighborhood.depth()).isEqualTo(1);
        assertThat(neighborhood.neighbors())
                .extracting(GraphChapter::chapterKey)
                .containsExactly("a", "c", "e");
    }

    @Test
    @DisplayName("2홉은 한 칸 더 간다")
    void twoHopsReachFurther() {
        Neighborhood neighborhood = queries.neighborhood(100L, 2).orElseThrow();

        assertThat(neighborhood.neighbors())
                .extracting(GraphChapter::chapterKey)
                .containsExactly("b", "c", "e");
    }

    /** 중심이 이웃 목록에 섞이면 클라이언트가 자기 자신을 이웃으로 그린다 (사슬이 되돌아온다). */
    @Test
    @DisplayName("중심 챕터는 이웃에 들어가지 않는다")
    void centerIsNotItsOwnNeighbor() {
        Neighborhood neighborhood = queries.neighborhood(101L, 3).orElseThrow();

        assertThat(neighborhood.neighbors()).extracting(GraphChapter::chapterId).doesNotContain(101L);
    }

    /** 상한을 넘겨도 조용히 전체 그래프를 주지 않는다. 응답의 depth 가 실제 적용값이어야 한다. */
    @Test
    @DisplayName("홉 수는 상한으로 접히고, 접힌 값이 응답에 실린다")
    void depthIsClamped() {
        assertThat(queries.neighborhood(100L, 99).orElseThrow().depth()).isEqualTo(3);
        assertThat(queries.neighborhood(100L, 0).orElseThrow().depth()).isEqualTo(1);
    }

    /** 간선이 중복돼 오면 클라이언트가 같은 선을 여러 번 그리고, 경로 가중치 계산도 왜곡된다. */
    @Test
    @DisplayName("간선은 노드 집합 안의 것만, 중복 없이 온다")
    void relationsAreScopedAndDistinct() {
        Neighborhood neighborhood = queries.neighborhood(101L, 1).orElseThrow();

        assertThat(neighborhood.relations()).hasSize(3);
        assertThat(neighborhood.relations())
                .extracting(edge -> edge.fromChapterId() + "->" + edge.toChapterId())
                .containsExactlyInAnyOrder("100->101", "101->102", "101->104");
        // c(102) 는 목록에 있지만 d(103) 는 없다 — 102->103 간선은 담기지 않는다.
        assertThat(neighborhood.relations())
                .extracting(edge -> edge.toChapterId())
                .doesNotContain(103L);
    }

    @Test
    @DisplayName("관계 타입이 살아 있다")
    void carriesRelationType() {
        Neighborhood neighborhood = queries.neighborhood(101L, 1).orElseThrow();

        assertThat(neighborhood.relations())
                .filteredOn(edge -> edge.toChapterId().equals(104L))
                .extracting(edge -> edge.relationType())
                .containsExactly(ChapterRelationType.RELATED);
    }

    /**
     * 그래프에 없는 챕터를 빈 이웃으로 답하면 "관련 챕터 없음"과 구분되지 않는다. 파생 저장소 유실이
     * 그 모습으로 조용히 지나가는 것을 막는다.
     */
    @Test
    @DisplayName("없는 챕터는 빈 결과가 아니라 빈 Optional 이다")
    void missingChapterIsEmptyOptional() {
        assertThat(queries.neighborhood(999L, 2)).isEmpty();
    }

    /** 이웃이 없는 고립 챕터는 정상이다 — 빈 이웃과 "노드 없음"이 다르다는 것이 여기서 확인된다. */
    @Test
    @DisplayName("고립된 챕터는 이웃 없는 결과를 준다")
    void isolatedChapterHasEmptyNeighbors() {
        try (var session = driver.session()) {
            session.executeWrite(tx -> tx.run(
                            "MERGE (c:Chapter {chapterId: 200}) SET c.skeletonId = $s, c.chapterKey = 'z',"
                                    + " c.title = '외딴', c.version = 1",
                            Map.of("s", SKELETON))
                    .consume());
        }

        Optional<Neighborhood> neighborhood = queries.neighborhood(200L, 2);

        assertThat(neighborhood).isPresent();
        assertThat(neighborhood.orElseThrow().neighbors()).isEmpty();
        assertThat(neighborhood.orElseThrow().relations()).isEmpty();
    }

    /** 코드보다 앞선 파생 그래프(롤백 직후)를 통째 실패로 만들지 않는다 — 그래프는 재구축이 가능하다. */
    @Test
    @DisplayName("모르는 관계 타입은 건너뛴다")
    void skipsUnknownRelationType() {
        try (var session = driver.session()) {
            session.executeWrite(tx -> tx.run(
                            """
                            MATCH (a:Chapter {chapterId: 100}), (b:Chapter {chapterId: 101})
                            MERGE (a)-[:RELATES {type: 'FROM_THE_FUTURE'}]->(b)
                            """)
                    .consume());
        }

        Neighborhood neighborhood = queries.neighborhood(100L, 1).orElseThrow();

        assertThat(neighborhood.neighbors())
                .extracting(GraphChapter::chapterKey)
                .containsExactly("b");
        assertThat(neighborhood.relations()).hasSize(1);
    }

    @Test
    @DisplayName("다른 뼈대의 진입점은 섞이지 않는다")
    void entryPointsAreScopedToSkeleton() {
        assertThat(queries.entryPoints(999L)).isEmpty();
    }
}
