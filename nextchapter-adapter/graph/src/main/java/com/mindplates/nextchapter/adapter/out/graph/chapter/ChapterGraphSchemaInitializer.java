package com.mindplates.nextchapter.adapter.out.graph.chapter;

import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.neo4j.driver.Driver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * {@code Chapter.chapterId} 유일 제약을 만든다.
 *
 * <p>없으면 {@code MERGE} 가 동시 실행에서 <b>같은 chapterId 노드를 두 개</b> 만들 수 있다. 그러면 탐색이
 * 챕터를 두 번 세거나 간선이 둘로 갈리는데, 어느 쪽도 에러가 아니라서 조용히 틀린다.
 *
 * <p>제약이 인덱스도 만들어 주므로 {@code MERGE} 성능도 여기서 확보된다 — 인덱스 없는 MERGE 는 전체
 * 노드 스캔이다.
 */
@Component
@RequiredArgsConstructor
public class ChapterGraphSchemaInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(ChapterGraphSchemaInitializer.class);

    private final Driver driver;

    @Override
    public void run(ApplicationArguments args) {
        try (var session = driver.session()) {
            session.executeWrite(tx -> tx.run(
                            "CREATE CONSTRAINT chapter_id_unique IF NOT EXISTS "
                                    + "FOR (c:Chapter) REQUIRE c.chapterId IS UNIQUE",
                            Map.of())
                    .consume());
            log.info("[graph] Chapter.chapterId 유일 제약 확인");
        } catch (RuntimeException e) {
            // Neo4j 가 아직 없어도 기동을 막지 않는다 — 파생 저장소이고, 헬스체크가 상태를 알려 준다.
            log.warn("[graph] 제약 생성 실패, 반영 시점에 다시 시도된다: {}", e.getMessage());
        }
    }
}
