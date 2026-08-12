package com.mindplates.nextchapter.application.chapter.port.out;

import com.mindplates.nextchapter.domain.chapter.model.ChapterRelationType;
import java.util.List;
import java.util.Optional;

/**
 * 그래프 <b>읽기</b>. Neo4j 가 답한다.
 *
 * <p>{@link LoadChapterRelationPort}(Postgres)와 나란히 두는 이유는 용도가 다르기 때문이다. 검수와
 * 재구축은 원천을 읽어야 하고, 사용자의 탐색은 "이 챕터에서 N홉 이내"처럼 <b>가변 길이 탐색</b>이라
 * 그래프 저장소가 답해야 한다. 같은 질문을 Postgres 에 하면 홉 수만큼 조인이 붙는다.
 *
 * <p>본문은 여기서 나오지 않는다 — 얇은 그래프라 제목·버전까지다. 탐색 후 본문 조회가 2홉인 것이
 * 그 선택의 대가이고, 그 대가를 받아들인 이유는 수정 이력과 승인 기록이 본문과 같은 트랜잭션에
 * 묶여야 하기 때문이다.
 */
public interface LoadChapterGraphPort {

    /**
     * 중심 챕터와 N홉 이내의 이웃.
     *
     * <p>중심 챕터가 그래프에 없으면 <b>빈 결과가 아니라 빈 Optional</b>이다. 둘을 같게 두면 "그래프에
     * 반영되지 않은 챕터"가 "관련 챕터 없음"으로 보이고, 그건 에러 없이 조용히 틀린다.
     */
    Optional<Neighborhood> neighborhood(Long chapterId, int depth);

    /** 선행 관계가 들어오지 않는 챕터. 사용자가 처음 진입할 수 있는 지점이다. */
    List<GraphChapter> entryPoints(Long skeletonId);

    record GraphChapter(Long chapterId, Long skeletonId, String chapterKey, String title, int version) {}

    record GraphEdge(Long fromChapterId, Long toChapterId, ChapterRelationType relationType) {}

    /**
     * @param depth 실제로 적용된 홉 수. 어댑터가 상한으로 접을 수 있으므로 요청값이 아니라 적용값을
     *     담는다 — 요청값을 그대로 되돌리면 클라이언트가 접혔다는 사실을 알 수 없다
     * @param relations 중심·이웃 사이의 간선만. 밖으로 나가는 간선은 담지 않는다 (목록에 없는 노드를 가리킨다)
     */
    record Neighborhood(GraphChapter center, int depth, List<GraphChapter> neighbors, List<GraphEdge> relations) {}
}
