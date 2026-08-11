package com.mindplates.nextchapter.application.chapter.port.out;

import com.mindplates.nextchapter.domain.chapter.model.ChapterRelationType;
import java.util.List;

/**
 * 파생 그래프 저장소(Neo4j) 반영.
 *
 * <p>Neo4j 에는 <b>챕터 ID · 관계 · 임베딩만</b> 둔다. 본문은 Postgres 가 원천이다 — 수정 이력과 승인
 * 기록이 본문과 같은 트랜잭션에 묶여야 하고, 그래프가 가벼울수록 탐색이 빠르다. 대가는 탐색 후 본문
 * 조회가 2홉이라는 점이다.
 *
 * <p>두 메서드 모두 <b>멱등</b>해야 한다. Kafka 는 at-least-once 이고, 파생 저장소는 통째 재구축도
 * 가능해야 하므로 같은 이벤트를 여러 번 적용하는 일이 정상 경로에 있다.
 */
public interface GraphSyncPort {

    /**
     * 챕터 노드를 만들거나 갱신한다.
     *
     * <p>버전이 <b>더 클 때만</b> 덮어써야 한다. 옛 오프셋에서 재생하면 지난 버전 이벤트가 다시 오는데,
     * 그때 무조건 덮어쓰면 그래프가 과거로 되돌아간다 — 그리고 그건 에러 없이 조용히 일어난다.
     */
    void upsertChapterNode(Long skeletonId, Long chapterId, String chapterKey, String title, int version);

    /** 뼈대의 관계를 통째로 교체한다. 전량 교체이므로 그 자체로 멱등이다. */
    void replaceRelations(Long skeletonId, List<Edge> edges);

    /** 간선 하나. 양 끝 노드가 없으면 구현이 만들어야 한다 — 순서가 어긋나도 유령 간선이 남지 않게. */
    record Edge(Long fromChapterId, Long toChapterId, ChapterRelationType relationType) {}
}
