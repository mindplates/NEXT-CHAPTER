package com.mindplates.nextchapter.application.chapter.view;

import com.mindplates.nextchapter.domain.chapter.model.ChapterRelationType;
import java.util.List;

/**
 * 한 챕터 주변. 사용자가 그래프를 따라 움직이는 화면의 재료다.
 *
 * @param depth 실제로 적용된 홉 수. 요청값이 상한을 넘으면 접히므로 응답에 실어 준다 — 클라이언트가
 *     "3홉을 요청했는데 왜 이만큼인가"를 알 수 있어야 한다
 */
public record ChapterNeighborhoodView(
        ChapterNodeView center, int depth, List<ChapterNodeView> neighbors, List<Relation> relations) {

    /** 간선. 노드 목록 안의 두 챕터만 잇는다 — 목록에 없는 노드를 가리키는 간선은 화면에 그릴 수 없다. */
    public record Relation(Long fromChapterId, Long toChapterId, ChapterRelationType relationType) {}
}
